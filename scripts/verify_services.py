#!/usr/bin/env python3
"""
Automated verification script for Callog / AllSet CRM Cloud Services:
1. Supabase DEV & PROD: Tests REST PostgREST tables (sales_calls, people, devices, etc.)
2. Firebase Firestore DEV & PROD: Tests /test/connection document read/write on 'call-logs' database
3. Firebase Cloud Storage DEV & PROD: Tests bucket accessibility and listing
"""

import sys
import os
import json
import ssl
import urllib.request
import urllib.error
import time

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

# ------------------------------------------------------------------------------
# Configuration
# ------------------------------------------------------------------------------
SUPABASE_CONFIGS = [
    {
        "env": "DEV",
        "url": "https://qizrtmvgcwxuycbpkeua.supabase.co/rest/v1",
        "key": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFpenJ0bXZnY3d4dXljYnBrZXVhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM3NTAzNTMsImV4cCI6MjA5OTMyNjM1M30.wLHCz8zv79ySmPSnI6Z-WSdD7s9qvzCxVxYQOnIh5WU",
        "tables": ["sales_calls", "people", "devices", "contact_aliases", "phone_numbers"]
    },
    {
        "env": "PROD",
        "url": "https://vxineppqpqslqjqvnlzv.supabase.co/rest/v1",
        "key": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZ4aW5lcHBxcHFzbHFqcXZubHp2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg0MTI1MjYsImV4cCI6MjEwMzk4ODUyNn0.OZ-LoP2u3a80_Z6HZ5zk2rhCokag1nniBYuTsNRFIpI",
        "tables": ["sales_calls", "people", "devices", "contact_aliases", "phone_numbers"]
    }
]

FIREBASE_CONFIGS = [
    {
        "env": "DEV",
        "project_id": "allset-491218",
        "api_key": "AIzaSyBtlY7EoO6PgPUCMjNR55K88H2v665qQgQ",
        "database_id": "call-logs",
        "bucket": "allset_calllogs_bucket"
    },
    {
        "env": "PROD",
        "project_id": "allset-244ab",
        "api_key": "AIzaSyDLqHOgAdLBz9__Un_BI0ZMwz5l2ucJnKo",
        "database_id": "call-logs",
        "bucket": "allset-244ab.firebasestorage.app"
    }
]

def print_header(title):
    print("\n" + "=" * 70)
    print(f"  {title}")
    print("=" * 70)

def test_supabase():
    print_header("1. TESTING SUPABASE SERVICES (DEV & PROD)")
    all_passed = True

    for cfg in SUPABASE_CONFIGS:
        env = cfg["env"]
        base_url = cfg["url"]
        key = cfg["key"]
        print(f"\n--- Checking Supabase [{env}] ({base_url}) ---")

        headers = {
            "apikey": key,
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json"
        }

        for table in cfg["tables"]:
            endpoint = f"{base_url}/{table}?limit=1"
            req = urllib.request.Request(endpoint, headers=headers)
            try:
                with urllib.request.urlopen(req, context=ctx, timeout=12) as resp:
                    data = json.loads(resp.read().decode())
                    print(f"  [OK] Table '{table}': HTTP {resp.status} - Returned {len(data)} record(s)")
            except urllib.error.HTTPError as e:
                body = e.read().decode()[:150]
                print(f"  [FAIL] Table '{table}': HTTP {e.code} - {body}")
                all_passed = False
            except Exception as e:
                print(f"  [FAIL] Table '{table}': Exception - {e}")
                all_passed = False

    return all_passed

