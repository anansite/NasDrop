import subprocess
import time
import os
import shutil
import urllib.request
import zipfile
import json
import xml.etree.ElementTree as ET

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
    adb("shell", "uiautomator", "dump", "/sdcard/window_dump.xml")
    res = subprocess.run([ADB, "-s", DEVICE, "shell", "cat", "/sdcard/window_dump.xml"], capture_output=True, text=True)
    return res.stdout

def find_node_by_text(xml_str, target_text):
    try:
        root = ET.fromstring(xml_str)
        for node in root.iter("node"):
            text = node.attrib.get("text", "")
            desc = node.attrib.get("content-desc", "")
            if target_text in text or target_text in desc:
                bounds = node.attrib.get("bounds", "")
                return bounds, text, desc
    except Exception as e:
        print("XML parse error:", e)
    return None, None, None

def click_bounds(bounds):
    import re
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        cx = (x1 + x2) // 2
        cy = (y1 + y2) // 2
        print(f"Clicking ({cx}, {cy}) for bounds {bounds}", flush=True)
        adb("shell", "input", "tap", str(cx), str(cy))
        time.sleep(1)
        return True
    return False

def wait_and_install():
    token = get_token()
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github.v3+json", "User-Agent": "NasDrop-CI"}

    # Find the latest run
    commit_sha = "893a5fa"
    print(f"Finding run for commit {commit_sha}...", flush=True)
    run_id = None
    for attempt in range(20):
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
        print("Failed to find run for commit!", flush=True)
        return False

    # Wait for completion
    for attempt in range(60):
        req = urllib.request.Request(f"https://api.github.com/repos/anansite/NasDrop/actions/runs/{run_id}", headers=headers)
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        status = data["status"]
        conclusion = data.get("conclusion")
        print(f"Run {run_id} status: {status}, conclusion: {conclusion}", flush=True)
        if status == "completed":
            if conclusion != "success":
                print(f"Build failed with conclusion: {conclusion}")
                return False
            break
        time.sleep(10)

    # Download artifact
    req = urllib.request.Request(f"https://api.github.com/repos/anansite/NasDrop/actions/runs/{run_id}/artifacts", headers=headers)
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    
    download_url = None
    for art in data.get("artifacts", []):
        if "x86_64" in art["name"]:
            download_url = art["archive_download_url"]
            print(f"Found artifact: {art['name']}, url: {download_url}", flush=True)
            break

    if not download_url:
        print("No x86_64 artifact found!")
        return False

    out_zip = os.path.join(ARTIFACTS_DIR, "v218_x86_64.zip")
    req = urllib.request.Request(download_url, headers=headers)
    with urllib.request.urlopen(req) as resp, open(out_zip, "wb") as f:
        shutil.copyfileobj(resp, f)
    print("Downloaded zip:", out_zip, flush=True)

    apk_path = None
    with zipfile.ZipFile(out_zip, 'r') as zf:
        zf.extractall(ARTIFACTS_DIR)
        for name in zf.namelist():
            if name.endswith(".apk"):
                apk_path = os.path.join(ARTIFACTS_DIR, name)
                print("Extracted APK:", apk_path, flush=True)
                break

    if not apk_path:
        print("No APK found in zip!")
        return False

    # Install APK
    ensure_connected()
    print("Installing APK to emulator...", flush=True)
    res = subprocess.run([ADB, "-s", DEVICE, "install", "-r", "-d", apk_path], capture_output=True, text=True)
    print("Install output:", res.stdout, res.stderr, flush=True)

    # Launch app
    print("Launching app...", flush=True)
    adb("shell", "am", "force-stop", "com.nasdrop")
    time.sleep(1)
    adb("shell", "am", "start", "-n", "com.nasdrop/.MainActivity")
    time.sleep(3)

    return True

if __name__ == "__main__":
    ok = wait_and_install()
    if not ok:
        print("Build or install failed!")
        exit(1)
    print("Ready for automated verification!")
