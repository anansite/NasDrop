import urllib.request, json, subprocess, time, sys

proc = subprocess.Popen(['git', 'credential', 'fill'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
out, _ = proc.communicate('protocol=https\nhost=github.com\n\n')
token = ''
for line in out.splitlines():
    if line.startswith('password='):
        token = line.split('=', 1)[1]

# Get current HEAD commit sha
head_sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
print(f"Target commit: {head_sha[:7]}", flush=True)

run_id = None
for _ in range(15):
    req = urllib.request.Request('https://api.github.com/repos/anansite/NasDrop/actions/runs?per_page=5', headers={'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json'})
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode())
        for r in data['workflow_runs']:
            if r['head_sha'].startswith(head_sha[:7]):
                run_id = r['id']
                print(f"Found run {run_id} for commit {head_sha[:7]} (status: {r['status']})", flush=True)
                break
    if run_id:
        break
    time.sleep(3)

if not run_id:
    print("Could not find run for commit", head_sha[:7], flush=True)
    sys.exit(1)

for i in range(40):
    req = urllib.request.Request(f'https://api.github.com/repos/anansite/NasDrop/actions/runs/{run_id}', headers={'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json'})
    with urllib.request.urlopen(req) as resp:
        run = json.loads(resp.read().decode())
        print(f"Poll {i+1}: status={run['status']}, conclusion={run['conclusion']}", flush=True)
        if run['status'] == 'completed':
            if run['conclusion'] == 'success':
                print("SUCCESS!", flush=True)
                sys.exit(0)
            else:
                print("FAILED!", flush=True)
                sys.exit(2)
    time.sleep(10)
