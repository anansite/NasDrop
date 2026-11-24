import subprocess
ADB = r'C:\Users\admin\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe'
subprocess.run([ADB, 'connect', '127.0.0.1:16384'])
res = subprocess.run([ADB, '-s', '127.0.0.1:16384', 'shell', 'am', 'start', '-a', 'android.intent.action.PICK', '-d', 'content://media/external/images/media'], capture_output=True, text=True)
print('STDOUT:', res.stdout)
print('STDERR:', res.stderr)
