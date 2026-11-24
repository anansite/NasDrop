import urllib.request, json, subprocess

proc = subprocess.Popen(['git', 'credential', 'fill'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
out, _ = proc.communicate('protocol=https\nhost=github.com\n\n')
token = ''
for line in out.splitlines():
    if line.startswith('password='):
        token = line.split('=', 1)[1]

req = urllib.request.Request('https://api.github.com/repos/anansite/NasDrop/actions/runs?per_page=20', headers={'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json'})
with urllib.request.urlopen(req) as resp:
    data = json.loads(resp.read().decode())
    for r in data['workflow_runs']:
        sha = r['head_sha'][:7]
        st = r['status']
        conc = str(r['conclusion'])
        title = r['display_title'][:60]
        run_id = r['id']
        print(f"{sha} | {run_id} | {st:<10} | {conc:<10} | {title}")
