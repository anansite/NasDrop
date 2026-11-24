import subprocess
import time
import os
import shutil
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

def tap(x, y):
    adb("shell", "input", "tap", str(x), str(y))

def cap(name):
    adb("shell", "screencap", "-p", f"/sdcard/{name}")
    local_path = os.path.join(os.getcwd(), name)
    adb("pull", f"/sdcard/{name}", local_path)
    if os.path.exists(local_path):
        target = os.path.join(ARTIFACTS_DIR, name)
        shutil.copyfile(local_path, target)
        print(f"Captured & saved {name} to artifacts ({os.path.getsize(target)} bytes)", flush=True)

def dump_ui():
    adb("shell", "uiautomator", "dump", "/sdcard/dump.xml")
    adb("pull", "/sdcard/dump.xml", "dump.xml")
    if not os.path.exists("dump.xml"):
        return []
    tree = ET.parse("dump.xml")
    nodes = []
    for node in tree.getroot().iter("node"):
        text = node.attrib.get("text", "") or node.attrib.get("content-desc", "")
        bounds = node.attrib.get("bounds", "")
        resource_id = node.attrib.get("resource-id", "")
        if text:
            nodes.append((text, bounds, resource_id))
    return nodes

def parse_bounds(bounds_str):
    import re
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds_str)
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None, None

def run_flow():
    ensure_connected()
    print("Running v216 verification flow...", flush=True)
    
    # Wait for UI to settle
    time.sleep(2)
    nodes = dump_ui()
    for t, b, _ in nodes:
        print(f"  {t} -> {b}", flush=True)
    
    # If on settings page or notification dialog
    for t, b, _ in nodes:
        if "允许" in t:
            x, y = parse_bounds(b)
            print(f"Dismissing notification permission: {t} at {x}, {y}", flush=True)
            tap(x, y)
            time.sleep(1)
            nodes = dump_ui()
            break

    # If on settings, click '保存配置'
    for t, b, _ in nodes:
        if "保存配置" in t:
            x, y = parse_bounds(b)
            print(f"Clicking 保存配置 at {x}, {y}", flush=True)
            tap(x, y)
            time.sleep(2)
            cap("screen_v216_settings_saved.png")
            break

    # Click 首页
    nodes = dump_ui()
    for t, b, _ in nodes:
        if t == "首页":
            x, y = parse_bounds(b)
            print(f"Clicking 首页 at {x}, {y}", flush=True)
            tap(x, y)
            break
            
    time.sleep(3)
    nodes = dump_ui()
    print("\nOn Home Screen:", flush=True)
    for t, b, _ in nodes:
        print(f"  {t} -> {b}", flush=True)
    cap("screen_v216_home.png")

    # Scroll down to find 时光杂货铺
    print("Scrolling down to find 时光杂货铺...", flush=True)
    adb("shell", "input", "swipe", "450", "1200", "450", "400", "300")
    time.sleep(2)
    nodes = dump_ui()
    for t, b, _ in nodes:
        print(f"  (after scroll) {t} -> {b}", flush=True)
        if "时光杂货铺" in t or "时光漫游" in t:
            x, y = parse_bounds(b)
            print(f"Clicking game entrance: {t} at {x}, {y}", flush=True)
            tap(x, y)
            break

    time.sleep(3)
    nodes = dump_ui()
    print("\nInside Game Center / Roam:", flush=True)
    for t, b, _ in nodes:
        print(f"  {t} -> {b}", flush=True)
    cap("screen_v216_game_screen.png")

    # If there is 时光漫游抽取 button
    for t, b, _ in nodes:
        if "漫游" in t or "开始抽取" in t or "抽取" in t:
            x, y = parse_bounds(b)
            print(f"Tapping roam button: {t} at {x}, {y}", flush=True)
            tap(x, y)
            time.sleep(2)
            break

    nodes = dump_ui()
    cap("screen_v216_game_roam.png")

    # Look for top album selector chip
    album_chip = None
    for t, b, _ in nodes:
        if "全部照片" in t or "相册" in t or "已选" in t:
            album_chip = (t, b)
            print(f"Found album chip: {t} -> {b}", flush=True)
            break

    if album_chip:
        x, y = parse_bounds(album_chip[1])
        print(f"Clicking album chip at {x}, {y}", flush=True)
        tap(x, y)
        time.sleep(2)
        nodes = dump_ui()
        print("\nMulti-album bottom sheet opened:", flush=True)
        for t, b, _ in nodes:
            print(f"  {t} -> {b}", flush=True)
        cap("screen_v216_multi_albums_sheet.png")

        # Click first album checkbox/row to select/deselect
        # Also let's check for '全选相册'
        for t, b, _ in nodes:
            if "全选相册" in t:
                x, y = parse_bounds(b)
                print(f"Tapping 全选相册 at {x}, {y}", flush=True)
                tap(x, y)
                time.sleep(1)
                break

        nodes = dump_ui()
        cap("screen_v216_multi_albums_selected.png")

        # Click '确定抽取'
        for t, b, _ in nodes:
            if "确定" in t or "确定抽取" in t:
                x, y = parse_bounds(b)
                print(f"Clicking 确定抽取 at {x}, {y}", flush=True)
                tap(x, y)
                break

        time.sleep(2)
        nodes = dump_ui()
        print("\nAfter Confirming Multi-album selection:", flush=True)
        for t, b, _ in nodes:
            print(f"  {t} -> {b}", flush=True)
        cap("screen_v216_game_drawing_multi.png")

if __name__ == "__main__":
    run_flow()
