# CRM Custom Ringtone & Audio Developer Guide

## 1. Quick Overview

This guide serves as a practical developer runbook for understanding, modifying, extending, and debugging the CRM custom ringtone and audio routing subsystem in **Callog (AllSet CRM)**.

---

## 2. Key Classes & Package Map

| Role | Class / File | Package | Responsibilities |
|---|---|---|---|
| **Authoritative Telecom Audio Controller** | [`RingtoneController`](file:///D:/repo/app/src/main/java/com/example/callog/core/telecom/RingtoneController.kt) | `com.example.callog.core.telecom` | Plays ringtone via `MediaPlayer`, manages audio focus, observes ringer modes (`SILENT`, `VIBRATE`, `NORMAL`), handles transient ducking, and executes lead-aware haptic vibrations. |
| **High-Level Telecom Ringtone Manager** | [`CallRingtoneManager`](file:///D:/repo/app/src/main/java/com/example/callog/core/telecom/CallRingtoneManager.kt) | `com.example.callog.core.telecom` | Bridges `RingtoneController` and `CallHapticManager`, coordinating silence and call-lifecycle haptics (call connected, call ended). |
| **Contact Ringtone Sync & Injection** | [`ContactRingtoneSyncManager`](file:///D:/repo/app/src/main/java/com/example/callog/data/provider/ContactRingtoneSyncManager.kt) | `com.example.callog.data.provider` | Injects MediaStore-exported ringtone URIs into `ContactsContract.Contacts.CUSTOM_RINGTONE` and checks `PhoneLookup` to avoid dual-ringing. |
| **Public MediaStore Exporter** | [`MediaStoreRingtoneExporter`](file:///D:/repo/app/src/main/java/com/example/callog/data/telecom/MediaStoreRingtoneExporter.kt) | `com.example.callog.data.telecom` | Copies private internal `.audio` files to public `MediaStore.Audio.Media` so Samsung InCallUI (separate UID) can read them without `SecurityException`. |
| **Domain Policy Contract** | [`RingtonePolicy`](file:///D:/repo/app/src/main/java/com/example/callog/domain/repository/RingtonePolicy.kt) | `com.example.callog.domain.repository` | Defines interface `ringtoneFor(person, lead): Uri`. |
| **Default Policy Engine** | [`DefaultRingtonePolicy`](file:///D:/repo/app/src/main/java/com/example/callog/data/telecom/DefaultRingtonePolicy.kt) | `com.example.callog.data.telecom` | Evaluates the 5-tier resolution priority (Person &rarr; CRM Priority &rarr; Lead Status &rarr; Customer &rarr; System Default). |
| **Audio Persistence Repository** | [`RingtoneRepositoryImpl`](file:///D:/repo/app/src/main/java/com/example/callog/data/repository/RingtoneRepositoryImpl.kt) | `com.example.callog.data.repository` | Atomically imports external audio tracks into `filesDir/ringtones/`, validates headers, persists prefs, and heals corrupted files. |
| **Settings UI** | [`CrmRingtoneSettingsScreen`](file:///D:/repo/app/src/main/java/com/example/callog/presentation/screens/settings/CrmRingtoneSettingsScreen.kt) | `com.example.callog.presentation.screens.settings` | Compose Material 3 screen allowing users to pick, preview, and reset ringtones for Hot, Warm, Cold, Customer, and Default targets. |

---

## 3. Step-by-Step Lifecycle Walkthrough

### 3.1. Setting a Custom Ringtone in the UI
1. The user navigates to **Settings &rarr; Ringtone Policy** (`CrmRingtoneSettingsScreen`).
2. Tapping a lead tier (e.g. **Hot Lead**) fires an intent:
   ```kotlin
   val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
       putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
       putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
       putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
       putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Hot Lead Ringtone")
       putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri ?: defaultUri)
   }
   ```
3. When the user selects an audio file, `ActivityResultLauncher` catches `EXTRA_RINGTONE_PICKED_URI`.
4. `ringtoneRepository.importRingtoneForLeadHot(chosenUri)`:
   - Streams the chosen URI into a temporary file: `filesDir/ringtones/ringtone_lead_hot_tmp_<timestamp>.tmp`.
   - Validates that bytes were copied and passes `MediaMetadataRetriever` verification.
   - Atomically replaces `filesDir/ringtones/ringtone_lead_hot.audio`.
   - Stores `file:///data/user/0/com.example.callog/files/ringtones/ringtone_lead_hot.audio` in `SharedPreferences` under key `ringtone_lead_hot`.

### 3.2. Background Export & Contact Sync (Pre-Call)
During scheduled CRM sync or lead updates (`ContactRingtoneSyncManager`):
1. `resolveLeadStatus()` maps the lead stage/status ID to `LeadStatus.HOT`.
2. `mediaStoreRingtoneExporter.getExportedRingtoneUri(LeadStatus.HOT)`:
   - Queries `MediaStore.Audio.Media` for `"Callog CRM Hot"`.
   - If not found, exports the file to `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` under `Ringtones/Callog/callog_lead_hot.mp3` with `IS_RINGTONE = 1`.
   - Obtains a public URI: `content://media/external/audio/media/1000000452`.
3. Sets `ContactsContract.Contacts.CUSTOM_RINGTONE` to this public URI for all contacts matching this lead's phone number.

### 3.3. Incoming Call Arrival
1. Android Telecom invokes `CallService.onCallAdded(call)`.
2. `CallSessionManager.onTelecomCallAdded()` updates session state to `CallState.RINGING`.
3. `CallSessionManager.handleStateChange(CallState.RINGING)` checks:
   ```kotlin
   val inDevice = contactRingtoneSyncManager?.isContactAlreadyInDevice(session.phoneNumber) ?: false
   ```
4. **Decision Branch**:
   - **Branch A (Already in Device Contacts)**:
     - Logged as `RINGTONE_NATIVE_DELEGATED`.
     - In-app playback is **bypassed**.
     - Samsung InCallUI reads `ContactsContract.Contacts.CUSTOM_RINGTONE` and plays the exported tone natively.
     - **Result**: Zero audio race, zero dual-ringing.
   - **Branch B (Not in Device Contacts / Unsynced Lead)**:
     - `CallSessionManager.resolvePersonAndLead()` runs with an 800ms timeout.
     - If the number matches a CRM `Person` or `LeadMasterEntity`:
       - `ringtoneController.startRingtone(callId, person, lead)` is called with an atomic generation token.
       - Audio focus is requested (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`).
       - Lead-aware vibration starts (e.g. double-pulse for HOT).
       - `MediaPlayer` plays the configured tone on a loop.
       - Asynchronously, `insertOrUpdateSingleLead(leadMasterEntity)` is scheduled so the next call rings natively via Branch A.
     - If the number is an unknown non-CRM caller:
       - In-app playback is skipped (`UNKNOWN_CALLER_NON_CRM`) so Samsung default ringer sounds without interference.

### 3.4. User Silencing or Call Termination
- **Hardware Button Silencing**:
  `InCallService.onSilenceRinger()` &rarr; `ringtoneController.silenceRinger()`:
  Immediately stops `MediaPlayer` and cancels vibrator.
- **Call Answered / Rejected / Disconnected**:
  `CallService.onCallRemoved()` or state change to `ACTIVE` / `DISCONNECTED`:
  `ringtoneController.stopRingtone()` releases `MediaPlayer`, cancels vibration, and calls `abandonAudioFocus()`.

---

## 4. Configuration Reference & Shared Preferences Keys

All ringtone preferences are stored in private SharedPreferences file:
`"ringtone_settings_prefs"`

| Key | Description | Example Stored Value |
|---|---|---|
| `ringtone_lead_hot` | Internal URI for HOT leads | `file:///data/user/0/com.example.callog/files/ringtones/ringtone_lead_hot.audio` |
| `ringtone_title_ringtone_lead_hot` | Display title for UI | `"Morning Glory (Custom)"` |
| `ringtone_lead_warm` | Internal URI for WARM leads | `file:///data/user/0/com.example.callog/files/ringtones/ringtone_lead_warm.audio` |
| `ringtone_title_ringtone_lead_warm` | Display title for UI | `"Soft Chimes"` |
| `ringtone_lead_cold` | Internal URI for COLD leads | `file:///data/user/0/com.example.callog/files/ringtones/ringtone_lead_cold.audio` |
| `ringtone_title_ringtone_lead_cold` | Display title for UI | `"Minimal Beat"` |
| `ringtone_lead_new` | Internal URI for NEW leads | `file:///data/user/0/com.example.callog/files/ringtones/ringtone_lead_new.audio` |
| `ringtone_customer_type` | Internal URI for active customers | `file:///data/user/0/com.example.callog/files/ringtones/ringtone_customer_type.audio` |
| `ringtone_title_ringtone_customer_type` | Display title for UI | `"Executive Suite"` |
| `ringtone_crm_priority` | Tone for urgent priority leads | `file:///data/user/0/com.example.callog/files/ringtones/ringtone_crm_priority.audio` |
| `ringtone_person_<personId>` | Person-specific override | `file:///data/user/0/com.example.callog/files/ringtones/ringtone_person_P001.audio` |

---

## 5. Troubleshooting & OEM Gotchas

### 5.1. Dual-Ringing on Samsung Devices
- **Symptom**: You hear two different ringtones playing at once during an incoming call.
- **Cause**: In-app `MediaPlayer` was started even though Samsung's native InCallUI is also ringing.
- **Fix**: Check `DeveloperLogger` for `RINGTONE_NATIVE_DELEGATED`. Ensure `READ_CONTACTS` is granted so `isContactAlreadyInDevice()` can detect the number in `PhoneLookup`. For unsynced contacts, verify `UNKNOWN_CALLER_NON_CRM` suppresses playback.

### 5.2. `SecurityException` in Samsung InCallUI
- **Symptom**: Samsung dialer crashes or logs `Permission Denial: reading com.example.callog ...`.
- **Cause**: A private `file://` URI was written into `ContactsContract.Contacts.CUSTOM_RINGTONE`.
- **Fix**: Never write private app file URIs directly to `ContactsContract`. Always pass through `MediaStoreRingtoneExporter.getExportedRingtoneUri()`, which writes a public `content://media/external/audio/media/<id>` URI.

### 5.3. Silent Mode Ignored
- **Symptom**: Ringtone sounds when device is set to silent or Do Not Disturb.
- **Cause**: Missing `audioManager.ringerMode` check.
- **Fix**: `RingtoneController.startRingtone()` checks `ringerMode != RINGER_MODE_SILENT` before requesting focus and creating `MediaPlayer`.

### 5.4. Ringtone Continues Playing After Answering
- **Symptom**: Ringtone persists into the active phone conversation.
- **Cause**: Samsung lockscreen answered call before InCallService callback arrived, or lifecycle teardown missed `CallState.ACTIVE`.
- **Fix**:
  1. `RingtoneController.startRingtone()` includes a hardware kill-switch checking `audioManager.mode == MODE_IN_CALL || audioManager.mode == MODE_IN_COMMUNICATION`.
  2. `CallSessionManager.handleStateChange(CallState.ACTIVE)` calls `ringtoneController.stopRingtone()`.

---

## 6. Testing & Quality Assurance

### 6.1. Running Ringtone Unit Tests
To execute all custom ringtone tests via Gradle:
```bash
./gradlew testDebugUnitTest --tests "com.example.callog.telecom.*"
```

### 6.2. Test Suites Overview

1. [`RingtonePersistenceAndLifecycleTest.kt`](file:///D:/repo/app/src/test/java/com/example/callog/telecom/RingtonePersistenceAndLifecycleTest.kt):
   - `testImportAudioFileCopiesToInternalStorageAndPersists`: Verifies byte copying into `filesDir/ringtones/` and URI persistence.
   - `testImportFailurePreservesPreviouslyConfiguredRingtone`: Verifies that a corrupt 0-byte file aborts import without destroying existing tones.
   - `testResetToDefaultCleansUpInternalFile`: Verifies that clearing ringtones removes physical files from disk.
   - `testCorruptedOrDeletedInternalFileCleansPrefAndFallsBack`: Verifies self-healing fallback when audio files are deleted on disk.
   - `testRingtoneControllerIdempotentCalls`: Verifies `startRingtone()` and `stopRingtone()` idempotency.

2. [`CallRingtoneManagerTest.kt`](file:///D:/repo/app/src/test/java/com/example/callog/telecom/CallRingtoneManagerTest.kt):
   - Priority 1: Person-specific ringtone overrides all other lead rules.
   - Priority 2: CRM Priority (`URGENT` / `HIGH`) overrides lead status.
   - Priority 3: Lead Status (`HOT` vs `WARM`).
   - Priority 4: Customer identification via company name or notes tag.
   - Priority 5: Default ringtone fallback.
   - Clean stop & state reset idempotency.

3. [`ContactRingtoneSyncTest.kt`](file:///D:/repo/app/src/test/java/com/example/callog/telecom/ContactRingtoneSyncTest.kt):
   - Lead status foreign key resolution.
   - MediaStore exporter default fallbacks.
   - Unknown numbers that match a CRM lead trigger lead-specific ringtones.
   - Unknown callers who are NOT CRM leads suppress in-app audio to prevent Samsung dual-ringing.
