import urllib.request, json, subprocess

proc = subprocess.Popen(['git', 'credential', 'fill'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
out, _ = proc.communicate('protocol=https\nhost=github.com\n\n')
token = ''
for line in out.splitlines():
    if line.startswith('password='):
        token = line.split('=', 1)[1]

req = urllib.request.Request('https://api.github.com/repos/anansite/nasdrop-server/actions/runs?per_page=5', headers={'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json'})
with urllib.request.urlopen(req) as resp:
    data = json.loads(resp.read().decode())
    for r in data['workflow_runs']:
        sha = r['head_sha'][:7]
        rid = r['id']
        st = r['status']
        conc = str(r['conclusion'])
        title = r['display_title'][:60]
        print(f"{sha} | {rid} | {st:<10} | {conc:<10} | {title}")
