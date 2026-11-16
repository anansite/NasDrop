import subprocess, urllib.request, json

proc = subprocess.Popen(["git", "credential", "fill"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
out, _ = proc.communicate("protocol=https\nhost=github.com\n\n")
token = ""
for line in out.splitlines():
    if line.startswith("password="):
        token = line.split("=", 1)[1]

req = urllib.request.Request("https://api.github.com/repos/anansite/private_filemanager/actions/runs?per_page=1", headers={
    "Authorization": f"Bearer {token}",
    "Accept": "application/vnd.github+json"
})
with urllib.request.urlopen(req) as resp:
    data = json.loads(resp.read().decode())
    r = data["workflow_runs"][0]
    print(r["id"], r["status"], r["conclusion"], r["head_commit"]["id"][:7], r["head_commit"]["message"].splitlines()[0])
