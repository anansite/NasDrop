import os, re, subprocess

def run_cmd(cmd, cwd=None):
    res = subprocess.run(cmd, shell=True, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    return res.stdout

print("=== 1. Checking git config & remotes for tokens ===")
for repo, path in [("nasdrop", "."), ("nasdrop-server", r"..\nasdrop-server")]:
    remotes = run_cmd("git remote -v", cwd=path)
    for line in remotes.splitlines():
        if "ghp_" in line or "github_pat_" in line or "@" in line:
            print(f"[{repo}] Remote URL contains auth credentials: {line}")

print("\n=== 2. Checking git commit history for tokens and secrets ===")
token_pattern = re.compile(r'(ghp_[a-zA-Z0-9]{36}|github_pat_[a-zA-Z0-9_]{50,}|AKIA[0-9A-Z]{16}|bearer\s+[a-zA-Z0-9_\-\.]{20,})', re.IGNORECASE)

for repo, path in [("nasdrop", "."), ("nasdrop-server", r"..\nasdrop-server")]:
    log_diff = run_cmd("git log -p -n 100", cwd=path)
    matches = token_pattern.findall(log_diff)
    if matches:
        print(f"[{repo}] FOUND TOKENS IN GIT HISTORY: {set(matches)}")
    else:
        print(f"[{repo}] No token matches in last 100 commits.")

print("\n=== 3. Checking Keystore & Signing Config in nasdrop ===")
for root, dirs, files in os.walk("app"):
    for f in files:
        if f.endswith((".gradle", ".kts", ".properties", ".xml")):
            fp = os.path.join(root, f)
            with open(fp, "r", encoding="utf-8", errors="ignore") as file:
                content = file.read()
                if "storePassword" in content or "keyPassword" in content or "keyAlias" in content:
                    print(f"[Signing Config] Found in {fp}:")
                    for l in content.splitlines():
                        if any(k in l for k in ["storePassword", "keyPassword", "keyAlias", "storeFile"]):
                            print("  ", l.strip())

print("\n=== 4. Checking nasdrop-server Configuration & Defaults ===")
cfg_path = r"..\nasdrop-server\internal\config\config.go"
if os.path.exists(cfg_path):
    with open(cfg_path, "r", encoding="utf-8") as f:
        print(f.read())

print("\n=== 5. Checking nasdrop-server API Authentication & CORS ===")
router_path = r"..\nasdrop-server\internal\api\router.go"
if os.path.exists(router_path):
    with open(router_path, "r", encoding="utf-8") as f:
        print(f.read()[:1500])

print("\n=== 6. Checking for Tracked Sensitive/Binary/Personal Files in nasdrop ===")
tracked = run_cmd("git ls-files", cwd=".").splitlines()
sensitive = [f for f in tracked if f.endswith(('.jks', '.keystore', '.xml', '.json', '.env', '.db')) and not f.startswith('app/src/main/res')]
print(f"Tracked sensitive/dump files in nasdrop ({len(sensitive)} files):")
for s in sensitive[:20]:
    print("  ", s)
if len(sensitive) > 20:
    print(f"   ... and {len(sensitive) - 20} more dump/json files.")
