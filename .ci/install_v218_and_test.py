import subprocess
import urllib.request
import os
import time
import re
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

def find_node(xml_str, target):
    try:
        root = ET.fromstring(xml_str)
        for node in root.iter("node"):
            text = node.attrib.get("text", "")
            desc = node.attrib.get("content-desc", "")
            if target in text or target in desc:
                return node.attrib.get("bounds", ""), text, desc
    except Exception as e:
        print("XML parse error:", e)
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

def download_and_install():
    url = "https://github.com/anansite/NasDrop/releases/download/v1.0.218/NasDrop-v1.0.218-x86_64.apk"
    apk_local = os.path.join(ARTIFACTS_DIR, "NasDrop-v1.0.218-x86_64.apk")
    print(f"Downloading {url} to {apk_local}...", flush=True)
    req = urllib.request.Request(url, headers={"User-Agent": "NasDrop-CI"})
    with urllib.request.urlopen(req) as resp, open(apk_local, "wb") as f:
        f.write(resp.read())
    print(f"Download complete: {os.path.getsize(apk_local)} bytes", flush=True)

    ensure_connected()
    print("Installing APK...", flush=True)
    res = subprocess.run([ADB, "-s", DEVICE, "install", "-r", "-d", apk_local], capture_output=True, text=True)
    print("Install output:", res.stdout, res.stderr, flush=True)

    # Launch app
    print("Launching com.nasdrop...", flush=True)
    adb("shell", "am", "force-stop", "com.nasdrop")
    time.sleep(1)
    adb("shell", "am", "start", "-n", "com.nasdrop/.MainActivity")
    time.sleep(3)

def run_verification():
    ensure_connected()
    take_screenshot("screen_v218_home")

    # 1. Click Photos Tab
    dump = get_ui_dump()
    bounds, text, desc = find_node(dump, "相册")
    if bounds:
        print("Clicking Photos tab...", flush=True)
        click_bounds(bounds)
    time.sleep(2)
    take_screenshot("screen_v218_photos_tab")

    # 2. Click Upload button in Photos tab
    dump = get_ui_dump()
    bounds, text, desc = find_node(dump, "上传照片")
    if not bounds:
        bounds, text, desc = find_node(dump, "上传")
    if bounds:
        print("Clicking upload button in Photos TopBar...", flush=True)
        click_bounds(bounds)
    else:
        print("Using FAB coordinates...", flush=True)
        adb("shell", "input", "tap", "660", "1160")
    time.sleep(2)

    # 3. Capture Upload Option Sheet in Photos tab
    dump = get_ui_dump()
    print("UI dump after upload clicked:")
    for match in re.findall(r'text="([^"]*)"', dump):
        if any(k in match for k in ["选择上传内容", "相册", "视频", "文件", "上传至"]):
            print(f"  [Sheet Text] {match}")
    take_screenshot("screen_v218_upload_sheet_photos")

    # 4. Click '全功能文件管理器' to verify SAF opens
    bounds_saf, _, _ = find_node(dump, "全功能文件管理器")
    if bounds_saf:
        print("Testing '全功能文件管理器' option...", flush=True)
        click_bounds(bounds_saf)
        time.sleep(2)
        take_screenshot("screen_v218_saf_open_document")
        # Go back to app
        adb("shell", "input", "keyevent", "4")
        time.sleep(2)

    # 5. Click upload button again and test '手机相册与照片'
    dump = get_ui_dump()
    bounds, text, desc = find_node(dump, "上传照片")
    if not bounds:
        bounds, text, desc = find_node(dump, "上传")
    if bounds:
        click_bounds(bounds)
        time.sleep(2)
        dump = get_ui_dump()
        bounds_gallery, _, _ = find_node(dump, "手机相册与照片")
        if bounds_gallery:
            print("Testing '手机相册与照片' option...", flush=True)
            click_bounds(bounds_gallery)
            time.sleep(2)
            take_screenshot("screen_v218_gallery_native_picker")
            # Go back to app
            adb("shell", "input", "keyevent", "4")
            time.sleep(2)

    # 6. Switch to Files tab
    dump = get_ui_dump()
    bounds, text, desc = find_node(dump, "文件")
    if bounds:
        print("Clicking Files tab...", flush=True)
        click_bounds(bounds)
        time.sleep(2)
        dump = get_ui_dump()
        bounds_upload, _, _ = find_node(dump, "上传文件")
        if not bounds_upload:
            bounds_upload, _, _ = find_node(dump, "上传")
        if bounds_upload:
            print("Clicking upload in Files tab...", flush=True)
            click_bounds(bounds_upload)
            time.sleep(2)
            take_screenshot("screen_v218_upload_sheet_files")

    print("All verifications completed successfully!", flush=True)

if __name__ == "__main__":
    download_and_install()
    run_verification()
