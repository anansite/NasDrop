import subprocess, time, os, re
import xml.etree.ElementTree as ET

ADB = 'adb -s 127.0.0.1:16384'
def sh(cmd):
    return subprocess.run(f'{ADB} {cmd}', shell=True, capture_output=True, text=True).stdout.strip()

def screen(name):
    path = os.path.join(r'C:\Users\admin\.gemini\antigravity-cli\brain\d6bc982b-0100-4c6e-8abd-14870e722c80', f'{name}.png')
    subprocess.run('adb connect 127.0.0.1:16384', shell=True)
    p = subprocess.run(f'{ADB} exec-out screencap -p', shell=True, capture_output=True)
    with open(path, 'wb') as f:
        f.write(p.stdout)
    print(f'Saved {name}: {len(p.stdout)} bytes to {path}')
    return path

def get_albums():
    sh('shell uiautomator dump /sdcard/ui_albums.xml')
    sh('pull /sdcard/ui_albums.xml .ci/ui_albums.xml')
    if not os.path.exists('.ci/ui_albums.xml'): return []
    root = ET.parse('.ci/ui_albums.xml').getroot()
    albums = []
    for node in root.iter():
        t = node.attrib.get('text', '')
        if t and any(k in t for k in ['相册', 'Photos', '微信', 'Camera', '相机', '截图']):
            albums.append(t)
    return albums

def main():
    subprocess.run('adb connect 127.0.0.1:16384', shell=True)
    
    # 1. 验证相册顺序一致性
    print('Testing album order stability...')
    sh('shell am force-stop com.nasdrop')
    time.sleep(1)
    sh('shell am start -n com.nasdrop/.MainActivity')
    time.sleep(2)
    # 点击相册 Tab
    sh('shell input tap 530 1520')
    time.sleep(2)
    albums1 = get_albums()
    print('Run 1 albums:', albums1)
    screen('screen_v213_albums_run1')
    
    # 杀掉再开第二次
    sh('shell am force-stop com.nasdrop')
    time.sleep(1)
    sh('shell am start -n com.nasdrop/.MainActivity')
    time.sleep(2)
    sh('shell input tap 530 1520')
    time.sleep(2)
    albums2 = get_albums()
    print('Run 2 albums:', albums2)
    screen('screen_v213_albums_run2')
    
    print('Albums equal:', albums1 == albums2)
    
    # 2. 验证视频左右拖拽触底与触顶
    print('Testing video seek clamping...')
    # 进全部文件列表打开视频
    sh('shell input tap 588 1520')
    time.sleep(1.5)
    sh('shell uiautomator dump /sdcard/ui_files.xml')
    sh('pull /sdcard/ui_files.xml .ci/ui_files.xml')
    root = ET.parse('.ci/ui_files.xml').getroot()
    for node in root.iter():
        t = node.attrib.get('text', '')
        if '.mp4' in t:
            b = node.attrib.get('bounds')
            m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', b)
            cx, cy = (int(m.group(1))+int(m.group(3)))//2, (int(m.group(2))+int(m.group(4)))//2
            sh(f'shell input tap {cx} {cy}')
            time.sleep(2.5)
            break
            
    # 向左滑动超出片头（从 600 向左拖至 50）
    print('Swiping left to head...')
    sh('shell input swipe 600 600 50 600 400')
    time.sleep(0.2)
    screen('screen_v213_seek_clamped_head')
    time.sleep(1)
    
    # 向右滑动超出片尾（从 100 向右拖至 700）
    print('Swiping right to tail...')
    sh('shell input swipe 100 600 700 600 400')
    time.sleep(0.2)
    screen('screen_v213_seek_clamped_tail')
    time.sleep(1)
    
    print('All v213 tests finished!')

if __name__ == '__main__':
    main()
