import subprocess
import xml.etree.ElementTree as ET

ADB = r"C:\Users\admin\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"
DEVICE = "127.0.0.1:16384"

subprocess.run([ADB, "connect", DEVICE], capture_output=True)
subprocess.run([ADB, "-s", DEVICE, "shell", "uiautomator", "dump", "/sdcard/nasdrop_live.xml"])
res = subprocess.run([ADB, "-s", DEVICE, "shell", "cat", "/sdcard/nasdrop_live.xml"], capture_output=True, text=True)
root = ET.fromstring(res.stdout)
for node in root.iter('node'):
    text = node.attrib.get('text', '')
    desc = node.attrib.get('content-desc', '')
    bounds = node.attrib.get('bounds', '')
    if text or desc:
        print(f"[{bounds}] text=\"{text}\" desc=\"{desc}\"")
