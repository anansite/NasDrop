import subprocess
import time
import os
import shutil
import urllib.request
import json
import xml.etree.ElementTree as ET
import re

ADB = r"C:\Users\admin\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"
DEVICE = "127.0.0.1:16384"
ARTIFACTS_DIR = r"C:\Users\admin\.gemini\antigravity-cli\brain\d6bc982b-0100-4c6e-8abd-14870e722c80"

def adb(*args):
    cmd = [ADB, "-s", DEVICE] + list(args)
    return subprocess.run(cmd, capture_output=True, text=True)

def ensure_connected():
    subprocess.run([ADB, "connect", DEVICE], capture_output=True)
    time.sleep(1)

def get_token():
    proc = subprocess.run(["git", "credential", "fill"], input="protocol=https\nhost=github.com\n", capture_output=True, text=True)
    for line in proc.stdout.splitlines():
        if line.startswith("password="):
            return line.split("=", 1)[1].strip()
    return ""

def take_screenshot(name):
    ensure_connected()
    remote = f"/sdcard/{name}.png"
    local = os.path.join(ARTIFACTS_DIR, f"{name}.png")
    adb("shell", "screencap", "-p", remote)
    adb("pull", remote, local)
    print(f"Captured screenshot: {local}", flush=True)
    return local

def get_ui_dump():
    ensure_connected()
    adb("shell", "uiautomator", "dump", "/sdcard/v219_dump.xml")
    res = subprocess.run([ADB, "-s", DEVICE, "shell", "cat", "/sdcard/v219_dump.xml"], capture_output=True, text=True)
    return res.stdout

def find_node(xml_str, target):
    try:
        root = ET.fromstring(xml_str)
        for node in root.iter("node"):
            text = node.attrib.get("text", "")
            desc = node.attrib.get("content-desc", "")
            if target in text or target in desc:
                return node.attrib.get("bounds", ""), text, desc
    except Exception:
        pass
    return None, None, None

def click_bounds(bounds):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        cx = (x1 + x2) // 2
        cy = (y1 + y2) // 2
        print(f"Clicking ({cx}, {cy}) for bounds {bounds}", flush=True)
        adb("shell", "input", "tap", str(cx), str(cy))
        time.sleep(2)
        return True
    return False

def wait_and_install_v219():
    token = get_token()
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github.v3+json", "User-Agent": "NasDrop-CI"}

    commit_sha = "6464035"
    print(f"Finding CI run for commit {commit_sha}...", flush=True)
    run_id = None
    for _ in range(20):
        req = urllib.request.Request("https://api.github.com/repos/anansite/NasDrop/actions/runs?per_page=5", headers=headers)
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        for run in data.get("workflow_runs", []):
            if run["head_sha"].startswith(commit_sha):
                run_id = run["id"]
                print(f"Found run ID: {run_id}, status: {run['status']}", flush=True)
                break
        if run_id:
            break
        time.sleep(3)

    if not run_id:
        print("Failed to find run for commit!")
        return False

    for _ in range(60):
        req = urllib.request.Request(f"https://api.github.com/repos/anansite/NasDrop/actions/runs/{run_id}", headers=headers)
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        status = data["status"]
        conclusion = data.get("conclusion")
        print(f"Run {run_id} status: {status}, conclusion: {conclusion}", flush=True)
        if status == "completed":
            if conclusion != "success":
                print(f"CI failed with conclusion: {conclusion}")
                return False
            break
        time.sleep(10)

    # Download from Release v1.0.219
    url = "https://github.com/anansite/NasDrop/releases/download/v1.0.219/NasDrop-v1.0.219-x86_64.apk"
    apk_local = os.path.join(ARTIFACTS_DIR, "NasDrop-v1.0.219-x86_64.apk")
    print(f"Downloading {url} to {apk_local}...", flush=True)
    req = urllib.request.Request(url, headers={"User-Agent": "NasDrop-CI"})
    with urllib.request.urlopen(req) as resp, open(apk_local, "wb") as f:
        f.write(resp.read())
    print(f"Downloaded APK: {os.path.getsize(apk_local)} bytes", flush=True)

    ensure_connected()
    print("Installing v219 APK...", flush=True)
    res = subprocess.run([ADB, "-s", DEVICE, "install", "-r", "-d", apk_local], capture_output=True, text=True)
    print("Install result:", res.stdout, res.stderr, flush=True)

    # Launch app
    print("Launching com.nasdrop...", flush=True)
    adb("shell", "am", "force-stop", "com.nasdrop")
    time.sleep(1)
    adb("shell", "am", "start", "-n", "com.nasdrop/.MainActivity")
    time.sleep(4)
    return True

def run_tests():
    ensure_connected()
    print("Testing v219 direct SAF recents flow...", flush=True)

    # 1. Switch to Photos tab (417, 1520)
    print("Switching to Photos tab...", flush=True)
    adb("shell", "input", "tap", "417", "1520")
    time.sleep(2)

    # 2. Click upload button directly
    dump = get_ui_dump()
    upload_bounds, text, desc = find_node(dump, "上传照片")
    if not upload_bounds:
        upload_bounds, text, desc = find_node(dump, "上传")
    if upload_bounds:
        print(f"Clicking upload in Photos tab: {upload_bounds}...", flush=True)
        click_bounds(upload_bounds)
    else:
        print("Clicking FAB (816, 1364)...", flush=True)
        adb("shell", "input", "tap", "816", "1364")
    time.sleep(2)

    # 3. Verify SAF is directly opened showing '最近'
    dump = get_ui_dump()
    print("=== UI DUMP AFTER DIRECT UPLOAD CLICK ===")
    found_recents = False
    for match in re.findall(r'text="([^"]*)"', dump):
        if any(k in match for k in ["最近", "近期的文件", "图片", "音频", "视频", "文档"]):
            print(f"  [Direct SAF Node] {match}")
            found_recents = True
    for match in re.findall(r'content-desc="([^"]*)"', dump):
        if any(k in match for k in ["显示根目录", "网格视图", "搜索"]):
            print(f"  [Direct SAF Desc] {match}")

    take_screenshot("screen_v219_photos_direct_recents")

    # Dismiss SAF
    print("Dismissing SAF...", flush=True)
    adb("shell", "input", "keyevent", "4")
    time.sleep(2)

    # 4. Switch to Files tab (588, 1520)
    print("Switching to Files tab...", flush=True)
    adb("shell", "input", "tap", "588", "1520")
    time.sleep(2)

    dump = get_ui_dump()
    upload_bounds_f, text, desc = find_node(dump, "上传文件")
    if not upload_bounds_f:
        upload_bounds_f, text, desc = find_node(dump, "上传")
    if upload_bounds_f:
        print(f"Clicking upload in Files tab: {upload_bounds_f}...", flush=True)
        click_bounds(upload_bounds_f)
    else:
        adb("shell", "input", "tap", "816", "1364")
    time.sleep(2)

    dump = get_ui_dump()
    print("=== UI DUMP IN FILES TAB DIRECT UPLOAD ===")
    for match in re.findall(r'text="([^"]*)"', dump):
        if any(k in match for k in ["最近", "近期的文件", "图片", "音频", "视频", "文档"]):
            print(f"  [Files SAF Node] {match}")

    take_screenshot("screen_v219_files_direct_recents")
    adb("shell", "input", "keyevent", "4")

    print(f"All tests completed! Found recents: {found_recents}", flush=True)

if __name__ == "__main__":
    ok = wait_and_install_v219()
    if not ok:
        print("Install failed!")
        exit(1)
    run_tests()
