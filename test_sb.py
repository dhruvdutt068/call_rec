import urllib.request
import urllib.error
import json
import ssl

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

# 1. DEV
dev_url = "https://qizrtmvgcwxuycbpkeua.supabase.co/rest/v1"
dev_key = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFpenJ0bXZnY3d4dXljYnBrZXVhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM3NTAzNTMsImV4cCI6MjA5OTMyNjM1M30.wLHCz8zv79ySmPSnI6Z-WSdD7s9qvzCxVxYQOnIh5WU"

# 2. PROD
prod_url = "https://vxineppqpqslqjqvnlzv.supabase.co/rest/v1"
prod_key = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZ4aW5lcHBxcHFzbHFqcXZubHp2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg0MTI1MjYsImV4cCI6MjEwMzk4ODUyNn0.OZ-LoP2u3a80_Z6HZ5zk2rhCokag1nniBYuTsNRFIpI"

def test_supabase(label, base_url, key):
    print(f"\n--- Testing {label} ({base_url}) ---")
    req = urllib.request.Request(f"{base_url}/sales_calls?limit=1", headers={
        "apikey": key,
        "Authorization": f"Bearer {key}"
    })
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=10) as resp:
            data = json.loads(resp.read().decode())
            print(f"[OK] {label} query succeeded: {data}")
    except urllib.error.HTTPError as e:
        print(f"[FAIL] {label} HTTP error {e.code}: {e.read().decode()}")
    except Exception as e:
        print(f"[FAIL] {label} Exception: {e}")

test_supabase("DEV", dev_url, dev_key)
test_supabase("PROD", prod_url, prod_key)
