import subprocess
import time
import os
import re

ADB = "adb -s 127.0.0.1:16384"

def sh(cmd):
    p = subprocess.run(f"{ADB} {cmd}", shell=True, capture_output=True, text=True)
    return p.stdout.strip()

def screen(name):
    path = os.path.join(r"C:\Users\admin\.gemini\antigravity-cli\brain\d6bc982b-0100-4c6e-8abd-14870e722c80", f"{name}.png")
    subprocess.run(f"adb connect 127.0.0.1:16384", shell=True)
    p = subprocess.run(f"{ADB} exec-out screencap -p", shell=True, capture_output=True)
    with open(path, "wb") as f:
        f.write(p.stdout)
    print(f"Saved {name}: {len(p.stdout)} bytes to {path}")
    return path

def dump_ui():
    sh("shell uiautomator dump /sdcard/ui.xml")
    p = subprocess.run(f"{ADB} exec-out cat /sdcard/ui.xml", shell=True, capture_output=True, text=True)
    return p.stdout

def get_bounds(xml, keyword):
    matches = re.findall(rf'text="([^"]*{keyword}[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    if matches:
        t, x1, y1, x2, y2 = matches[0]
        return int(t) if False else (t, (int(x1)+int(x2))//2, (int(y1)+int(y2))//2)
    # also check content-desc
    matches2 = re.findall(rf'content-desc="([^"]*{keyword}[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    if matches2:
        t, x1, y1, x2, y2 = matches2[0]
        return (t, (int(x1)+int(x2))//2, (int(y1)+int(y2))//2)
    return None

def main():
    subprocess.run("adb connect 127.0.0.1:16384", shell=True)
    print("Devices:", subprocess.run("adb devices", shell=True, capture_output=True, text=True).stdout)
    
    # 确保在前台
    sh("shell am start -n com.nasdrop/.MainActivity")
    time.sleep(2)
    
    xml = dump_ui()
    # 找到相册 Tab
    res = get_bounds(xml, "相册")
    print("Photos tab:", res)
    if res:
        sh(f"shell input tap {res[1]} {res[2]}")
        time.sleep(1.5)
        screen("screen_v210_photos_tab")
        
        # 测试在相册页面滑动
        print("Testing Photos scroll fling...")
        sh("shell input swipe 540 1200 540 400 150") # 快速上滑
        time.sleep(1)
        screen("screen_v210_photos_scrolled")
        sh("shell input swipe 540 400 540 1200 150") # 快速下滑恢复
        time.sleep(1)

    # 找到全部文件 Tab
    xml = dump_ui()
    res_files = get_bounds(xml, "全部文件") or get_bounds(xml, "文件")
    print("Files tab:", res_files)
    if res_files:
        sh(f"shell input tap {res_files[1]} {res_files[2]}")
        time.sleep(1.5)
        screen("screen_v210_files_tab")
        
        # 测试在文件页面滑动
        print("Testing Files scroll fling...")
        sh("shell input swipe 540 1200 540 400 150")
        time.sleep(1)
        screen("screen_v210_files_scrolled")
        sh("shell input swipe 540 400 540 1200 150")
        time.sleep(1)

if __name__ == '__main__':
    main()
