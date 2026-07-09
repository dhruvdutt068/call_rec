package com.example.callog.data.remote

import android.content.Context
import android.util.Log
import com.example.callog.domain.model.FirebaseConfig
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "FirestoreService"

    private val _lastUploadError = MutableStateFlow<String?>(null)
    val lastUploadError: StateFlow<String?> = _lastUploadError.asStateFlow()

    fun getSavedConfig(): FirebaseConfig? {
        val prefs = context.getSharedPreferences("firebase_config_prefs", Context.MODE_PRIVATE)
        val projectId = prefs.getString("project_id", null)
        val apiKey = prefs.getString("api_key", null)
        val appId = prefs.getString("app_id", null)
        return if (!projectId.isNullOrEmpty() && !apiKey.isNullOrEmpty() && !appId.isNullOrEmpty()) {
            FirebaseConfig(projectId, apiKey, appId)
        } else {
            null
        }
    }

    fun saveConfig(config: FirebaseConfig?) {
        val prefs = context.getSharedPreferences("firebase_config_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (config != null) {
                putString("project_id", config.projectId)
                putString("api_key", config.apiKey)
                putString("app_id", config.appId)
            } else {
                remove("project_id")
                remove("api_key")
                remove("app_id")
            }
            apply()
        }
        clearCustomApp()
    }

    fun getDevicePhoneNumber(): String {
        val prefs = context.getSharedPreferences("firebase_config_prefs", Context.MODE_PRIVATE)
        return prefs.getString("device_phone_number", "") ?: ""
    }

    fun saveDevicePhoneNumber(phoneNumber: String) {
        val prefs = context.getSharedPreferences("firebase_config_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("device_phone_number", phoneNumber).apply()
    }

    private fun getNormalizedDevicePhoneNumber(): String {
        val raw = getDevicePhoneNumber()
        if (raw.isEmpty()) return "unknown_device"
        val clean = raw.replace(Regex("[\\s\\-()+]"), "")
        if (clean.length >= 10) {
            return "91" + clean.takeLast(10)
        }
        return clean.ifEmpty { "unknown_device" }
    }

    fun clearCustomApp() {
        try {
            val customApp = FirebaseApp.getInstance("customApp")
            customApp.delete()
            Log.d(TAG, "Deleted custom FirebaseApp instance to allow re-initialization.")
        } catch (e: Exception) {
            // Instance did not exist, ignore
        }
    }

    suspend fun testFirebaseConnection(config: FirebaseConfig?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val firestore = if (config != null) {
                // Delete old customApp if it exists to test clean config
                try {
                    FirebaseApp.getInstance("customApp").delete()
                } catch (e: Exception) {}

                val options = FirebaseOptions.Builder()
                    .setProjectId(config.projectId)
                    .setApiKey(config.apiKey)
                    .setApplicationId(config.appId)
                    .build()
                
                val testApp = FirebaseApp.initializeApp(context, options, "customApp")
                FirebaseFirestore.getInstance(testApp)
            } else {
                // Use default FirebaseApp (which evaluates google-services.json)
                if (FirebaseApp.getApps(context).isEmpty()) {
                    Log.w(TAG, "Firebase not initialized. Initializing default app programmatically with placeholder credentials.")
                    val options = FirebaseOptions.Builder()
                        .setApplicationId("1:123456789012:android:abcdef0123456789")
                        .setProjectId("callvault-placeholder-id")
                        .setApiKey("placeholder-api-key")
                        .build()
                    FirebaseApp.initializeApp(context, options)
                }
                Firebase.firestore
            }

            // Write a test document that stays in Firestore for inspection
            val docRef = firestore.collection("test").document("connection")
            docRef.set(mapOf(
                "message" to "Firebase Connected",
                "timestamp" to System.currentTimeMillis()
            )).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase connection test failed", e)
            Result.failure(e)
        }
    }

    private val db: FirebaseFirestore?
        get() {
            return try {
                val savedConfig = getSavedConfig()
                if (savedConfig != null) {
                    val customApp = try {
                        FirebaseApp.getInstance("customApp")
                    } catch (e: Exception) {
                        val options = FirebaseOptions.Builder()
                            .setProjectId(savedConfig.projectId)
                            .setApiKey(savedConfig.apiKey)
                            .setApplicationId(savedConfig.appId)
                            .build()
                        FirebaseApp.initializeApp(context, options, "customApp")
                    }
                    FirebaseFirestore.getInstance(customApp)
                } else {
                    if (FirebaseApp.getApps(context).isEmpty()) {
                        Log.w(TAG, "Firebase not initialized. Initializing programmatically with placeholder credentials.")
                        val options = FirebaseOptions.Builder()
                            .setApplicationId("1:123456789012:android:abcdef0123456789")
                            .setProjectId("callvault-placeholder-id")
                            .setApiKey("placeholder-api-key")
                            .build()
                        FirebaseApp.initializeApp(context, options)
                    }
                    Firebase.firestore
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Firebase Firestore", e)
                null
            }
        }

    suspend fun checkIfExists(phoneNumber: String, callType: String, callId: String): Boolean {
        val firestore = db ?: return false
        val devicePhone = getNormalizedDevicePhoneNumber()
        return try {
            val doc = firestore.collection("users")
                .document(devicePhone)
                .collection("phonelogs")
                .document(phoneNumber)
                .collection(callType)
                .document("call_$callId")
                .get()
                .await()
            doc.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if call document exists: $callId under $devicePhone/$phoneNumber/$callType", e)
            false
        }
    }

    suspend fun getCallMetadata(phoneNumber: String, callType: String, callId: String): Map<String, Any>? {
        val firestore = db ?: return null
        val devicePhone = getNormalizedDevicePhoneNumber()
        return try {
            val doc = firestore.collection("users")
                .document(devicePhone)
                .collection("phonelogs")
                .document(phoneNumber)
                .collection(callType)
                .document("call_$callId")
                .get()
                .await()
            if (doc.exists()) doc.data else null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting call metadata: $callId under $devicePhone/$phoneNumber/$callType", e)
            null
        }
    }

    suspend fun uploadCallMetadata(phoneNumber: String, callType: String, callId: String, metadata: Map<String, Any>): Boolean {
        val firestore = db ?: run {
            _lastUploadError.value = "Firestore is not initialized. Please verify your connection setup."
            null
        } ?: return false
        val devicePhone = getNormalizedDevicePhoneNumber()
        return try {
            firestore.collection("users")
                .document(devicePhone)
                .collection("phonelogs")
                .document(phoneNumber)
                .collection(callType)
                .document("call_$callId")
                .set(metadata)
                .await()
            Log.i(TAG, "Successfully uploaded metadata for call: $callId")
            _lastUploadError.value = null
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading call metadata: $callId under $devicePhone/$phoneNumber/$callType", e)
            _lastUploadError.value = e.message ?: e.toString()
            false
        }
    }

    suspend fun updateCallMetadata(phoneNumber: String, callType: String, callId: String, metadata: Map<String, Any>): Boolean {
        val firestore = db ?: run {
            _lastUploadError.value = "Firestore is not initialized. Please verify your connection setup."
            null
        } ?: return false
        val devicePhone = getNormalizedDevicePhoneNumber()
        return try {
            firestore.collection("users")
                .document(devicePhone)
                .collection("phonelogs")
                .document(phoneNumber)
                .collection(callType)
                .document("call_$callId")
                .set(metadata, com.google.firebase.firestore.SetOptions.merge())
                .await()
            Log.i(TAG, "Successfully updated metadata for call: $callId")
            _lastUploadError.value = null
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating call metadata: $callId under $devicePhone/$phoneNumber/$callType", e)
            _lastUploadError.value = e.message ?: e.toString()
            false
        }
    }
}
