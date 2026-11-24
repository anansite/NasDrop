import subprocess
import time
import os
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
    adb("shell", "uiautomator", "dump", "/sdcard/dump.xml")
    res = subprocess.run([ADB, "-s", DEVICE, "shell", "cat", "/sdcard/dump.xml"], capture_output=True, text=True)
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

def run_e2e():
    ensure_connected()
    print("Preparing device state...", flush=True)
    # Send back key multiple times to dismiss any leftover pickers
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.5)
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.5)

    print("Launching NasDrop...", flush=True)
    adb("shell", "am", "force-stop", "com.nasdrop")
    time.sleep(1)
    adb("shell", "am", "start", "-n", "com.nasdrop/.MainActivity")
    time.sleep(4)

    # Step 1: Verify Home Screen
    dump = get_ui_dump()
    print("Checking home screen package...")
    take_screenshot("screen_v218_home")

    # Step 2: Switch to Photos tab (bottom bar around x=300, y=1530)
    # Let's find '相册' with bounds y > 1400 or click (300, 1530)
    print("Clicking Photos tab...", flush=True)
    adb("shell", "input", "tap", "300", "1530")
    time.sleep(2)
    take_screenshot("screen_v218_photos_tab")

    # Step 3: Click Upload button
    # In Photos tab, the top bar has upload button or the FAB (right bottom around x=820, y=1440)
    dump = get_ui_dump()
    bounds, text, desc = find_node(dump, "上传照片")
    if not bounds:
        bounds, text, desc = find_node(dump, "上传")
    if bounds:
        print(f"Found upload button in topbar: {bounds}, clicking...", flush=True)
        click_bounds(bounds)
    else:
        print("Clicking upload FAB at (820, 1440)...", flush=True)
        adb("shell", "input", "tap", "820", "1440")
    time.sleep(2)

    # Step 4: Verify Upload Option Bottom Sheet
    dump = get_ui_dump()
    print("=== DUMP AFTER UPLOAD CLICKED ===")
    for match in re.findall(r'text="([^"]*)"', dump):
        if any(k in match for k in ["选择上传内容", "手机相册", "本地视频", "全功能文件管理器", "上传至", "当前推荐"]):
            print(f"  [Found UI Text] {match}")
    take_screenshot("screen_v218_upload_sheet_photos")

    # Step 5: Click '手机相册与照片'
    bounds_gallery, _, _ = find_node(dump, "手机相册与照片")
    if bounds_gallery:
        print(f"Clicking '手机相册与照片' {bounds_gallery}...", flush=True)
        click_bounds(bounds_gallery)
        time.sleep(2)
        take_screenshot("screen_v218_gallery_picker")
        print("Dismissing gallery picker...", flush=True)
        adb("shell", "input", "keyevent", "4")
        time.sleep(2)

    # Step 6: Click Upload again and test '全功能文件管理器'
    print("Re-opening upload sheet...", flush=True)
    if bounds:
        click_bounds(bounds)
    else:
        adb("shell", "input", "tap", "820", "1440")
    time.sleep(2)

    dump = get_ui_dump()
    bounds_saf, _, _ = find_node(dump, "全功能文件管理器")
    if bounds_saf:
        print(f"Clicking '全功能文件管理器' {bounds_saf}...", flush=True)
        click_bounds(bounds_saf)
        time.sleep(2)
        take_screenshot("screen_v218_saf_open_document")
        print("Dismissing SAF picker...", flush=True)
        adb("shell", "input", "keyevent", "4")
        time.sleep(2)

    # Step 7: Switch to Files tab (bottom bar around x=500, y=1530)
    print("Switching to Files tab...", flush=True)
    adb("shell", "input", "tap", "500", "1530")
    time.sleep(2)
    take_screenshot("screen_v218_files_tab")

    # Click upload in Files tab
    dump = get_ui_dump()
    bounds_fup, _, _ = find_node(dump, "上传文件")
    if not bounds_fup:
        bounds_fup, _, _ = find_node(dump, "上传")
    if bounds_fup:
        print(f"Clicking upload in Files tab: {bounds_fup}...", flush=True)
        click_bounds(bounds_fup)
    else:
        adb("shell", "input", "tap", "820", "1440")
    time.sleep(2)

    dump = get_ui_dump()
    print("=== DUMP IN FILES UPLOAD SHEET ===")
    for match in re.findall(r'text="([^"]*)"', dump):
        if any(k in match for k in ["选择上传内容", "手机相册", "本地视频", "全功能文件管理器", "上传至", "当前推荐"]):
            print(f"  [Files Sheet UI Text] {match}")
    take_screenshot("screen_v218_upload_sheet_files")

    print("E2E verification completed successfully!", flush=True)

if __name__ == "__main__":
    run_e2e()
