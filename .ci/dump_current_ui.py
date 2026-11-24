import subprocess
import xml.etree.ElementTree as ET

ADB = r"C:\Program Files\Netease\MuMu\nx_main\adb.exe"
DEV = "127.0.0.1:16384"
subprocess.run([ADB, "connect", DEV], check=False)
subprocess.run([ADB, "-s", DEV, "shell", "uiautomator", "dump", "/sdcard/current_dump.xml"], check=False)
xml_content = subprocess.run([ADB, "-s", DEV, "shell", "cat", "/sdcard/current_dump.xml"], capture_output=True, text=True).stdout

try:
    root = ET.fromstring(xml_content)
    for elem in root.iter():
        txt = elem.attrib.get('text', '')
        desc = elem.attrib.get('content-desc', '')
        bounds = elem.attrib.get('bounds', '')
        if txt or desc:
            print(f'{bounds}: text="{txt}" desc="{desc}"')
except Exception as e:
    print('Error:', e)
