import subprocess

ADB = r'C:\Users\admin\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe'
dev = '127.0.0.1:16384'

# Dump current window
res = subprocess.run([ADB, '-s', dev, 'shell', 'dumpsys', 'window'], capture_output=True, text=True)
for line in res.stdout.splitlines():
    if 'mCurrentFocus' in line or 'mFocusedApp' in line:
        print(line.strip())
