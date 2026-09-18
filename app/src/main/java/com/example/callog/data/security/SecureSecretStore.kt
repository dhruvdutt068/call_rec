package com.example.callog.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.example.callog.domain.model.preset.PresetSecrets
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureSecretStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "SecureSecretStore"
    private val PREFS_NAME = "callog_secure_preset_secrets"
    private val KEY_ALIAS = "CallogPresetMasterKey_v1"
    private val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val TRANSFORMATION = "AES/GCM/NoPadding"
    private val GCM_IV_LENGTH = 12
    private val GCM_TAG_LENGTH = 128

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            } else {
                (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
            }
        } catch (e: Exception) {
            Log.w(TAG, "AndroidKeyStore not available (likely unit test environment). Using software fallback.", e)
            getFallbackSoftwareKey()
        }
    }

    // Fallback software key for Robolectric/JVM Unit Tests
    private var fallbackKey: SecretKey? = null
    private fun getFallbackSoftwareKey(): SecretKey {
        return fallbackKey ?: run {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            keyGen.generateKey().also { fallbackKey = it }
        }
    }

    fun saveSecrets(presetId: String, secrets: PresetSecrets) {
        try {
            val jsonString = json.encodeToString(secrets)
            val secretKey = getOrCreateSecretKey()

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(jsonString.toByteArray(Charsets.UTF_8))

            // Combined IV + CipherText
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            val base64 = Base64.encodeToString(combined, Base64.NO_WRAP)
            prefs.edit().putString("secret_$presetId", base64).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt and save secrets for preset $presetId", e)
        }
    }

    fun getSecrets(presetId: String): PresetSecrets? {
        val base64 = prefs.getString("secret_$presetId", null) ?: return null
        return try {
            val combined = Base64.decode(base64, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) return null

            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val plainTextBytes = cipher.doFinal(cipherText)
            val jsonString = String(plainTextBytes, Charsets.UTF_8)
            json.decodeFromString<PresetSecrets>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt secrets for preset $presetId", e)
            null
        }
    }

    fun deleteSecrets(presetId: String) {
        prefs.edit().remove("secret_$presetId").apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
