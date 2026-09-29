package com.example.callog.core.config

/**
 * Single source of truth for hardcoded client / production Supabase credentials.
 * Whenever client credentials need to be updated in the repository, edit this file.
 */
object SupabaseDefaults {
    // Development Supabase
    //https://qizrtmvgcwxuycbpkeua.supabase.co
    const val DEV_URL = "https://qizrtmvgcwxuycbpkeua.supabase.co"
    const val DEV_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFpenJ0bXZnY3d4dXljYnBrZXVhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM3NTAzNTMsImV4cCI6MjA5OTMyNjM1M30.wLHCz8zv79ySmPSnI6Z-WSdD7s9qvzCxVxYQOnIh5WU"

    // Production Supabase
    const val PROD_URL = "https://vxineppqpqslqjqvnlzv.supabase.co"
    const val PROD_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZ4aW5lcHBxcHFzbHFqcXZubHp2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg0MTI1MjYsImV4cCI6MjEwMzk4ODUyNn0.OZ-LoP2u3a80_Z6HZ5zk2rhCokag1nniBYuTsNRFIpI"

    // Default points to Development
    const val DEFAULT_URL = DEV_URL
    const val DEFAULT_ANON_KEY = DEV_ANON_KEY
    const val TABLE_SALES_CALLS = "sales_calls"
    const val TABLE_PEOPLE = "people"
    const val TABLE_PHONE_NUMBERS = "phone_numbers"
    const val TABLE_CONTACT_ALIASES = "contact_aliases"
    const val TABLE_DEVICES = "devices"
    const val TABLE_LEADS = "leads"
}
