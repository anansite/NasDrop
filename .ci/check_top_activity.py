import subprocess
ADB = r'C:\Users\admin\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe'
DEVICE = '127.0.0.1:16384'
subprocess.run([ADB, 'connect', DEVICE], capture_output=True)
res = subprocess.run([ADB, '-s', DEVICE, 'shell', 'dumpsys', 'activity', 'top'], capture_output=True, text=True)
print('\n'.join(res.stdout.splitlines()[:25]))
