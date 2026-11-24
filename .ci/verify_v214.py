import subprocess
import time
import os
import xml.etree.ElementTree as ET

ADB = r"C:\Program Files\Netease\MuMu\nx_main\adb.exe"
DEV = "127.0.0.1:16384"
ARTIFACT_DIR = r"C:\Users\admin\.gemini\antigravity-cli\brain\d6bc982b-0100-4c6e-8abd-14870e722c80"

def adb_cmd(cmd):
    return subprocess.run([ADB, "-s", DEV] + cmd, capture_output=True, text=True)

def tap(x, y):
    adb_cmd(["shell", "input", "tap", str(x), str(y)])
    time.sleep(1)

def dump_ui():
    adb_cmd(["shell", "uiautomator", "dump", "/sdcard/window_dump.xml"])
    res = adb_cmd(["shell", "cat", "/sdcard/window_dump.xml"])
    return res.stdout

def find_nodes_by_text(xml_str, text_sub):
    nodes = []
    try:
        root = ET.fromstring(xml_str)
        for elem in root.iter():
            txt = elem.attrib.get("text", "")
            desc = elem.attrib.get("content-desc", "")
            if text_sub in txt or text_sub in desc:
                bounds = elem.attrib.get("bounds", "")
                nodes.append((txt or desc, bounds, elem))
    except Exception as e:
        print("XML parse error:", e)
    return nodes

def parse_bounds(bounds_str):
    # e.g. [120,450][960,520]
    import re
    m = re.findall(r'\[(\d+),(\d+)\]', bounds_str)
    if len(m) == 2:
        x1, y1 = int(m[0][0]), int(m[0][1])
        x2, y2 = int(m[1][0]), int(m[1][1])
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None

def screenshot(filename):
    os.makedirs(ARTIFACT_DIR, exist_ok=True)
    local_path = os.path.join(ARTIFACT_DIR, filename)
    adb_cmd(["shell", "screencap", "-p", "/sdcard/screen.png"])
    adb_cmd(["pull", "/sdcard/screen.png", local_path])
    print(f"Saved screenshot: {local_path}")
    return local_path

print("1. Connecting ADB...")
subprocess.run([ADB, "connect", DEV], check=False)

print("2. Starting NasDrop...")
adb_cmd(["shell", "am", "force-stop", "com.nasdrop"])
adb_cmd(["shell", "am", "start", "-n", "com.nasdrop/.MainActivity"])
time.sleep(3)

screenshot("screen_v214_home.png")

xml = dump_ui()
print("Home UI dumped. Checking for 时光杂货铺...")
nodes = find_nodes_by_text(xml, "时光杂货铺")
if not nodes:
    nodes = find_nodes_by_text(xml, "趣味小店")
print("Store nodes:", nodes)

if nodes:
    cx, cy = parse_bounds(nodes[0][1])
    print(f"Tapping 时光杂货铺 at ({cx}, {cy})...")
    tap(cx, cy)
    time.sleep(2)
    screenshot("screen_v214_game_sheet.png")

    xml = dump_ui()
    roam_nodes = find_nodes_by_text(xml, "时光漫游抽取")
    print("Roam nodes:", roam_nodes)
    if roam_nodes:
        rx, ry = parse_bounds(roam_nodes[0][1])
        print(f"Tapping 时光漫游抽取 at ({rx}, {ry})...")
        tap(rx, ry)
        time.sleep(3)
        screenshot("screen_v214_game_screen.png")

        xml = dump_ui()
        # Find album pill on top
        pill_nodes = find_nodes_by_text(xml, "全部照片")
        if not pill_nodes:
            pill_nodes = find_nodes_by_text(xml, "相册")
        print("Pill nodes:", pill_nodes)

        # Tap top album selector pill
        # Usually centered around (540, 100) or find pill
        if pill_nodes:
            px, py = parse_bounds(pill_nodes[0][1])
            tap(px, py)
        else:
            tap(540, 110)
        time.sleep(2)

        screenshot("screen_v214_album_multi_select.png")
        xml = dump_ui()
        print("Album Picker Sheet opened.")
        
        # Check for multi-select features
        print("Checking multi-select buttons...")
        print("全选相册:", find_nodes_by_text(xml, "全选相册"))
        print("全部照片:", find_nodes_by_text(xml, "全部相册 (全局混抽)"))
        print("确定抽取:", find_nodes_by_text(xml, "确定抽取"))

        # Find folders in the sheet
        folders = [n for n in find_nodes_by_text(xml, "张") if "全" not in n[0]]
        print("Folder items:", folders)

        # Toggle first album folder if found
        all_lines = dump_ui()
        for text, bounds, _ in find_nodes_by_text(all_lines, "张"):
            # tap slightly above or on the item
            fx, fy = parse_bounds(bounds)
            if fy < 1600 and "全" not in text:
                print(f"Toggling folder item at ({fx}, {fy}) [{text}]")
                tap(fx, fy)
                time.sleep(1)
                break

        screenshot("screen_v214_album_selected_one.png")

        # Now tap 确定抽取
        xml = dump_ui()
        confirm_nodes = find_nodes_by_text(xml, "确定抽取")
        if confirm_nodes:
            cx, cy = parse_bounds(confirm_nodes[0][1])
            print(f"Tapping 确定抽取 at ({cx}, {cy})...")
            tap(cx, cy)
            time.sleep(2)
            screenshot("screen_v214_game_after_selection.png")

print("Verification script completed.")
