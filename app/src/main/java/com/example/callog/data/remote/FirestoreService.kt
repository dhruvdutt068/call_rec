package com.example.callog.data.remote

import android.content.Context
import android.util.Log
import com.example.callog.domain.model.FirebaseConfig
import com.example.callog.core.diagnostics.DeveloperLogger
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
import com.example.callog.core.config.EnvironmentConfigManager
import com.example.callog.domain.model.AppEnvironment
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val environmentConfigManager: EnvironmentConfigManager
) {
    private val TAG = "FirestoreService"

    private val _lastUploadError = MutableStateFlow<String?>(null)
    val lastUploadError: StateFlow<String?> = _lastUploadError.asStateFlow()

    init {
        environmentConfigManager.addOnEnvironmentChangeListener {
            clearCustomApp()
        }
    }

    fun getSavedConfig(): FirebaseConfig? {
        return environmentConfigManager.getActiveFirebaseConfig()
    }

    fun getFirebaseConfig(env: AppEnvironment): FirebaseConfig? {
        return environmentConfigManager.getFirebaseConfig(env)
    }

    fun saveConfig(config: FirebaseConfig?, env: AppEnvironment = environmentConfigManager.getActiveEnvironment()) {
        environmentConfigManager.saveFirebaseConfig(env, config)
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

    fun getDeviceOwnerName(): String {
        val prefs = context.getSharedPreferences("firebase_config_prefs", Context.MODE_PRIVATE)
        return prefs.getString("device_owner_name", "") ?: ""
    }

    fun saveDeviceOwnerName(name: String) {
        val prefs = context.getSharedPreferences("firebase_config_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("device_owner_name", name).apply()
    }

    fun getCustomRecordingPath(): String {
        val prefs = context.getSharedPreferences("firebase_config_prefs", Context.MODE_PRIVATE)
        return prefs.getString("custom_recording_path", "") ?: ""
    }

    fun saveCustomRecordingPath(path: String) {
        val prefs = context.getSharedPreferences("firebase_config_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("custom_recording_path", path).apply()
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
                    FirebaseApp.initializeApp(context)
                }
                FirebaseFirestore.getInstance()
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
                        FirebaseApp.initializeApp(context)
                    }
                    FirebaseFirestore.getInstance()
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
            DeveloperLogger.info("FIRESTORE_METADATA_UPLOAD_STARTED", "Uploading call $callId to Firestore")
            Log.d("SYNC", "Uploading call to Firestore:")
            Log.d("SYNC", "Salesperson Name = ${metadata["salesperson_name"]}")
            Log.d("SYNC", "Salesperson Phone = ${metadata["salesperson_phone"]}")
            Log.d("SYNC", "Buyer Name = ${metadata["buyer_name"]}")
            Log.d("SYNC", "Buyer Phone = ${metadata["buyer_phone"]}")
            Log.d("SYNC", "Payload Map = $metadata")

            firestore.collection("users")
                .document(devicePhone)
                .collection("phonelogs")
                .document(phoneNumber)
                .collection(callType)
                .document("call_$callId")
                .set(metadata)
                .await()
            Log.d("FIRESTORE", "Upload Successful for call: $callId")
            DeveloperLogger.success("FIRESTORE_METADATA_UPLOAD_SUCCESS", "Successfully uploaded call $callId metadata to Firestore")
            _lastUploadError.value = null
            true
        } catch (e: Exception) {
            Log.e("FIRESTORE", "Upload Failed for call: $callId", e)
            DeveloperLogger.error("FIRESTORE_METADATA_UPLOAD_FAILED", "Failed uploading call $callId to Firestore", e)
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
            DeveloperLogger.info("FIRESTORE_RECORDING_URL_UPDATE", "Updating call $callId in Firestore")
            Log.d("SYNC", "Updating call in Firestore:")
            Log.d("SYNC", "Salesperson Name = ${metadata["salesperson_name"]}")
            Log.d("SYNC", "Salesperson Phone = ${metadata["salesperson_phone"]}")
            Log.d("SYNC", "Buyer Name = ${metadata["buyer_name"]}")
            Log.d("SYNC", "Buyer Phone = ${metadata["buyer_phone"]}")
            Log.d("SYNC", "Payload Map = $metadata")

            firestore.collection("users")
                .document(devicePhone)
                .collection("phonelogs")
                .document(phoneNumber)
                .collection(callType)
                .document("call_$callId")
                .set(metadata, com.google.firebase.firestore.SetOptions.merge())
                .await()
            Log.d("FIRESTORE", "Upload Successful for call: $callId")
            DeveloperLogger.success("FIRESTORE_RECORDING_URL_UPDATE", "Successfully updated call $callId recording URL / metadata in Firestore")
            _lastUploadError.value = null
            true
        } catch (e: Exception) {
            Log.e("FIRESTORE", "Upload Failed for call: $callId", e)
            DeveloperLogger.error("FIRESTORE_RECORDING_URL_UPDATE", "Failed updating call $callId recording URL in Firestore", e)
            _lastUploadError.value = e.message ?: e.toString()
            false
        }
    }
}
