import subprocess

ADB = r'C:\Users\admin\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe'

def get_device():
    subprocess.run([ADB, 'connect', '127.0.0.1:16384'], capture_output=True)
    res = subprocess.run([ADB, 'devices'], capture_output=True, text=True)
    for line in res.stdout.splitlines()[1:]:
        parts = line.strip().split()
        if len(parts) >= 2 and parts[1] == 'device':
            return parts[0]
    return '127.0.0.1:16384'

dev = get_device()
print('Target device:', dev)

# Test 1: ACTION_PICK for images
res1 = subprocess.run([ADB, '-s', dev, 'shell', 'am', 'start', '-a', 'android.intent.action.PICK', '-d', 'content://media/external/images/media'], capture_output=True, text=True)
print('PICK images:', res1.stdout)
subprocess.run([ADB, '-s', dev, 'shell', 'screencap', '-p', '/sdcard/test_pick_img.png'])
subprocess.run([ADB, '-s', dev, 'pull', '/sdcard/test_pick_img.png', '.ci/test_pick_img.png'])

# Back out
subprocess.run([ADB, '-s', dev, 'shell', 'input', 'keyevent', '4'])

# Test 2: ACTION_OPEN_DOCUMENT for */*
res2 = subprocess.run([ADB, '-s', dev, 'shell', 'am', 'start', '-a', 'android.intent.action.OPEN_DOCUMENT', '-c', 'android.intent.category.OPENABLE', '-t', '*/*'], capture_output=True, text=True)
print('OPEN_DOC */*:', res2.stdout)
subprocess.run([ADB, '-s', dev, 'shell', 'screencap', '-p', '/sdcard/test_open_doc.png'])
subprocess.run([ADB, '-s', dev, 'pull', '/sdcard/test_open_doc.png', '.ci/test_open_doc.png'])

print('Screenshots captured successfully!')
