package com.example.callog.core.telecom

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import com.example.callog.domain.model.OemCapabilityInfo
import com.example.callog.domain.model.OemType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects device hardware manufacturer, OEM software environment, and provides
 * verified settings navigation and step-by-step guidance for native two-way call recording.
 *
 * Implements Phase 5: OEM Recording Onboarding
 */
@Singleton
class OemCapabilityDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun detectCapabilities(): OemCapabilityInfo {
        val manufacturer = Build.MANUFACTURER.trim()
        val brand = Build.BRAND.trim()
        val model = Build.MODEL.trim()
        val sdkVersion = Build.VERSION.SDK_INT

        val oemType = resolveOemType(manufacturer, brand)

        val settingsIntent = resolveVerifiedSettingsIntent(oemType)
        val instructions = getInstructionsForOem(oemType)

        val isNativeSupported = when (oemType) {
            OemType.SAMSUNG,
            OemType.XIAOMI,
            OemType.ONEPLUS,
            OemType.OPPO,
            OemType.REALME,
            OemType.VIVO -> true
            OemType.GOOGLE_PIXEL -> false // Stock Pixel dialer restricts auto-recording in most regions
            OemType.OTHER -> false
        }

        val note = when (oemType) {
            OemType.SAMSUNG -> "Samsung One UI supports built-in 2-way call recording via Phone app settings."
            OemType.XIAOMI -> "Xiaomi MIUI & HyperOS support native auto-recording saved to sound_recorder/call_rec."
            OemType.ONEPLUS -> "OxygenOS / ColorOS base provides auto-call recording in Phone Settings."
            OemType.OPPO, OemType.REALME -> "ColorOS / Realme UI provides native Call Recording in Phone -> Call Settings."
            OemType.VIVO -> "Funtouch OS / OriginOS provides Call Recording under Settings -> Phone / Call Settings."
            OemType.GOOGLE_PIXEL -> "Google Phone app does not allow automatic 2-way recording in this region."
            OemType.OTHER -> "Native recording availability depends on regional telecom firmware."
        }

