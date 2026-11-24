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
repo = "anansite/NasDrop"

# 1. Get Repo Public Key
req = urllib.request.Request(f'https://api.github.com/repos/{repo}/actions/secrets/public-key', headers={
    'Authorization': f'Bearer {token}',
    'Accept': 'application/vnd.github+json'
})
with urllib.request.urlopen(req) as resp:
    pk_info = json.loads(resp.read().decode())
key_id = pk_info['key_id']
pub_key = pk_info['key']
print(f"Retrieved repo public key: key_id={key_id}")

def encrypt_secret(pubkey_b64: str, secret_val: str) -> str:
    pk = public.PublicKey(pubkey_b64.encode('utf-8'), encoding.Base64Encoder())
    box = public.SealedBox(pk)
    return base64.b64encode(box.encrypt(secret_val.encode('utf-8'))).decode('utf-8')

def set_secret(name: str, value: str):
    enc_val = encrypt_secret(pub_key, value)
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
        print(f"Secret '{name}' set successfully! Status: {resp.status}")

# Read keystore and encode
with open("signing/nasdrop.jks", "rb") as f:
    keystore_bytes = f.read()
keystore_b64 = base64.b64encode(keystore_bytes).decode('utf-8')

print(f"Keystore file size: {len(keystore_bytes)} bytes, base64 length: {len(keystore_b64)}")

set_secret("RELEASE_KEYSTORE_BASE64", keystore_b64)
set_secret("RELEASE_KEYSTORE_PASSWORD", "nasdrop123")
set_secret("RELEASE_KEY_ALIAS", "nasdrop")
set_secret("RELEASE_KEY_PASSWORD", "nasdrop123")
print("All 4 keystore secrets successfully created in GitHub Actions Secrets!")
