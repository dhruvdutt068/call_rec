package com.example.callog.core.config

import com.example.callog.BuildConfig

/**
 * Single source of truth for Supabase credentials and constants.
 * URLs and Anon Keys are injected at compile time from `secrets.properties` via `BuildConfig`.
 */
object SupabaseDefaults {
    // Development Supabase (defaults to working client instance)
    val DEV_URL: String = BuildConfig.SUPABASE_DEV_URL
    val DEV_ANON_KEY: String = BuildConfig.SUPABASE_DEV_ANON_KEY

    // Production Supabase
    val PROD_URL: String = BuildConfig.SUPABASE_PROD_URL
    val PROD_ANON_KEY: String = BuildConfig.SUPABASE_PROD_ANON_KEY

    // Default points to Production (AllSet CRM)
    val DEFAULT_URL: String = PROD_URL
    val DEFAULT_ANON_KEY: String = PROD_ANON_KEY

    const val TABLE_SALES_CALLS = "sales_calls"
    const val TABLE_PEOPLE = "people"
    const val TABLE_PHONE_NUMBERS = "phone_numbers"
    const val TABLE_CONTACT_ALIASES = "contact_aliases"
    const val TABLE_DEVICES = "devices"
    const val TABLE_LEADS = "leads"
    const val TABLE_LEADS_MASTER = "leads_master"
    const val TABLE_LEAD_STAGES = "lead_stages"
    const val TABLE_LEAD_STATUSES = "lead_statuses"
    const val TABLE_LEAD_TASKS = "lead_tasks"
    const val TABLE_LEAD_NOTES = "lead_notes"
    const val TABLE_LEAD_STATE_HISTORY = "lead_state_history"
}
