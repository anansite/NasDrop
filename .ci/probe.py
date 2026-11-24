import urllib.request, ssl, base64

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

xml = '<?xml version="1.0" encoding="utf-8" ?><D:propfind xmlns:D="DAV:"><D:prop><D:quota-available-bytes/><D:quota-used-bytes/><D:resourcetype/></D:prop></D:propfind>'
auth = base64.b64encode(b'admin:123456').decode()
for p in ['/', '/dav/']:
    try:
        req = urllib.request.Request('https://nas.205002.xyz' + p, data=xml.encode(), method='PROPFIND', headers={
            'Authorization': 'Basic ' + auth,
            'Content-Type': 'application/xml',
            'Depth': '0'
        })
        with urllib.request.urlopen(req, context=ctx, timeout=3) as r:
            print(p, r.status, r.read().decode('utf-8', errors='ignore'))
    except Exception as e:
        print(p, 'ERR', e)
