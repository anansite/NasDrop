import subprocess
import time
import os
import shutil
import urllib.request
import json
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

def get_token():
    proc = subprocess.run(["git", "credential", "fill"], input="protocol=https\nhost=github.com\n", capture_output=True, text=True)
    for line in proc.stdout.splitlines():
        if line.startswith("password="):
            return line.split("=", 1)[1].strip()
    return ""

def wait_and_install_v217():
    token = get_token()
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github.v3+json", "User-Agent": "NasDrop-CI"}
    run_id = 45481146022
    print(f"Waiting for CI run {run_id}...", flush=True)

    for _ in range(60):
        req = urllib.request.Request(f"https://api.github.com/repos/anansite/NasDrop/actions/runs/{run_id}", headers=headers)
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        status = data["status"]
        conclusion = data.get("conclusion")
        print(f"Status: {status}, conclusion: {conclusion}", flush=True)
        if status == "completed":
            break
        time.sleep(10)

    # Download from latest release v1.0.217
    time.sleep(5)
    url = "https://github.com/anansite/NasDrop/releases/download/v1.0.217/NasDrop-v1.0.217-x86_64.apk"
    print(f"Downloading from {url}...", flush=True)
    urllib.request.urlretrieve(url, "NasDrop-v1.0.217-x86_64.apk")
    print("Downloaded APK v217 successfully!", flush=True)

    ensure_connected()
    print("Installing APK v217 on top of existing app...", flush=True)
    r = subprocess.run([ADB, "-s", DEVICE, "install", "-r", "-d", "NasDrop-v1.0.217-x86_64.apk"], capture_output=True, text=True)
    print(r.stdout, r.stderr, flush=True)

    print("Launching MainActivity...", flush=True)
    adb("shell", "am", "start", "-n", "com.nasdrop/.MainActivity")
    time.sleep(4)

    adb("shell", "uiautomator", "dump", "/sdcard/dump.xml")
    adb("pull", "/sdcard/dump.xml", "dump.xml")
    tree = ET.parse("dump.xml")
    print("UI after upgrade launch:", flush=True)
    for node in tree.getroot().iter("node"):
        text = node.attrib.get("text") or node.attrib.get("content-desc")
        bounds = node.attrib.get("bounds")
        if text:
            print(f"  {text} -> {bounds}", flush=True)

    adb("shell", "screencap", "-p", "/sdcard/screen_v217_upgrade_home.png")
    adb("pull", "/sdcard/screen_v217_upgrade_home.png", "screen_v217_upgrade_home.png")
    shutil.copyfile("screen_v217_upgrade_home.png", os.path.join(ARTIFACTS_DIR, "screen_v217_upgrade_home.png"))

    # Tap settings tab to verify fields
    adb("shell", "input", "tap", "759", "1520")
    time.sleep(2)
    adb("shell", "uiautomator", "dump", "/sdcard/dump.xml")
    adb("pull", "/sdcard/dump.xml", "dump.xml")
    tree = ET.parse("dump.xml")
    print("\nSettings tab after upgrade:", flush=True)
    for node in tree.getroot().iter("node"):
        text = node.attrib.get("text") or node.attrib.get("content-desc")
        bounds = node.attrib.get("bounds")
        if text and any(k in text for k in ["WebDAV", "http", "admin", "账号", "密码"]):
            print(f"  {text} -> {bounds}", flush=True)

    adb("shell", "screencap", "-p", "/sdcard/screen_v217_upgrade_settings.png")
    adb("pull", "/sdcard/screen_v217_upgrade_settings.png", "screen_v217_upgrade_settings.png")
    shutil.copyfile("screen_v217_upgrade_settings.png", os.path.join(ARTIFACTS_DIR, "screen_v217_upgrade_settings.png"))

if __name__ == "__main__":
    wait_and_install_v217()
