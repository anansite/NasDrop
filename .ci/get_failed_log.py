import subprocess, urllib.request, json

def get_token():
    proc = subprocess.Popen(['git', 'credential', 'fill'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
    out, _ = proc.communicate('protocol=https\nhost=github.com\n\n')
    for line in out.splitlines():
        if line.startswith('password='):
            return line.split('=', 1)[1]
    return ''

class NoAuthRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        new_req = super().redirect_request(req, fp, code, msg, headers, newurl)
        if new_req:
            new_req.headers.pop('Authorization', None)
            new_req.headers.pop('authorization', None)
        return new_req

token = get_token()
repo = 'anansite/private_filemanager'
run_id = 43895053695
headers = {'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json'}

req = urllib.request.Request(f'https://api.github.com/repos/{repo}/actions/runs/{run_id}/jobs', headers=headers)
with urllib.request.urlopen(req) as resp:
    jobs = json.loads(resp.read().decode())['jobs']

for job in jobs:
    print(f"Job: {job['name']} - {job['conclusion']}")
    job_id = job['id']
    log_url = f'https://api.github.com/repos/{repo}/actions/jobs/{job_id}/logs'
    opener = urllib.request.build_opener(NoAuthRedirect)
    log_req = urllib.request.Request(log_url, headers=headers)
    try:
        with opener.open(log_req) as lresp:
            log_text = lresp.read().decode('utf-8', errors='replace')
            lines = log_text.splitlines()
            for i, line in enumerate(lines):
                if 'e: ' in line or 'error' in line.lower() or 'failure' in line.lower():
                    # print context
                    for j in range(max(0, i-2), min(len(lines), i+3)):
                        print(lines[j])
                    print('-' * 40)
    except Exception as e:
        print('Error fetching log:', e)
