package com.example.callog.domain.model

import android.content.Intent

/**
 * Categorization of supported OEM device manufacturers.
 */
enum class OemType(val displayName: String) {
    SAMSUNG("Samsung (One UI)"),
    XIAOMI("Xiaomi (MIUI / HyperOS)"),
    ONEPLUS("OnePlus (OxygenOS)"),
    OPPO("Oppo (ColorOS)"),
    REALME("Realme (Realme UI)"),
    VIVO("Vivo (Funtouch OS / OriginOS)"),
    GOOGLE_PIXEL("Google Pixel"),
    OTHER("Standard Android")
}

/**
 * Encapsulates the detected device OEM capabilities regarding native call recording.
 */
data class OemCapabilityInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: Int,
    val oemType: OemType,
    val isNativeRecordingSupported: Boolean,
    val settingsIntent: Intent?,
    val isDirectoryDiscoverySupported: Boolean,
    val isManualSetupRequired: Boolean,
    val setupInstructions: List<String>,
    val autoRecordingDetectionNote: String
)
