import subprocess
import os
import sqlite3

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')

local_db_path = "local_call_vault.db"

# Pull call_vault_db using cat via adb exec-out
with open(local_db_path, "wb") as f:
    subprocess.run([adb, "exec-out", "run-as", "com.example.callog", "cat", "databases/call_vault_db"], stdout=f, check=True)

# Also pull -wal if it exists
with open("local_call_vault.db-wal", "wb") as f:
    subprocess.run([adb, "exec-out", "run-as", "com.example.callog", "cat", "databases/call_vault_db-wal"], stdout=f)

# Also pull -shm if it exists
with open("local_call_vault.db-shm", "wb") as f:
    subprocess.run([adb, "exec-out", "run-as", "com.example.callog", "cat", "databases/call_vault_db-shm"], stdout=f)

conn = sqlite3.connect(local_db_path)
cursor = conn.cursor()

print("--- Sales Calls Summary ---")
cursor.execute("SELECT count(*), syncStatus, syncError FROM sales_calls GROUP BY syncStatus, syncError;")
for r in cursor.fetchall():
    print(r)

print("\n--- Total Calls vs Sales Calls ---")
cursor.execute("SELECT count(*) FROM calls_research;")
print("calls_research count:", cursor.fetchone()[0])
cursor.execute("SELECT count(*) FROM sales_calls;")
print("sales_calls count:", cursor.fetchone()[0])

print("\n--- Sample Sales Calls (5 rows) ---")
cursor.execute("SELECT id, callId, salespersonPhone, salespersonName, buyerPhone, syncStatus, syncError, personId, createdAt FROM sales_calls LIMIT 5;")
for r in cursor.fetchall():
    print(r)

print("\n--- Recent Sync Logs (last 20) ---")
cursor.execute("SELECT eventType, level, message, details, timestamp FROM sync_logs ORDER BY id DESC LIMIT 20;")
for r in cursor.fetchall():
    print(r)

conn.close()
