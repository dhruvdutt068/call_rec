package com.example.callog.core.constants

object Constants {
    const val DATABASE_NAME = "call_vault_db"
    
    // Standard directories for scanned recordings
    val RECORDING_DIRECTORIES = listOf(
        "Recordings/Callog",
        "Recordings/Call Vault",
        "Recordings",
        "Recordings/Call",
        "Sounds/Call",
        "Recordings/Record/Call",
        "Recordings/Record",
        "Record/Call",
        "Call",
        "Samsung/Call",
        "MIUI/sound_recorder/call_rec",
        "OnePlus/Record/PhoneRecord",
        "Record/PhoneRecord",
        "Music/Recordings/CallRecordings",
        "Recordings/PhoneRecord",
        "Record",
        "vivo/record",
        "vivo/records",
        "Sounds/Recordings"
    )

    // Notification constants
    const val REMINDER_NOTIFICATION_ID = 1001

    // Audio file extensions commonly used for call recordings
    val SUPPORTED_RECORDING_EXTENSIONS = listOf(
        "mp3", "m4a", "amr", "3gp", "wav", "aac", "ogg"
    )
}
