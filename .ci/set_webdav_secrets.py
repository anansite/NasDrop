import base64, json, subprocess, urllib.request
from nacl import encoding, public

def get_token():
    proc = subprocess.Popen(['git', 'credential', 'fill'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
    out, _ = proc.communicate('protocol=https\nhost=github.com\n\n')
    for line in out.splitlines():
        if line.startswith('password='):
            return line.split('=', 1)[1]
    raise ValueError("Could not find GitHub token")

token = get_token()

def set_repo_secret(repo: str, name: str, value: str):
    # 1. Get Repo Public Key
    req = urllib.request.Request(f'https://api.github.com/repos/{repo}/actions/secrets/public-key', headers={
        'Authorization': f'Bearer {token}',
        'Accept': 'application/vnd.github+json'
    })
    with urllib.request.urlopen(req) as resp:
        pk_info = json.loads(resp.read().decode())
    key_id = pk_info['key_id']
    pub_key_b64 = pk_info['key']

    # 2. Encrypt Secret
    pk = public.PublicKey(pub_key_b64.encode('utf-8'), encoding.Base64Encoder())
    box = public.SealedBox(pk)
    enc_val = base64.b64encode(box.encrypt(value.encode('utf-8'))).decode('utf-8')

    # 3. PUT Secret
    body = json.dumps({
        "encrypted_value": enc_val,
        "key_id": key_id
    }).encode('utf-8')
    put_req = urllib.request.Request(
        f'https://api.github.com/repos/{repo}/actions/secrets/{name}',
        data=body,
        headers={
            'Authorization': f'Bearer {token}',
            'Accept': 'application/vnd.github+json',
            'Content-Type': 'application/json'
        },
        method='PUT'
    )
    with urllib.request.urlopen(put_req) as resp:
        print(f"[{repo}] Secret '{name}' set successfully! Status: {resp.status}")

secrets_to_set = {
    "DEFAULT_WEBDAV_URL": "http://nas.205002.xyz/",
    "DEFAULT_WEBDAV_USER": "admin",
    "DEFAULT_WEBDAV_PASS": "admin",
}

for k, v in secrets_to_set.items():
    set_repo_secret("anansite/NasDrop", k, v)

for k, v in [("NASDROP_USER", "admin"), ("NASDROP_PASS", "admin")]:
    set_repo_secret("anansite/nasdrop-server", k, v)

print("All WebDAV default security secrets successfully written to GitHub Secrets!")
