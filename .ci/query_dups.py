import sqlite3

conn = sqlite3.connect('/config/nasdrop.db')
cur = conn.cursor()
cur.execute('SELECT name, count(*), group_concat(path, " | ") FROM photo_metadata GROUP BY name HAVING count(*) > 1 ORDER BY count(*) DESC LIMIT 30')
rows = cur.fetchall()
print(f"Total duplicate groups: {len(rows)}")
for r in rows:
    print(f"Name: {r[0]}, Count: {r[1]}, Paths: {r[2]}")
