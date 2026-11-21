import urllib.request, json, subprocess

proc = subprocess.Popen(['git', 'credential', 'fill'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
out, _ = proc.communicate('protocol=https\nhost=github.com\n\n')
token = ''
for line in out.splitlines():
    if line.startswith('password='):
        token = line.split('=', 1)[1]

req = urllib.request.Request('https://api.github.com/repos/anansite/NasDrop/releases/latest', headers={'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json'})
with urllib.request.urlopen(req) as resp:
    rel = json.loads(resp.read().decode())
    print('Latest release tag:', rel['tag_name'])
    for a in rel['assets']:
        print(f"  {a['name']} ({a['size']} bytes) -> {a['browser_download_url']}")
