import os
d = r'C:\Users\admin\.gemini\antigravity-cli\brain\d6bc982b-0100-4c6e-8abd-14870e722c80'
for f in os.listdir(d):
    if f.startswith('screen_v218'):
        fp = os.path.join(d, f)
        print(f, os.path.getsize(fp))
