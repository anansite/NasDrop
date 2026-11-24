import subprocess
import xml.etree.ElementTree as ET
import time

ADB = r"C:\Users\admin\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"
DEVICE = "127.0.0.1:16384"

subprocess.run([ADB, "connect", DEVICE], capture_output=True)
# Launch ACTION_OPEN_DOCUMENT with quoted '*/*'
res = subprocess.run([ADB, "-s", DEVICE, "shell", "am", "start", "-a", "android.intent.action.OPEN_DOCUMENT", "-c", "android.intent.category.OPENABLE", "-t", "'*/*'"], capture_output=True, text=True)
print("Start result:", res.stdout, res.stderr)
time.sleep(2)
subprocess.run([ADB, "-s", DEVICE, "shell", "uiautomator", "dump", "/sdcard/open_doc_dump.xml"])
res2 = subprocess.run([ADB, "-s", DEVICE, "shell", "cat", "/sdcard/open_doc_dump.xml"], capture_output=True, text=True)
root = ET.fromstring(res2.stdout)
for node in root.iter('node'):
    text = node.attrib.get('text', '')
    desc = node.attrib.get('content-desc', '')
    bounds = node.attrib.get('bounds', '')
    if text or desc:
        print(f"[{bounds}] Text: '{text}' | Desc: '{desc}'")
