import subprocess
import urllib.request
import json
import time
import os
import zipfile
import sys

def get_token():
    proc = subprocess.Popen(["git", "credential", "fill"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
    out, _ = proc.communicate("protocol=https\nhost=github.com\n\n")
    for line in out.splitlines():
        if line.startswith("password="):
            return line.split("=", 1)[1]
    return ""

def get_local_sha():
    res = subprocess.run(["git", "-C", r"C:\Users\admin\WorkBuddy\2026-11-16-15-32-06\nasdrop", "rev-parse", "HEAD"], capture_output=True, text=True)
    return res.stdout.strip()[:7]

class NoAuthRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        new_req = super().redirect_request(req, fp, code, msg, headers, newurl)
        if new_req:
            new_req.headers.pop('Authorization', None)
            new_req.headers.pop('authorization', None)
        return new_req

token = get_token()
repo = "anansite/NasDrop"
target_sha = get_local_sha()
headers = {
    "Authorization": f"Bearer {token}",
    "Accept": "application/vnd.github+json"
}

print(f"Target commit sha to wait for: {target_sha}", flush=True)
print("Monitoring GitHub Actions build...", flush=True)

run_id = None
while True:
    try:
        req = urllib.request.Request(f"https://api.github.com/repos/{repo}/actions/runs?per_page=5", headers=headers)
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode())
            runs = data.get("workflow_runs", [])
            matching_run = None
            for r in runs:
                if r["head_commit"]["id"].startswith(target_sha):
                    matching_run = r
                    break
            
            if matching_run:
                run_id = matching_run["id"]
                status = matching_run["status"]
                conclusion = matching_run["conclusion"]
                print(f"[{time.strftime('%H:%M:%S')}] Run {run_id} ({target_sha}): {status} / {conclusion}", flush=True)
                if status == "completed":
                    if conclusion != "success":
                        print(f"Build failed with conclusion: {conclusion}", flush=True)
                        sys.exit(1)
                    break
            else:
                print(f"[{time.strftime('%H:%M:%S')}] Waiting for workflow run for {target_sha} to be scheduled...", flush=True)
    except Exception as e:
        print("Error checking status:", e, flush=True)
    time.sleep(20)

print("Build succeeded! Fetching artifact...", flush=True)
art_req = urllib.request.Request(f"https://api.github.com/repos/{repo}/actions/runs/{run_id}/artifacts", headers=headers)
with urllib.request.urlopen(art_req) as resp:
    art_data = json.loads(resp.read().decode())
    artifacts = art_data.get("artifacts", [])
    if not artifacts:
        print("No artifacts found!", flush=True)
        sys.exit(1)
    art = artifacts[0]
    art_id = art["id"]
    print(f"Artifact {art['name']} (ID: {art_id}) found, downloading...", flush=True)

dl_url = f"https://api.github.com/repos/{repo}/actions/artifacts/{art_id}/zip"
opener = urllib.request.build_opener(NoAuthRedirect)
dl_req = urllib.request.Request(dl_url, headers=headers)
zip_path = os.path.abspath("out/latest_apk.zip")
os.makedirs("out", exist_ok=True)

with opener.open(dl_req) as resp, open(zip_path, "wb") as f:
    f.write(resp.read())
print(f"Downloaded zip ({os.path.getsize(zip_path)} bytes), extracting...", flush=True)

with zipfile.ZipFile(zip_path, 'r') as zip_ref:
    zip_ref.extractall("out")

target_apk_name = f"NasDrop-{target_sha}.apk"
latest_apk = os.path.join("out", target_apk_name)
if not os.path.exists(latest_apk):
    apk_files = [f for f in os.listdir("out") if f.endswith(".apk") and f.startswith("NasDrop")]
    apk_files.sort(key=lambda x: os.path.getmtime(os.path.join("out", x)), reverse=True)
    latest_apk = os.path.join("out", apk_files[0])

print(f"Target APK: {latest_apk} ({os.path.getsize(latest_apk)} bytes)", flush=True)

adb_path = r"C:\Program Files\Netease\MuMu\nx_main\adb.exe"
print("Connecting adb to MuMu (127.0.0.1:16384)...", flush=True)
subprocess.run([adb_path, "connect", "127.0.0.1:16384"], check=False)

devs_out = subprocess.run([adb_path, "devices"], capture_output=True, text=True).stdout
dev_ids = []
for line in devs_out.splitlines():
    parts = line.strip().split()
    if len(parts) >= 2 and parts[1] == "device":
        dev_ids.append(parts[0])

print(f"Found active devices: {dev_ids}", flush=True)
all_apks = sorted([os.path.join("out", f) for f in os.listdir("out") if f.endswith(".apk")], key=os.path.getmtime, reverse=True)

for dev in dev_ids:
    abi_res = subprocess.run([adb_path, "-s", dev, "shell", "getprop", "ro.product.cpu.abi"], capture_output=True, text=True)
    dev_abi = abi_res.stdout.strip()
    print(f"Device {dev} ABI: {dev_abi}", flush=True)

    matching_apk = None
    for apk in all_apks:
        if dev_abi and dev_abi in apk:
            matching_apk = apk
            break
    if not matching_apk:
        matching_apk = latest_apk

    print(f"Installing {matching_apk} to device {dev}...", flush=True)
    install_res = subprocess.run([adb_path, "-s", dev, "install", "-r", matching_apk], capture_output=True, text=True)
    print(f"Install on {dev}:", install_res.stdout, install_res.stderr, flush=True)
    subprocess.run([adb_path, "-s", dev, "shell", "am", "force-stop", "com.nasdrop"], check=False)
    subprocess.run([adb_path, "-s", dev, "shell", "am", "start", "-n", "com.nasdrop/.MainActivity"], check=False)

print("Done! NasDrop updated and launched on all devices.", flush=True)
