import subprocess

ADB = r"C:\Program Files\Netease\MuMu\nx_main\adb.exe"
DEV = "127.0.0.1:16384"
subprocess.run([ADB, "connect", DEV], check=False)
res = subprocess.run([ADB, "-s", DEV, "shell", "logcat", "-d"], capture_output=True, text=True, errors='ignore')
lines = [l for l in res.stdout.splitlines() if any(k in l for k in ["Repository", "BrowserViewModel", "WebDav", "直连", "Tailscale", "nasdrop"])]
for l in lines[-35:]:
    print(l)
