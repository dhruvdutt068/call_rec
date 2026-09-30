import sqlite3

conn = sqlite3.connect("local_call_vault.db")
c = conn.cursor()

c.execute("""
SELECT id, datetime(timestamp/1000, 'unixepoch', 'localtime'), level, event, status, message 
FROM sync_logs 
WHERE event = 'FIRESTORE_METADATA_UPLOAD_STARTED' OR event = 'CALL_RECEIVER_POST_CALL';
""")
for r in c.fetchall():
    print(r)

print("\n--- All events with timestamp ---")
c.execute("""
SELECT id, datetime(timestamp/1000, 'unixepoch', 'localtime'), level, event, message 
FROM sync_logs 
ORDER BY id ASC;
""")
for r in c.fetchall()[:25]:
    print(r)

conn.close()
