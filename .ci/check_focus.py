import subprocess
ADB = r'C:\Users\admin\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe'
res = subprocess.run([ADB, '-s', '127.0.0.1:16384', 'shell', 'dumpsys', 'window', 'windows'], capture_output=True, text=True)
for line in res.stdout.splitlines():
    if 'mCurrentFocus' in line or 'mFocusedApp' in line:
        print(line)
