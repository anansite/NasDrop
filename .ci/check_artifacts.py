import subprocess
import urllib.request
import json

def get_token():
    proc = subprocess.run(["git", "credential", "fill"], input="protocol=https\nhost=github.com\n", capture_output=True, text=True)
    for line in proc.stdout.splitlines():
        if line.startswith("password="):
            return line.split("=", 1)[1].strip()
    return ""

token = get_token()
headers = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github.v3+json", "User-Agent": "NasDrop-CI"}
run_id = 45486162127

# List artifacts
req = urllib.request.Request(f"https://api.github.com/repos/anansite/NasDrop/actions/runs/{run_id}/artifacts", headers=headers)
with urllib.request.urlopen(req) as resp:
    data = json.loads(resp.read().decode("utf-8"))
print("Artifacts count:", data.get("total_count"))
for art in data.get("artifacts", []):
    print("Artifact:", art["name"], art["size_in_bytes"], art["archive_download_url"])

# List releases
req2 = urllib.request.Request("https://api.github.com/repos/anansite/NasDrop/releases?per_page=3", headers=headers)
with urllib.request.urlopen(req2) as resp:
    rels = json.loads(resp.read().decode("utf-8"))
for rel in rels:
    print("Release:", rel.get("tag_name"), rel.get("name"))
    for asset in rel.get("assets", []):
        print("  Asset:", asset.get("name"), asset.get("browser_download_url"))
