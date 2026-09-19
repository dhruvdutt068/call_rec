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
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.example.callog.core.config.EnvironmentConfigManager
import com.example.callog.domain.model.AppEnvironment
import com.example.callog.domain.provider.ActivePresetProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val environmentConfigManager: EnvironmentConfigManager,
    private val activePresetProvider: ActivePresetProvider
) {
    private val TAG = "FirestoreService"

    private val _lastUploadError = MutableStateFlow<String?>(null)
    val lastUploadError: StateFlow<String?> = _lastUploadError.asStateFlow()

    companion object {
        /**
         * Normalizes storage bucket name / URL into standard bucket format.
         * Handles gs://, https://, whitespace, and falls back to project default if blank.
         */
        fun normalizeStorageBucket(rawBucket: String?, projectId: String?): String {
            var b = rawBucket?.trim() ?: ""
            if (b.startsWith("gs://", ignoreCase = true)) {
                b = b.substring(5)
            }
            if (b.startsWith("https://firebasestorage.googleapis.com/v0/b/", ignoreCase = true)) {
                b = b.removePrefix("https://firebasestorage.googleapis.com/v0/b/").substringBefore("/o").substringBefore("/")
            }
            b = b.trim().trimEnd('/')
            if (b.isBlank() && !projectId.isNullOrBlank()) {
                b = "$projectId.firebasestorage.app"
            }
            return b
        }
    }

    init {
        environmentConfigManager.addOnEnvironmentChangeListener {
            clearCustomApp()
        }
        activePresetProvider.addOnActivePresetChangeListener {
            clearCustomApp()
        }
    }

    fun getSavedConfig(): FirebaseConfig? {
        // 1. Check Active Preset configuration first
        val activePreset = runBlocking {
            try {
                activePresetProvider.getActivePreset()
            } catch (e: Exception) {
                null
            }
        }
        val activeSecrets = runBlocking {
            try {
                activePresetProvider.getActiveSecrets()
            } catch (e: Exception) {
                null
            }
        }

        if (activePreset != null) {
            val projId = activePreset.configuration.firebaseProjectId?.trim()
            val appId = activePreset.configuration.firebaseAppId?.trim()
            val apiKey = activeSecrets?.firebaseApiKey?.trim()
            val bucket = activePreset.configuration.storageBucket?.trim()

            val isDummy = (projId?.startsWith("callog-dev") == true && apiKey?.startsWith("AIzaSyDevKeySample") == true)
            if (!projId.isNullOrBlank() && !appId.isNullOrBlank() && !apiKey.isNullOrBlank() && !isDummy) {
                return FirebaseConfig(
                    projectId = projId,
                    apiKey = apiKey,
                    appId = appId,
                    storageBucket = bucket?.ifBlank { null },
                    gcmSenderId = activePreset.configuration.gcmSenderId?.trim()?.ifBlank { null },
                    databaseUrl = activePreset.configuration.databaseUrl?.trim()?.ifBlank { null }
                )
            }
        }

        // 2. Fall back to EnvironmentConfigManager
        val envConfig = environmentConfigManager.getActiveFirebaseConfig()
        if (envConfig != null) {
            return envConfig
        }

        // 3. Fallback: null means default FirebaseApp (from google-services.json)
        return null
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
            Log.d(TAG, "Deleted custom FirebaseApp instance to allow clean re-initialization.")
        } catch (e: Exception) {
            // Instance did not exist, ignore
        }
    }

    @Synchronized
    fun getOrInitCustomApp(config: FirebaseConfig): FirebaseApp {
        val cleanBucket = normalizeStorageBucket(config.storageBucket, config.projectId)
        val existingApp = try {
            FirebaseApp.getInstance("customApp")
        } catch (e: Exception) {
            null
        }

        if (existingApp != null) {
            val existingOptions = existingApp.options
            val sameProjectId = existingOptions.projectId == config.projectId
            val sameApiKey = existingOptions.apiKey == config.apiKey
            val sameAppId = existingOptions.applicationId == config.appId
            val sameBucket = existingOptions.storageBucket == cleanBucket || (existingOptions.storageBucket.isNullOrBlank() && cleanBucket.isBlank())

            if (sameProjectId && sameApiKey && sameAppId && sameBucket) {
                return existingApp
            } else {
                Log.d(TAG, "Custom FirebaseApp options changed. Re-initializing...")
                try {
                    existingApp.delete()
                } catch (e: Exception) {
                    Log.w(TAG, "Error deleting stale custom FirebaseApp", e)
                }
            }
        }

        val optionsBuilder = FirebaseOptions.Builder()
            .setProjectId(config.projectId)
            .setApiKey(config.apiKey)
            .setApplicationId(config.appId)

        if (cleanBucket.isNotBlank()) {
            optionsBuilder.setStorageBucket(cleanBucket)
        }
        config.gcmSenderId?.takeIf { it.isNotBlank() }?.let { optionsBuilder.setGcmSenderId(it.trim()) }
        config.databaseUrl?.takeIf { it.isNotBlank() }?.let { optionsBuilder.setDatabaseUrl(it.trim()) }

        val app = FirebaseApp.initializeApp(context, optionsBuilder.build(), "customApp")
        Log.i(TAG, "Initialized custom FirebaseApp for project: ${config.projectId}, bucket: $cleanBucket")
        return app
    }

    fun getStorageInstance(): FirebaseStorage? {
        return try {
            val savedConfig = getSavedConfig()
            if (savedConfig != null) {
                val customApp = getOrInitCustomApp(savedConfig)
                val cleanBucket = normalizeStorageBucket(savedConfig.storageBucket, savedConfig.projectId)
                if (cleanBucket.isNotBlank()) {
                    FirebaseStorage.getInstance(customApp, "gs://$cleanBucket")
                } else {
                    FirebaseStorage.getInstance(customApp)
                }
            } else {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context)
                }
                FirebaseStorage.getInstance()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Storage", e)
            DeveloperLogger.error("STORAGE_INIT_FAILED", "Failed to initialize Firebase Storage: ${e.message}", exception = e)
            null
        }
    }

    val db: FirebaseFirestore?
        get() {
            return try {
                val savedConfig = getSavedConfig()
                if (savedConfig != null) {
                    val customApp = getOrInitCustomApp(savedConfig)
                    FirebaseFirestore.getInstance(customApp)
                } else {
                    if (FirebaseApp.getApps(context).isEmpty()) {
                        FirebaseApp.initializeApp(context)
                    }
                    FirebaseFirestore.getInstance()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Firebase Firestore", e)
                DeveloperLogger.error("FIRESTORE_INIT_FAILED", "Failed to initialize Firestore: ${e.message}", exception = e)
                null
            }
        }

    suspend fun testFirebaseConnection(config: FirebaseConfig?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val (firestore, storage) = if (config != null) {
                val app = getOrInitCustomApp(config)
                val cleanBucket = normalizeStorageBucket(config.storageBucket, config.projectId)
                val stor = if (cleanBucket.isNotBlank()) {
                    FirebaseStorage.getInstance(app, "gs://$cleanBucket")
                } else {
                    FirebaseStorage.getInstance(app)
                }
                Pair(FirebaseFirestore.getInstance(app), stor)
            } else {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context)
                }
                Pair(FirebaseFirestore.getInstance(), FirebaseStorage.getInstance())
            }

            // Write a test document that stays in Firestore for inspection
            val docRef = firestore.collection("test").document("connection")
            docRef.set(mapOf(
                "message" to "Firebase & Storage Connected",
                "timestamp" to System.currentTimeMillis()
            )).await()

            // Verify Storage root reference is accessible
            val bucketName = storage.app.options.storageBucket ?: storage.reference.bucket
            Log.i(TAG, "Firebase connection test passed: Firestore writable & Storage referenced ($bucketName)")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase connection test failed", e)
            Result.failure(e)
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
