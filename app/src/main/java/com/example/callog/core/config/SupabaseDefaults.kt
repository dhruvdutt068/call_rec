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
    const val PROD_URL = "https://nmhgdhehsfcootlqwumw.supabase.co"
    const val PROD_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5taGdkaGVoc2Zjb290bHF3dW13Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODgzMjc1OTEsImV4cCI6MjEwMzkwMzU5MX0.ThJvtEr2kmmaPQIPFBy-bCJIliHVqv2m_2SS-ZR2Gm0"

    // Default points to Production (AllSet CRM)
    const val DEFAULT_URL = PROD_URL
    const val DEFAULT_ANON_KEY = PROD_ANON_KEY
    const val TABLE_SALES_CALLS = "sales_calls"
    const val TABLE_PEOPLE = "people"
    const val TABLE_PHONE_NUMBERS = "phone_numbers"
    const val TABLE_CONTACT_ALIASES = "contact_aliases"
    const val TABLE_DEVICES = "devices"
    const val TABLE_LEADS = "leads"
    const val TABLE_LEADS_MASTER = "leads_master"
    const val TABLE_LEAD_STAGES = "lead_stages"
    const val TABLE_LEAD_STATUSES = "lead_statuses"
    const val TABLE_LEAD_COMMUNICATIONS = "lead_communications"
    const val TABLE_LEAD_TASKS = "lead_tasks"
    const val TABLE_LEAD_NOTES = "lead_notes"
    const val TABLE_LEAD_STATE_HISTORY = "lead_state_history"
    const val TABLE_CALLS_LOG = "calls_log"
}
