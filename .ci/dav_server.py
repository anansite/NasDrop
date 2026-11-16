#!/usr/bin/env python3
"""极简 WebDAV 服务，仅用于本地/模拟器联调。

只实现 NasDrop 用到的方法：OPTIONS / PROPFIND / GET / HEAD / PUT / DELETE / MKCOL / MOVE。
行为尽量贴近用户的 hacdias/webdav：Basic 认证、根目录只读、/nas 子目录可写。
"""
import base64
import os
import posixpath
import shutil
import sys
import urllib.parse
from email.utils import formatdate
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = os.path.abspath(os.environ.get("DAV_ROOT", ".ci/davroot"))
USER, PASS = "admin", "admin"
WRITABLE = {"/nas"}


def local_path(url_path: str) -> str:
    p = urllib.parse.unquote(url_path.split("?", 1)[0])
    p = posixpath.normpath(p)
    if p == "/":
        return ROOT
    return os.path.join(ROOT, p.lstrip("/"))


def writable(url_path: str) -> bool:
    p = posixpath.normpath("/" + url_path.strip("/"))
    return any(p == w or p.startswith(w + "/") for w in WRITABLE)


def iso(ts: float) -> str:
    from datetime import datetime, timezone
    return datetime.fromtimestamp(ts, timezone.utc).strftime("%a, %d %b %Y %H:%M:%S GMT")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "TestDAV/1.0"

    def log_message(self, fmt, *args):
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    # ---------- auth ----------
    def authorized(self) -> bool:
        h = self.headers.get("Authorization", "")
        if h.startswith("Basic "):
            try:
                u, _, p = base64.b64decode(h[6:]).decode().partition(":")
                if u == USER and p == PASS:
                    return True
            except Exception:
                pass
        self.send_response(401)
        self.send_header("WWW-Authenticate", 'Basic realm="Restricted"')
        self.send_header("Content-Length", "0")
        self.end_headers()
        return False

    # ---------- helpers ----------
    def entry_xml(self, url_path: str, name: str) -> str:
        fs = local_path(url_path)
        st = os.stat(fs)
        is_dir = os.path.isdir(fs)
        q = urllib.parse.quote(url_path)
        return (
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            "<D:response><D:href>%s</D:href><D:propstat><D:prop>"
            "<D:displayname>%s</D:displayname>"
            "<D:getcontentlength>%d</D:getcontentlength>"
            "<D:getlastmodified>%s</D:getlastmodified>"
            "<D:getetag>\"%s\"</D:getetag>"
            "<D:resourcetype>%s</D:resourcetype>"
            "</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>"
            % (
                q + ("/" if is_dir and not q.endswith("/") else ""),
                name,
                0 if is_dir else st.st_size,
                iso(st.st_mtime),
                "%x-%x" % (int(st.st_mtime), st.st_size),
                "<D:collection/>" if is_dir else "",
            )
        )

    # ---------- methods ----------
    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("DAV", "1, 2")
        self.send_header("Allow", "OPTIONS, GET, HEAD, PUT, DELETE, PROPFIND, MKCOL, MOVE, COPY")
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_PROPFIND(self):
        if not self.authorized():
            return
        p = urllib.parse.unquote(self.path)
        fs = local_path(p)
        if not os.path.exists(fs):
            self.send_error(404)
            return
        depth = self.headers.get("Depth", "1")
        parts = [self.entry_xml(p.rstrip("/") or "/", os.path.basename(fs.rstrip("/")) or "/")]
        if depth != "0" and os.path.isdir(fs):
            for n in sorted(os.listdir(fs)):
                child = (p.rstrip("/") or "") + "/" + n
                parts.append(self.entry_xml(child, n))
        body = (
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            "<D:multistatus xmlns:D=\"DAV:\">%s</D:multistatus>" % "".join(parts)
        ).encode()
        self.send_response(207)
        self.send_header("Content-Type", "application/xml; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if not self.authorized():
            return
        fs = local_path(self.path)
        if not os.path.isfile(fs):
            self.send_error(404)
            return
        size = os.path.getsize(fs)
        rng = self.headers.get("Range")
        start, end = 0, size - 1
        code = 200
        if rng and rng.startswith("bytes="):
            a, _, b = rng[6:].partition("-")
            try:
                start = int(a) if a else 0
                end = int(b) if b else size - 1
            except ValueError:
                start, end = 0, size - 1
            if start < size:
                code = 206
        length = max(0, end - start + 1)
        self.send_response(code)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(length))
        if code == 206:
            self.send_header("Content-Range", "bytes %d-%d/%d" % (start, end, size))
        self.end_headers()
        with open(fs, "rb") as f:
            f.seek(start)
            self.wfile.write(f.read(length))

    def do_HEAD(self):
        if not self.authorized():
            return
        fs = local_path(self.path)
        if not os.path.exists(fs):
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(0 if os.path.isdir(fs) else os.path.getsize(fs)))
        self.end_headers()

    def do_PUT(self):
        if not self.authorized():
            return
        if not writable(self.path):
            self.send_error(403)
            return
        fs = local_path(self.path)
        os.makedirs(os.path.dirname(fs), exist_ok=True)
        n = int(self.headers.get("Content-Length", 0))
        exists = os.path.exists(fs)
        with open(fs, "wb") as f:
            left = n
            while left > 0:
                chunk = self.rfile.read(min(65536, left))
                if not chunk:
                    break
                f.write(chunk)
                left -= len(chunk)
        self.send_response(201 if not exists else 204)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_MKCOL(self):
        if not self.authorized():
            return
        if not writable(self.path):
            self.send_error(403)
            return
        fs = local_path(self.path)
        if os.path.exists(fs):
            self.send_error(405)
            return
        os.makedirs(fs, exist_ok=True)
        self.send_response(201)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_DELETE(self):
        if not self.authorized():
            return
        if not writable(self.path):
            self.send_error(403)
            return
        fs = local_path(self.path)
        if not os.path.exists(fs):
            self.send_error(404)
            return
        shutil.rmtree(fs) if os.path.isdir(fs) else os.remove(fs)
        self.send_response(204)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_MOVE(self):
        if not self.authorized():
            return
        if not writable(self.path):
            self.send_error(403)
            return
        dst = self.headers.get("Destination", "")
        dpath = urllib.parse.urlparse(dst).path
        src, tar = local_path(self.path), local_path(dpath)
        if not os.path.exists(src):
            self.send_error(404)
            return
        os.makedirs(os.path.dirname(tar), exist_ok=True)
        shutil.move(src, tar)
        self.send_response(201)
        self.send_header("Content-Length", "0")
        self.end_headers()


if __name__ == "__main__":
    os.makedirs(os.path.join(ROOT, "nas", "movies"), exist_ok=True)
    os.makedirs(os.path.join(ROOT, "nas", "Photos"), exist_ok=True)
    with open(os.path.join(ROOT, "nas", "movies", "readme.txt"), "wb") as f:
        f.write(b"hello from testdav\n")
    print("dav root =", ROOT, "listening 8088", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8088), Handler).serve_forever()
