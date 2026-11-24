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
    subprocess.run("adb connect 127.0.0.1:16384", shell=True)
    p = subprocess.run(f"{ADB} exec-out screencap -p", shell=True, capture_output=True)
    with open(path, "wb") as f:
        f.write(p.stdout)
    print(f"Saved {name}: {len(p.stdout)} bytes to {path}")
    return path

def dump_ui():
    sh("shell uiautomator dump /sdcard/ui.xml")
    p = subprocess.run(f"{ADB} exec-out cat /sdcard/ui.xml", shell=True, capture_output=True, text=True)
    return p.stdout

def main():
    subprocess.run("adb connect 127.0.0.1:16384", shell=True)
    # 打开相册Tab
    sh("shell am start -n com.nasdrop/.MainActivity")
    time.sleep(1.5)
    
    # 切换到底部相册 Tab
    xml = dump_ui()
    # 底部导航相册按钮位置通常在屏幕底部中央
    # 看 xml 找到包含“相册”的条目
    matches = re.findall(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    
    # 点击底部的相册导航 (大约 y=1520 附近)
    for m in re.finditer(r'text="相册"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        x1, y1, x2, y2 = map(int, m.groups())
        print(f"Found 相册 tab at ({x1}, {y1}) - ({x2}, {y2})")
        sh(f"shell input tap {(x1+x2)//2} {(y1+y2)//2}")
        time.sleep(1)
        break
    
    screen("screen_v210_photos_list")
    
    # 点击第一张照片（相册网格大概在 x=150, y=300）
    print("Clicking first photo...")
    sh("shell input tap 150 300")
    time.sleep(2)
    screen("screen_v210_viewer_opened")
    
    # 查看是否有底栏“伴生记忆”按钮
    xml_viewer = dump_ui()
    print("Viewer dump length:", len(xml_viewer))
    memo_btn = re.search(r'text="伴生记忆"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml_viewer)
    if memo_btn:
        x1, y1, x2, y2 = map(int, memo_btn.groups())
        print(f"Clicking 伴生记忆 button at {(x1+x2)//2}, {(y1+y2)//2}")
        sh(f"shell input tap {(x1+x2)//2} {(y1+y2)//2}")
        time.sleep(1.5)
        screen("screen_v210_memory_sheet")
        
        # 寻找“文字记忆”或输入
        xml_sheet = dump_ui()
        edit_memo = re.search(r'text="[^\"]*文字[^\"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml_sheet)
        if edit_memo:
            ex1, ey1, ex2, ey2 = map(int, edit_memo.groups())
            sh(f"shell input tap {(ex1+ex2)//2} {(ey1+ey2)//2}")
            time.sleep(1)
            screen("screen_v210_memo_dialog")
            
            # 输入超长文字
            long_text = "这是一段非常详细生动的家庭珍贵伴生记忆故事。那天阳光格外明媚，全家人一起在公园里散步，微风徐徐吹过湖面，湖水泛起层层金色的波纹。孩子们在草地上欢快地奔跑追逐，笑声穿透了整个树林。爷爷奶奶坐在长椅上微笑着看着这一幕，母亲忙着拍照记录下这难得温馨的家庭瞬间，父亲则在旁边支起野餐帐篷，准备了香气扑鼻的午餐。这张照片抓拍下了最真实的幸福神情，不仅定格了那一刻的欢声笑语，更成为了全家人永远铭记的心灵记忆和岁月沉淀。"
            # 用 broadcast 或 setprop 输入，或者使用剪贴板
            sh(f"shell cmd clipboard set text '{long_text}'")
            # 粘贴
            sh("shell input keyevent 279") # PASTE
            time.sleep(0.5)
            # 点击确定保存
            xml_dlg = dump_ui()
            save_btn = re.search(r'text="保存"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml_dlg)
            if save_btn:
                sx1, sy1, sx2, sy2 = map(int, save_btn.groups())
                sh(f"shell input tap {(sx1+sx2)//2} {(sy1+sy2)//2}")
                time.sleep(1)
                
    # 截取保存后的照片常驻记忆卡片状态（默认折叠2行）
    screen("screen_v210_memo_collapsed")
    
    # 点击记忆胶囊展开（常驻记忆胶囊通常在屏幕底部，约 y=1350）
    print("Tapping memo capsule to expand...")
    sh("shell input tap 400 1350")
    time.sleep(1)
    screen("screen_v210_memo_expanded")
    
    # 在展开的卡片上向上滑动内部内容，验证滑动
    print("Scrolling inside memo capsule...")
    sh("shell input swipe 400 1350 400 1250 300")
    time.sleep(1)
    screen("screen_v210_memo_inner_scrolled")
    
    print("Memo UX test complete!")

if __name__ == '__main__':
    main()
