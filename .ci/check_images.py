import os
d = r'C:\Users\admin\.gemini\antigravity-cli\brain\d6bc982b-0100-4c6e-8abd-14870e722c80'
for f in sorted(os.listdir(d)):
    if 'v218' in f and f.endswith('.png'):
        fp = os.path.join(d, f)
        print(f, os.path.getsize(fp))
