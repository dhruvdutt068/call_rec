package com.example.callog.core.config

/**
 * Single source of truth for hardcoded client / production Supabase credentials.
 * Whenever client credentials need to be updated in the repository, edit this file.
 */
object SupabaseDefaults {
    const val DEFAULT_URL = "https://qizrtmvgcwxuycbpkeua.supabase.co"
    const val DEFAULT_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFpenJ0bXZnY3d4dXljYnBrZXVhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM3NTAzNTMsImV4cCI6MjA5OTMyNjM1M30.wLHCz8zv79ySmPSnI6Z-WSdD7s9qvzCxVxYQOnIh5WU"
    const val TABLE_SALES_CALLS = "sales_calls"
}
