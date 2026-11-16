import subprocess
import urllib.request
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

token = get_token()
art_id = "14216040607"
repo = "anansite/private_filemanager"
dl_url = f"https://api.github.com/repos/{repo}/actions/artifacts/{art_id}/zip"

class NoAuthRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        new_req = super().redirect_request(req, fp, code, msg, headers, newurl)
        if new_req:
            new_req.headers.pop('Authorization', None)
            new_req.headers.pop('authorization', None)
        return new_req

opener = urllib.request.build_opener(NoAuthRedirect)
req = urllib.request.Request(dl_url, headers={
    "Authorization": f"Bearer {token}",
    "Accept": "application/vnd.github+json"
})

zip_path = os.path.abspath("out/latest_apk.zip")
os.makedirs("out", exist_ok=True)
print("Downloading artifact...", flush=True)

with opener.open(req) as resp, open(zip_path, "wb") as f:
    f.write(resp.read())

print(f"Downloaded zip: {os.path.getsize(zip_path)} bytes", flush=True)

with zipfile.ZipFile(zip_path, 'r') as zip_ref:
    zip_ref.extractall("out")

apk_files = [f for f in os.listdir("out") if f.endswith(".apk") and f.startswith("NasDrop")]
apk_files.sort(key=lambda x: os.path.getmtime(os.path.join("out", x)), reverse=True)
latest_apk = os.path.join("out", apk_files[0])
print(f"Extracted latest APK: {latest_apk} ({os.path.getsize(latest_apk)} bytes)", flush=True)

adb_path = r"C:\Program Files\Netease\MuMu\nx_main\adb.exe"
print("Connecting adb...", flush=True)
subprocess.run([adb_path, "connect", "127.0.0.1:16384"], check=False)

print(f"Installing {latest_apk} to emulator...", flush=True)
res = subprocess.run([adb_path, "-s", "127.0.0.1:16384", "install", "-r", latest_apk], capture_output=True, text=True)
print("Install output:", res.stdout, res.stderr, flush=True)

print("Starting NasDrop...", flush=True)
subprocess.run([adb_path, "-s", "127.0.0.1:16384", "shell", "am", "force-stop", "com.nasdrop"], check=False)
subprocess.run([adb_path, "-s", "127.0.0.1:16384", "shell", "am", "start", "-n", "com.nasdrop/.MainActivity"], check=False)
print("Done!", flush=True)
