import urllib.request, json, subprocess, time, sys

proc = subprocess.Popen(['git', 'credential', 'fill'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
out, _ = proc.communicate('protocol=https\nhost=github.com\n\n')
token = ''
for line in out.splitlines():
    if line.startswith('password='):
        token = line.split('=', 1)[1]

req = urllib.request.Request('https://api.github.com/repos/anansite/NasDrop/actions/runs?per_page=3', headers={'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json'})
with urllib.request.urlopen(req) as resp:
    data = json.loads(resp.read().decode())
    for r in data['workflow_runs']:
        print(f"ID: {r['id']}, commit: {r['head_sha'][:7]}, status: {r['status']}, conclusion: {r['conclusion']}, name: {r['name']}", flush=True)

latest_run = data['workflow_runs'][0]
run_id = latest_run['id']
print(f"Tracking run {run_id} for commit {latest_run['head_sha'][:7]}", flush=True)

for i in range(40):
    req = urllib.request.Request(f'https://api.github.com/repos/anansite/NasDrop/actions/runs/{run_id}', headers={'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json'})
    with urllib.request.urlopen(req) as resp:
        run = json.loads(resp.read().decode())
        print(f"Poll {i+1}: status={run['status']}, conclusion={run['conclusion']}", flush=True)
        if run['status'] == 'completed':
            if run['conclusion'] == 'success':
                print("SUCCESS!", flush=True)
            else:
                print("FAILED!", flush=True)
            break
    time.sleep(10)
