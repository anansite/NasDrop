import urllib.request, json, subprocess, zipfile, os

proc = subprocess.Popen(['git', 'credential', 'fill'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
out, _ = proc.communicate('protocol=https\nhost=github.com\n\n')
token = ''
for line in out.splitlines():
    if line.startswith('password='):
        token = line.split('=', 1)[1]

repo = "anansite/NasDrop"
run_id = 45365491430
headers = {
    "Authorization": f"Bearer {token}",
    "Accept": "application/vnd.github+json",
    "User-Agent": "NasDrop-Installer"
}

class NoAuthRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        new_req = super().redirect_request(req, fp, code, msg, headers, newurl)
        if new_req and 'Authorization' in new_req.headers:
            del new_req.headers['Authorization']
        return new_req

art_req = urllib.request.Request(f"https://api.github.com/repos/{repo}/actions/runs/{run_id}/artifacts", headers=headers)
with urllib.request.urlopen(art_req) as resp:
    art_data = json.loads(resp.read().decode())
    art = art_data["artifacts"][0]
    art_id = art["id"]
    print(f"Artifact {art['name']} (ID: {art_id}) found, downloading...")

dl_url = f"https://api.github.com/repos/{repo}/actions/artifacts/{art_id}/zip"
opener = urllib.request.build_opener(NoAuthRedirect)
dl_req = urllib.request.Request(dl_url, headers=headers)
zip_path = os.path.abspath("out/run_204.zip")

with opener.open(dl_req) as resp, open(zip_path, "wb") as f:
    f.write(resp.read())
print(f"Downloaded zip: {os.path.getsize(zip_path)} bytes")

with zipfile.ZipFile(zip_path, 'r') as zip_ref:
    for member in zip_ref.namelist():
        print("Zip member:", member)
    zip_ref.extractall("out")

apk_path = os.path.abspath("out/NasDrop-v1.0.204-x86_64.apk")
print(f"Installing {apk_path} ({os.path.getsize(apk_path)} bytes)...")

adb_path = r"C:\Program Files\Netease\MuMu\nx_main\adb.exe"
subprocess.run([adb_path, "connect", "127.0.0.1:16384"])
res = subprocess.run([adb_path, "-s", "127.0.0.1:16384", "install", "-r", "-d", apk_path], capture_output=True, text=True)
print("Install result:", res.stdout, res.stderr)

subprocess.run([adb_path, "-s", "127.0.0.1:16384", "shell", "am", "start", "-n", "com.nasdrop/.MainActivity"])
print("Launched MainActivity!")
