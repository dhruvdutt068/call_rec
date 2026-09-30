package com.example.callog.core.config

import com.example.callog.BuildConfig
import com.example.callog.domain.model.AppEnvironment
import com.example.callog.domain.model.FirebaseConfig

/**
 * Single source of truth for hardcoded/default Firebase configuration.
 * Values are injected at compile time from `secrets.properties` via `BuildConfig`.
 */
object FirebaseDefaults {

    val DEV_CONFIG = FirebaseConfig(
        projectId = BuildConfig.FIREBASE_DEV_PROJECT_ID,
        apiKey = BuildConfig.FIREBASE_DEV_API_KEY,
        appId = BuildConfig.FIREBASE_DEV_APP_ID,
        storageBucket = BuildConfig.FIREBASE_DEV_STORAGE_BUCKET.ifBlank { null },
        gcmSenderId = BuildConfig.FIREBASE_DEV_GCM_SENDER_ID.ifBlank { null },
        databaseUrl = BuildConfig.FIREBASE_DEV_DATABASE_URL.ifBlank { null },
        databaseId = BuildConfig.FIREBASE_DEV_DATABASE_ID.ifBlank { null }
    )

    val PROD_CONFIG = FirebaseConfig(
        projectId = BuildConfig.FIREBASE_PROD_PROJECT_ID,
        apiKey = BuildConfig.FIREBASE_PROD_API_KEY,
        appId = BuildConfig.FIREBASE_PROD_APP_ID,
        storageBucket = BuildConfig.FIREBASE_PROD_STORAGE_BUCKET.ifBlank { null },
        gcmSenderId = BuildConfig.FIREBASE_PROD_GCM_SENDER_ID.ifBlank { null },
        databaseUrl = null,
        databaseId = BuildConfig.FIREBASE_PROD_DATABASE_ID.ifBlank { null }
    )

    fun forEnvironment(env: AppEnvironment): FirebaseConfig {
        return if (env == AppEnvironment.DEVELOPMENT) DEV_CONFIG else PROD_CONFIG
    }
}
