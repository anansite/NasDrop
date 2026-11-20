with open('app/src/main/java/com/nasdrop/ui/BrowserScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()
b = 0
for idx, ch in enumerate(text):
    if ch == '{': b += 1
    elif ch == '}': b -= 1
print('Brace balance:', b)
