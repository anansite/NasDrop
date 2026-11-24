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
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res

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
        print(f"Captured & saved {name} to artifacts ({os.path.getsize(target)} bytes)")
    else:
        print(f"Failed to capture {name}")

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
    # e.g. [148,638][424,682]
    import re
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds_str)
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None, None

def main():
    ensure_connected()
    
    nodes = dump_ui()
    print("Current UI texts:")
    for t, b, _ in nodes:
        print(f"  {t} -> {b}")

    # Check if on settings page
    has_save = any("保存配置" in t for t, _, _ in nodes)
    if has_save:
        for t, b, _ in nodes:
            if "保存配置" in t:
                x, y = parse_bounds(b)
                print(f"Clicking 保存配置 at {x}, {y}")
                tap(x, y)
                break
        time.sleep(2)
        cap("screen_v215_settings_saved.png")

    # Check bottom navigation for 首页
    nodes = dump_ui()
    home_clicked = False
    for t, b, _ in nodes:
        if t == "首页":
            x, y = parse_bounds(b)
            print(f"Clicking 首页 at {x}, {y}")
            tap(x, y)
            home_clicked = True
            break
    
    if not home_clicked:
        # Fallback tap coordinates for 首页 tab
        tap(142, 1520)

    time.sleep(3)
    nodes = dump_ui()
    print("\nAfter clicking 首页:")
    for t, b, _ in nodes:
        print(f"  {t} -> {b}")
    cap("screen_v215_home.png")

    # Look for 时光杂货铺 or 时光漫游 or 小游戏
    game_target = None
    for t, b, _ in nodes:
        if "时光杂货铺" in t or "时光漫游" in t or "杂货铺" in t:
            game_target = b
            print(f"Found game target: {t} -> {b}")
            break

    if game_target:
        x, y = parse_bounds(game_target)
        tap(x, y)
    else:
        # If not visible, scroll down and check
        print("Scrolling down to find game center...")
        adb("shell", "input", "swipe", "450", "1200", "450", "400", "300")
        time.sleep(2)
        nodes = dump_ui()
        for t, b, _ in nodes:
            print(f"  (after scroll) {t} -> {b}")
            if "时光杂货铺" in t or "时光漫游" in t:
                x, y = parse_bounds(b)
                tap(x, y)
                break

    time.sleep(3)
    nodes = dump_ui()
    print("\nInside Game Center:")
    for t, b, _ in nodes:
        print(f"  {t} -> {b}")
    cap("screen_v215_game_home.png")

    # If there is a 时光漫游抽取 card or button, tap it
    roam_button = None
    for t, b, _ in nodes:
        if "时光漫游抽取" in t or "时光漫游" in t or "开始抽取" in t:
            roam_button = b
            print(f"Found roam button: {t} -> {b}")
            break
    if roam_button:
        x, y = parse_bounds(roam_button)
        tap(x, y)
        time.sleep(2)
        nodes = dump_ui()
        print("\nInside Roam Screen:")
        for t, b, _ in nodes:
            print(f"  {t} -> {b}")

    cap("screen_v215_roam_screen.png")

    # Now let's test album selection: look for the top album capsule e.g. "全部照片" or "相册"
    album_capsule = None
    for t, b, _ in nodes:
        if "全部照片" in t or "相册" in t or "已选" in t:
            album_capsule = (t, b)
            print(f"Found album capsule: {t} -> {b}")
            break

    if album_capsule:
        x, y = parse_bounds(album_capsule[1])
        print(f"Tapping album capsule at {x}, {y}")
        tap(x, y)
        time.sleep(2)
        nodes = dump_ui()
        print("\nInside Album Sheet:")
        for t, b, _ in nodes:
            print(f"  {t} -> {b}")
        cap("screen_v215_game_multi_albums_sheet.png")

        # Let's select two albums if present
        album_items = []
        for t, b, _ in nodes:
            # Look for albums, which usually have张 or specific names
            if "张" in t or "Album" in t or "Pictures" in t or "DCIM" in t or "/" in t:
                album_items.append((t, b))
                print(f"Found album item candidate: {t} -> {b}")

        # If there are albums in the list, tap on 1 or 2 of them to toggle selection
        # Note: we also have "全选相册"
        for t, b, _ in nodes:
            if "全选相册" in t:
                print(f"Found 全选相册 -> tapping it")
                x, y = parse_bounds(b)
                tap(x, y)
                time.sleep(1)
                break

        cap("screen_v215_game_multi_albums_sheet_selected.png")

        # Now click 确定抽取
        nodes = dump_ui()
        for t, b, _ in nodes:
            if "确定" in t or "确定抽取" in t:
                print(f"Clicking 确定抽取 at {b}")
                x, y = parse_bounds(b)
                tap(x, y)
                break

        time.sleep(2)
        nodes = dump_ui()
        print("\nAfter Confirming Multi-album:")
        for t, b, _ in nodes:
            print(f"  {t} -> {b}")
        cap("screen_v215_game_running_multi.png")

if __name__ == "__main__":
    main()
