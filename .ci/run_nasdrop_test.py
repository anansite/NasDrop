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
    adb("shell", "uiautomator", "dump", "/sdcard/nasdrop_dump.xml")
    res = subprocess.run([ADB, "-s", DEVICE, "shell", "cat", "/sdcard/nasdrop_dump.xml"], capture_output=True, text=True)
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

def run():
    ensure_connected()
    print("Launching NasDrop...", flush=True)
    adb("shell", "am", "force-stop", "com.nasdrop")
    time.sleep(1)
    adb("shell", "am", "start", "-n", "com.nasdrop/.MainActivity")
    time.sleep(4)

    # Dump UI
    dump = get_ui_dump()
    print("Finding bottom bar items...")
    root = ET.fromstring(dump)
    for node in root.iter('node'):
        text = node.attrib.get('text', '')
        desc = node.attrib.get('content-desc', '')
        bounds = node.attrib.get('bounds', '')
        if any(k in text or k in desc for k in ['首页', '相册', '文件', '影音', '上传', '设置', '云端', '默认']):
            print(f"[{bounds}] Text: '{text}' | Desc: '{desc}'")

    take_screenshot("screen_v218_home")

    # Click bottom bar '相册' (its y bounds should be near the bottom, e.g. y > 1400)
    photos_tab_bounds = None
    for node in root.iter('node'):
        text = node.attrib.get('text', '')
        desc = node.attrib.get('content-desc', '')
        bounds = node.attrib.get('bounds', '')
        if text == "相册" or desc == "相册":
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
            if m and int(m.group(2)) > 1000:
                photos_tab_bounds = bounds
                break

    if photos_tab_bounds:
        print("Clicking bottom Photos tab:", photos_tab_bounds)
        click_bounds(photos_tab_bounds)
    else:
        # Fallback click bottom bar second tab
        print("Fallback click at bottom bar for Photos tab (270, 1530)")
        adb("shell", "input", "tap", "270", "1530")
        time.sleep(2)

    take_screenshot("screen_v218_photos_tab")

    # In Photos tab, find upload button
    dump = get_ui_dump()
    upload_bounds = None
    root = ET.fromstring(dump)
    for node in root.iter('node'):
        text = node.attrib.get('text', '')
        desc = node.attrib.get('content-desc', '')
        bounds = node.attrib.get('bounds', '')
        if "上传" in text or "上传" in desc:
            print("Found upload node:", text, desc, bounds)
            upload_bounds = bounds
            break

    if upload_bounds:
        print("Clicking upload button in Photos TopBar:", upload_bounds)
        click_bounds(upload_bounds)
    else:
        # Try FAB: in v218 DraggableUploadFab is around (800, 1400) or check dump
        print("Looking for FAB or clicking default FAB position...")
        adb("shell", "input", "tap", "800", "1400")
        time.sleep(2)

    take_screenshot("screen_v218_upload_sheet_photos")

    # Check upload sheet contents
    dump = get_ui_dump()
    print("Checking upload sheet contents:")
    for match in re.findall(r'text="([^"]*)"', dump):
        if any(k in match for k in ["选择上传内容", "相册", "视频", "文件", "上传至", "全功能"]):
            print(f"  [Sheet Option] {match}")

    # Test clicking '手机相册与照片'
    bounds_gallery, _, _ = find_node(dump, "手机相册与照片")
    if bounds_gallery:
        print("Clicking '手机相册与照片' option...")
        click_bounds(bounds_gallery)
        time.sleep(2)
        take_screenshot("screen_v218_gallery_picker")
        adb("shell", "input", "keyevent", "4") # Back
        time.sleep(2)

    # Click upload again and test '全功能文件管理器'
    if upload_bounds:
        click_bounds(upload_bounds)
        time.sleep(2)
        dump = get_ui_dump()
        bounds_saf, _, _ = find_node(dump, "全功能文件管理器")
        if bounds_saf:
            print("Clicking '全功能文件管理器' option...")
            click_bounds(bounds_saf)
            time.sleep(2)
            take_screenshot("screen_v218_saf_open_document")
            adb("shell", "input", "keyevent", "4") # Back
            time.sleep(2)

    # Test Files Tab
    files_tab_bounds = None
    dump = get_ui_dump()
    root = ET.fromstring(dump)
    for node in root.iter('node'):
        text = node.attrib.get('text', '')
        desc = node.attrib.get('content-desc', '')
        bounds = node.attrib.get('bounds', '')
        if text == "文件" or desc == "文件":
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
            if m and int(m.group(2)) > 1000:
                files_tab_bounds = bounds
                break
    if files_tab_bounds:
        print("Clicking Files tab:", files_tab_bounds)
        click_bounds(files_tab_bounds)
        time.sleep(2)
        take_screenshot("screen_v218_files_tab")

        dump = get_ui_dump()
        bounds_fupload, _, _ = find_node(dump, "上传")
        if bounds_fupload:
            print("Clicking upload in Files tab:", bounds_fupload)
            click_bounds(bounds_fupload)
            time.sleep(2)
            take_screenshot("screen_v218_upload_sheet_files")

    print("Automation finished successfully!")

if __name__ == "__main__":
    run()
