import subprocess, time

ADB = r"C:\Program Files\Netease\MuMu\nx_main\adb.exe"
DEV = "127.0.0.1:16384"
subprocess.run([ADB, "connect", DEV], check=False)
subprocess.run([ADB, "-s", DEV, "shell", "pm", "clear", "com.nasdrop"], check=False)
time.sleep(1)
subprocess.run([ADB, "-s", DEV, "shell", "am", "start", "-n", "com.nasdrop/.MainActivity"], check=False)
time.sleep(4)
