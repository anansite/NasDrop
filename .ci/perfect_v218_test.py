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
    adb("shell", "uiautomator", "dump", "/sdcard/v218_dump.xml")
    res = subprocess.run([ADB, "-s", DEVICE, "shell", "cat", "/sdcard/v218_dump.xml"], capture_output=True, text=True)
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

def run():
    ensure_connected()
    print("Capturing v218 Home Screen...", flush=True)
    take_screenshot("screen_v218_home")

    # 1. Click Photos Tab (417, 1520)
    print("Switching to Photos tab at (417, 1520)...", flush=True)
    adb("shell", "input", "tap", "417", "1520")
    time.sleep(2)
    take_screenshot("screen_v218_photos_tab")

    # 2. Find upload button in Photos Tab
    dump = get_ui_dump()
    upload_bounds, text, desc = find_node(dump, "上传")
    if upload_bounds:
        print(f"Found upload button in Photos tab: {upload_bounds} ({text}/{desc})", flush=True)
        click_bounds(upload_bounds)
    else:
        # Check FAB position in dump: looking for FileUpload or size ~100
        print("Looking for FAB in dump...")
        root = ET.fromstring(dump)
        for node in root.iter("node"):
            bounds = node.attrib.get("bounds", "")
            desc = node.attrib.get("content-desc", "")
            if "上传" in desc or "快速上传" in desc:
                upload_bounds = bounds
                break
        if upload_bounds:
            click_bounds(upload_bounds)
        else:
            print("Clicking topbar upload at (650, 100)...", flush=True)
            adb("shell", "input", "tap", "650", "100")
    time.sleep(2)

    # 3. Capture Upload Option Bottom Sheet in Photos tab
    dump = get_ui_dump()
    print("=== UPLOAD OPTION BOTTOM SHEET (PHOTOS) ===")
    for match in re.findall(r'text="([^"]*)"', dump):
        if any(k in match for k in ["选择上传内容", "手机相册", "本地视频", "全功能文件管理器", "上传至", "当前推荐"]):
            print(f"  [Sheet Text] {match}")
    take_screenshot("screen_v218_upload_sheet_photos")

    # 4. Click '手机相册与照片'
    bounds_gal, _, _ = find_node(dump, "手机相册与照片")
    if bounds_gal:
        print(f"Clicking '手机相册与照片' {bounds_gal}...", flush=True)
        click_bounds(bounds_gal)
        time.sleep(2)
        take_screenshot("screen_v218_gallery_picker")
        print("Dismissing gallery picker...", flush=True)
        adb("shell", "input", "keyevent", "4")
        time.sleep(2)

    # 5. Re-open upload sheet and test '全功能文件管理器'
    print("Re-opening upload sheet...", flush=True)
    if upload_bounds:
        click_bounds(upload_bounds)
    else:
        adb("shell", "input", "tap", "650", "100")
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

    # 6. Switch to Files tab (588, 1520)
    print("Switching to Files tab at (588, 1520)...", flush=True)
    adb("shell", "input", "tap", "588", "1520")
    time.sleep(2)
    take_screenshot("screen_v218_files_tab")

    dump = get_ui_dump()
    upload_bounds_files, text, desc = find_node(dump, "上传")
    if upload_bounds_files:
        print(f"Found upload button in Files tab: {upload_bounds_files} ({text}/{desc})", flush=True)
        click_bounds(upload_bounds_files)
    else:
        adb("shell", "input", "tap", "650", "100")
    time.sleep(2)

    dump = get_ui_dump()
    print("=== UPLOAD OPTION BOTTOM SHEET (FILES) ===")
    for match in re.findall(r'text="([^"]*)"', dump):
        if any(k in match for k in ["选择上传内容", "手机相册", "本地视频", "全功能文件管理器", "上传至", "当前推荐"]):
            print(f"  [Files Sheet Text] {match}")
    take_screenshot("screen_v218_upload_sheet_files")

    print("ALL TESTS COMPLETE!", flush=True)

if __name__ == "__main__":
    run()
