package com.example.callog.core.config

/**
 * Single source of truth for hardcoded client / production Supabase credentials.
 * Whenever client credentials need to be updated in the repository, edit this file.
 */
object SupabaseDefaults {
    const val DEFAULT_URL = "https://vxineppqpqslqjqvnlzv.supabase.co"
    const val DEFAULT_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZ4aW5lcHBxcHFzbHFqcXZubHp2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg0MTI1MjYsImV4cCI6MjEwMzk4ODUyNn0.OZ-LoP2u3a80_Z6HZ5zk2rhCokag1nniBYuTsNRFIpI"
    const val TABLE_SALES_CALLS = "sales_calls"
    const val TABLE_PEOPLE = "people"
    const val TABLE_PHONE_NUMBERS = "phone_numbers"
    const val TABLE_CONTACT_ALIASES = "contact_aliases"
    const val TABLE_DEVICES = "devices"
}
