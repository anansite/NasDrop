import sqlite3
conn = sqlite3.connect("/data/nasdrop.db")
cur = conn.cursor()
cur.execute("SELECT path, name, size, modified, taken_at, has_thumb FROM photo_metadata LIMIT 5")
for row in cur.fetchall():
    print(row)
cur.execute("SELECT COUNT(*), SUM(CASE WHEN has_thumb=1 THEN 1 ELSE 0 END), SUM(CASE WHEN has_thumb=0 THEN 1 ELSE 0 END) FROM photo_metadata")
print("Total, has_thumb, no_thumb:", cur.fetchone())
