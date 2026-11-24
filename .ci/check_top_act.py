import subprocess
ADB = r'C:\Users\admin\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe'
DEVICE = '127.0.0.1:16384'
subprocess.run([ADB, 'connect', DEVICE], capture_output=True)
res = subprocess.run([ADB, '-s', DEVICE, 'shell', 'dumpsys', 'activity', 'activities'], capture_output=True, text=True)
for line in res.stdout.splitlines():
    if 'mResumedActivity' in line or 'topResumedActivity' in line or 'ResumedActivity:' in line:
        print(line.strip())
