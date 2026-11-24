import subprocess
import xml.etree.ElementTree as ET

ADB = r"C:\Users\admin\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"
DEVICE = "127.0.0.1:16384"

subprocess.run([ADB, "connect", DEVICE], capture_output=True)
wm_res = subprocess.run([ADB, "-s", DEVICE, "shell", "wm", "size"], capture_output=True, text=True)
print("Screen size:", wm_res.stdout)

subprocess.run([ADB, "-s", DEVICE, "shell", "uiautomator", "dump", "/sdcard/all_nodes.xml"])
res = subprocess.run([ADB, "-s", DEVICE, "shell", "cat", "/sdcard/all_nodes.xml"], capture_output=True, text=True)
root = ET.fromstring(res.stdout)
for node in root.iter('node'):
    text = node.attrib.get('text', '')
    desc = node.attrib.get('content-desc', '')
    bounds = node.attrib.get('bounds', '')
    cls = node.attrib.get('class', '')
    if any(k in text or k in desc for k in ['首页', '相册', '文件', '影音', '上传', '设置', '默认']):
        print(f"[{bounds}] Text: '{text}' | Desc: '{desc}' | Class: {cls}")