def test_firebase_firestore():
    print_header("2. TESTING FIREBASE FIRESTORE (DEV & PROD)")
    all_passed = True

    for cfg in FIREBASE_CONFIGS:
        env = cfg["env"]
        proj = cfg["project_id"]
        key = cfg["api_key"]
        db = cfg["database_id"]

        print(f"\n--- Checking Firestore [{env}] (Project: {proj}, DB: {db}) ---")

        # 1. Write healthcheck document
        test_doc_url = f"https://firestore.googleapis.com/v1/projects/{proj}/databases/{db}/documents/test?documentId=connection&key={key}"
        payload = json.dumps({
            "fields": {
                "message": {"stringValue": f"Automated Connection Test ({env})"},
                "timestamp": {"integerValue": str(int(time.time() * 1000))},
                "status": {"stringValue": "healthy"}
            }
        }).encode("utf-8")

        write_req = urllib.request.Request(
            test_doc_url,
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST"
        )

        try:
            with urllib.request.urlopen(write_req, context=ctx, timeout=12) as resp:
                print(f"  [OK] Write Document (test/connection): HTTP {resp.status} - Document successfully saved")
        except urllib.error.HTTPError as e:
            # If document already exists, update with PATCH
            if e.code == 409 or e.code == 400:
                patch_url = f"https://firestore.googleapis.com/v1/projects/{proj}/databases/{db}/documents/test/connection?key={key}"
                patch_req = urllib.request.Request(
                    patch_url,
                    data=payload,
                    headers={"Content-Type": "application/json"},
                    method="PATCH"
                )
                try:
                    with urllib.request.urlopen(patch_req, context=ctx, timeout=12) as patch_resp:
                        print(f"  [OK] Update Document (test/connection): HTTP {patch_resp.status} - Document updated")
                except Exception as patch_e:
                    print(f"  [FAIL] Update Document failed: {patch_e}")
                    all_passed = False
            else:
                body = e.read().decode()[:150]
                print(f"  [FAIL] Write Document: HTTP {e.code} - {body}")
                all_passed = False
        except Exception as e:
            print(f"  [FAIL] Write Document: {e}")
            all_passed = False

        # 2. Read back document
        read_url = f"https://firestore.googleapis.com/v1/projects/{proj}/databases/{db}/documents/test/connection?key={key}"
        read_req = urllib.request.Request(read_url)
        try:
            with urllib.request.urlopen(read_req, context=ctx, timeout=12) as resp:
                doc = json.loads(resp.read().decode())
                fields = doc.get("fields", {})
                msg = fields.get("message", {}).get("stringValue", "N/A")
                print(f"  [OK] Read Document (test/connection): HTTP {resp.status} - Content: '{msg}'")
        except Exception as e:
            print(f"  [FAIL] Read Document: {e}")
            all_passed = False

    return all_passed

def test_firebase_storage():
    print_header("3. TESTING FIREBASE CLOUD STORAGE (DEV & PROD)")
    all_passed = True

    for cfg in FIREBASE_CONFIGS:
        env = cfg["env"]
        bucket = cfg["bucket"]
        key = cfg["api_key"]

        print(f"\n--- Checking Storage [{env}] (Bucket: {bucket}) ---")
        url = f"https://firebasestorage.googleapis.com/v0/b/{bucket}/o?maxResults=5&key={key}"
        req = urllib.request.Request(url)
        try:
            with urllib.request.urlopen(req, context=ctx, timeout=12) as resp:
                data = json.loads(resp.read().decode())
                items = data.get("items", [])
                print(f"  [OK] Bucket '{bucket}': HTTP {resp.status} - Accessible, sampled {len(items)} audio asset(s)")
                for item in items[:2]:
                    name = item.get("name", "Unknown")
                    size = item.get("size", "0")
                    print(f"       -> Asset: {name} ({size} bytes)")
        except urllib.error.HTTPError as e:
            body = e.read().decode()[:150]
            print(f"  [FAIL] Bucket '{bucket}': HTTP {e.code} - {body}")
            all_passed = False
        except Exception as e:
            print(f"  [FAIL] Bucket '{bucket}': {e}")
            all_passed = False

    return all_passed

if __name__ == "__main__":
    sb_ok = test_supabase()
    fs_ok = test_firebase_firestore()
    st_ok = test_firebase_storage()

    print_header("SUMMARY")
    print(f"  Supabase Verification:         {'PASSED [OK]' if sb_ok else 'FAILED [X]'}")
    print(f"  Firestore Verification:        {'PASSED [OK]' if fs_ok else 'FAILED [X]'}")
    print(f"  Cloud Storage Verification:    {'PASSED [OK]' if st_ok else 'FAILED [X]'}")

    if sb_ok and fs_ok and st_ok:
        print("\n>>> ALL CLOUD SERVICES VERIFIED AND HEALTHY! <<<\n")
        sys.exit(0)
    else:
        print("\n>>> ONE OR MORE SERVICE CHECKS FAILED. <<<\n")
        sys.exit(1)
