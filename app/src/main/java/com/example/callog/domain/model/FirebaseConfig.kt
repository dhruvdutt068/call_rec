package com.example.callog.domain.model

data class FirebaseConfig(
    val projectId: String,
    val apiKey: String,
    val appId: String,
    val storageBucket: String? = null,
    val gcmSenderId: String? = null,
    val databaseUrl: String? = null
)
