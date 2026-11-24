import subprocess
import time
import os
import zipfile
import json
import urllib.request
import shutil
import xml.etree.ElementTree as ET

ADB = r"C:\Users\admin\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"
DEVICE = "127.0.0.1:16384"
ARTIFACTS_DIR = r"C:\Users\admin\.gemini\antigravity-cli\brain\d6bc982b-0100-4c6e-8abd-14870e722c80"

# Fetch token from git credentials
def get_gh_token():
    proc = subprocess.run(["git", "credential", "fill"], input="protocol=https\nhost=github.com\n", capture_output=True, text=True)
    for line in proc.stdout.splitlines():
        if line.startswith("password="):
            return line.split("=", 1)[1].strip()
    return os.environ.get("GITHUB_TOKEN", "")

def adb(*args):
    cmd = [ADB, "-s", DEVICE] + list(args)
    return subprocess.run(cmd, capture_output=True, text=True)

def ensure_connected():
    subprocess.run([ADB, "connect", DEVICE], capture_output=True)
    time.sleep(1)

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

def tap(x, y):
    adb("shell", "input", "tap", str(x), str(y))

def wait_for_run_and_download(token):
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "NasDrop-CI"
    }
    target_commit = "30f7698"
    print(f"Waiting for CI run on commit {target_commit}...", flush=True)
    
    run_id = None
    for _ in range(60):
        req = urllib.request.Request("https://api.github.com/repos/anansite/NasDrop/actions/runs?per_page=10", headers=headers)
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        for run in data.get("workflow_runs", []):
            if run.get("head_commit", {}).get("id", "").startswith(target_commit):
                run_id = run["id"]
                status = run["status"]
                conclusion = run.get("conclusion")
                print(f"Found run {run_id}: status={status}, conclusion={conclusion}", flush=True)
                if status == "completed":
                    if conclusion != "success":
                        raise RuntimeError(f"Run finished with conclusion {conclusion}")
                    break
        if run_id and status == "completed":
            break
        time.sleep(10)

    if not run_id:
        raise RuntimeError("CI run not found within timeout")

    # Download artifact
    print(f"Fetching artifacts for run {run_id}...", flush=True)
    req = urllib.request.Request(f"https://api.github.com/repos/anansite/NasDrop/actions/runs/{run_id}/artifacts", headers=headers)
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    
    artifact = None
    for art in data.get("artifacts", []):
        if art["name"] in ["apks", "app-release", "apk"]:
            artifact = art
            break
    if not artifact and data.get("artifacts"):
        artifact = data["artifacts"][0]
        
    print(f"Downloading artifact {artifact['name']} ({artifact['size_in_bytes']} bytes)...", flush=True)
    download_url = artifact["archive_download_url"]
    dl_req = urllib.request.Request(download_url, headers=headers)
    with urllib.request.urlopen(dl_req) as resp, open("artifact_v216.zip", "wb") as f:
        shutil.copyfileobj(resp, f)

    with zipfile.ZipFile("artifact_v216.zip", "r") as z:
        z.extractall("extracted_v216")
    
    apk_file = None
    for root, _, files in os.walk("extracted_v216"):
        for f in files:
            if f.endswith("x86_64.apk") or ("universal" in f and f.endswith(".apk")) or f.endswith(".apk"):
                apk_file = os.path.join(root, f)
                if "x86_64" in f:
                    break
        if apk_file and "x86_64" in apk_file:
            break
            
    print(f"Selected APK: {apk_file}", flush=True)
    return apk_file

def main():
    token = get_gh_token()
    apk = wait_for_run_and_download(token)
    
    ensure_connected()
    print("Installing APK...", flush=True)
    res = subprocess.run([ADB, "-s", DEVICE, "install", "-r", "-d", apk], capture_output=True, text=True)
    print("Install output:", res.stdout, res.stderr, flush=True)

    print("Launching app...", flush=True)
    adb("shell", "am", "start", "-n", "com.nasdrop/.MainActivity")
    time.sleep(3)

    nodes = dump_ui()
    print("UI after launch:")
    for t, b, _ in nodes:
        print(f"  {t} -> {b}", flush=True)
    cap("screen_v216_launch.png")

if __name__ == "__main__":
    main()
