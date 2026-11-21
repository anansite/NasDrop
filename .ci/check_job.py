import urllib.request, json, subprocess

proc = subprocess.Popen(['git', 'credential', 'fill'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
out, _ = proc.communicate('protocol=https\nhost=github.com\n\n')
token = ''
for line in out.splitlines():
    if line.startswith('password='):
        token = line.split('=', 1)[1]

class NoAuthRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        new_req = super().redirect_request(req, fp, code, msg, headers, newurl)
        if new_req and 'Authorization' in new_req.headers:
            del new_req.headers['Authorization']
        return new_req

opener = urllib.request.build_opener(NoAuthRedirect)

req = urllib.request.Request('https://api.github.com/repos/anansite/NasDrop/actions/runs/44878658979/jobs', headers={'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json'})
with opener.open(req) as resp:
    jobs = json.loads(resp.read().decode())['jobs']
    for j in jobs:
        print(f"Job {j['id']}: {j['name']}, conclusion={j['conclusion']}")
        for s in j.get('steps', []):
            if s.get('conclusion') == 'failure':
                print(f"  Step failed: {s['name']}")
                
        log_req = urllib.request.Request(f"https://api.github.com/repos/anansite/NasDrop/actions/jobs/{j['id']}/logs", headers={'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json'})
        try:
            with opener.open(log_req) as log_resp:
                log_data = log_resp.read().decode('utf-8', errors='ignore')
                err_lines = [line for line in log_data.splitlines() if 'e: ' in line or 'FAILED' in line or 'error:' in line or 'FAILURE' in line or 'Unresolved' in line]
                print(f"Found {len(err_lines)} error lines:")
                for l in err_lines[-30:]:
                    print(l)
        except Exception as e:
            print("Could not fetch log:", e)