        return OemCapabilityInfo(
            manufacturer = manufacturer,
            model = model,
            androidVersion = sdkVersion,
            oemType = oemType,
            isNativeRecordingSupported = isNativeSupported,
            settingsIntent = settingsIntent,
            isDirectoryDiscoverySupported = isNativeSupported,
            isManualSetupRequired = isNativeSupported,
            setupInstructions = instructions,
            autoRecordingDetectionNote = note
        )
    }

    internal fun resolveOemType(manufacturer: String, brand: String): OemType {
        val m = manufacturer.lowercase()
        val b = brand.lowercase()

        return when {
            m.contains("samsung") || b.contains("samsung") -> OemType.SAMSUNG
            m.contains("xiaomi") || b.contains("xiaomi") || m.contains("redmi") || b.contains("poco") -> OemType.XIAOMI
            m.contains("oneplus") || b.contains("oneplus") -> OemType.ONEPLUS
            m.contains("oppo") || b.contains("oppo") -> OemType.OPPO
            m.contains("realme") || b.contains("realme") -> OemType.REALME
            m.contains("vivo") || b.contains("vivo") || m.contains("iqoo") || b.contains("iqoo") -> OemType.VIVO
            m.contains("google") || b.contains("google") -> OemType.GOOGLE_PIXEL
            else -> OemType.OTHER
        }
    }

    private fun resolveVerifiedSettingsIntent(oemType: OemType): Intent? {
        val candidates = mutableListOf<Intent>()

        when (oemType) {
            OemType.SAMSUNG -> {
                candidates.add(Intent("com.samsung.android.app.telephonyui.action.OPEN_RECORD_SETTINGS"))
                candidates.add(Intent().setComponent(ComponentName("com.samsung.android.dialer", "com.samsung.android.dialer.DialtactsActivity")))
            }
            OemType.XIAOMI -> {
                candidates.add(Intent().setComponent(ComponentName("com.android.phone", "com.android.phone.MiuiCallRecordSetting")))
                candidates.add(Intent().setComponent(ComponentName("com.android.phone", "com.android.phone.settings.CallRecordSetting")))
            }
            OemType.OPPO, OemType.REALME -> {
                candidates.add(Intent().setComponent(ComponentName("com.android.phone", "com.android.phone.OppoCallFeaturesSetting")))
                candidates.add(Intent().setComponent(ComponentName("com.oppo.callsetting", "com.oppo.callsetting.CallRecordActivity")))
            }
            OemType.ONEPLUS -> {
                candidates.add(Intent().setComponent(ComponentName("com.oneplus.dialer", "com.oneplus.dialer.callrecord.CallRecordSettingsActivity")))
            }
            OemType.VIVO -> {
                candidates.add(Intent().setComponent(ComponentName("com.android.phone", "com.android.phone.RecordSettingsActivity")))
                candidates.add(Intent().setComponent(ComponentName("com.vivo.upslide", "com.vivo.upslide.record.RecordSettingActivity")))
            }
            else -> {}
        }

        // Standard Telecom / Phone Call Settings fallback
        candidates.add(Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS))
        candidates.add(Intent(android.provider.Settings.ACTION_SETTINGS))

        val pm = context.packageManager
        for (intent in candidates) {
            try {
                if (intent.resolveActivity(pm) != null) {
                    return intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                }
            } catch (_: Exception) {
                // Ignore and check next fallback
            }
        }
        return null
    }

    internal fun getInstructionsForOem(oemType: OemType): List<String> {
        return when (oemType) {
            OemType.SAMSUNG -> listOf(
                "Open the Samsung Phone app.",
                "Tap the 3 dots menu (⋮) in the top-right corner and select 'Settings'.",
                "Tap 'Record calls'.",
                "Enable 'Auto record calls' and choose 'All calls'.",
                "Recordings are saved to 'Recordings/Call' and will be auto-synced by Callog."
            )
            OemType.XIAOMI -> listOf(
                "Open the native Phone app (or Settings -> Apps -> System app settings -> Call settings).",
                "Tap 'Call recording'.",
                "Turn ON 'Record calls automatically'.",
                "Select 'All numbers' to ensure complete CRM coverage.",
                "Recordings are saved to 'MIUI/sound_recorder/call_rec' and auto-discovered."
            )
            OemType.ONEPLUS -> listOf(
                "Open Phone app -> Settings (⋮ menu).",
                "Tap 'Call recording'.",
                "Toggle 'Auto-record calls' to ON.",
                "Verify Callog has Audio/Storage permission to discover recordings in 'Recordings/Call'."
            )
            OemType.OPPO, OemType.REALME -> listOf(
                "Open Settings -> Mobile network -> Call settings (or Phone -> ⋮ -> Settings).",
                "Tap 'Call recording'.",
                "Enable 'Record all calls'.",
                "Callog will automatically index new recordings from 'Record/Call'."
            )
            OemType.VIVO -> listOf(
                "Open Phone app -> Settings (or Settings -> Phone -> Call recording).",
                "Tap 'Record settings' and select 'Record all calls automatically'.",
                "IMPORTANT: On Vivo, the native call recorder is built into Vivo's system dialer. If Callog is set as the Default Phone App, Vivo bypasses recording. Keep Vivo Phone as default and use Callog's Smart CRM Overlay Mode.",
                "Settings -> Battery -> Background power consumption -> Callog -> Set to 'High background power usage' so recordings auto-sync."
            )
            OemType.GOOGLE_PIXEL -> listOf(
                "Google Pixel devices use Google Dialer which strictly limits automatic 2-way recording.",
                "If manual recording is started during calls in Phone app, Callog will attempt to correlate exported audio files.",
                "Alternatively, use Callog's manual audio attachment feature in Call Summary."
            )
            OemType.OTHER -> listOf(
                "Open your device's native Phone/Dialer app.",
                "Navigate to Settings -> Call Recording (if supported by manufacturer).",
                "Enable 'Auto Call Recording' for all calls.",
                "If unavailable, use Callog's manual recording import option."
            )
        }
    }
}
