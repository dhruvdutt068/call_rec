# Post-Call Audio Recording Pipeline Developer Guide

## 1. Quick Overview

This guide serves as a practical developer runbook for understanding, modifying, extending, and debugging the post-call audio recording subsystem in **Callog (AllSet CRM)**.

---

## 2. Key Classes & Package Map

| Role | Class / File | Package | Responsibilities |
|---|---|---|---|
| **Post-Call Disconnect Coordinator** | [`CallSessionManager`](file:///D:/repo/app/src/main/java/com/example/callog/domain/service/CallSessionManager.kt) | `com.example.callog.domain.service` | Orchestrates staggered 3-pass OEM scanning (+1.5s, +3.5s, +6.5s) on telecom disconnect. |
| **Real-Time Storage Observer** | [`RecordingMediaObserver`](file:///D:/repo/app/src/main/java/com/example/callog/data/provider/RecordingMediaObserver.kt) | `com.example.callog.data.provider` | Debounces MediaStore write events (1200ms) to detect external OEM recorder files as they are flushed. |
| **Telephony Fallback Receiver** | [`CallReceiver`](file:///D:/repo/app/src/main/java/com/example/callog/data/receiver/CallReceiver.kt) | `com.example.callog.data.receiver` | Catches telephony `EXTRA_STATE_IDLE` broadcast and triggers a delayed background worker. |
| **In-App Recording Controller** | [`AndroidCallRecordingController`](file:///D:/repo/app/src/main/java/com/example/callog/data/telecom/AndroidCallRecordingController.kt) | `com.example.callog.data.telecom` | Manages live in-app recording with minimum duration protection and hardware stop crash prevention. |
| **Recording Storage Finalizer** | [`RecordingStorageManager`](file:///D:/repo/app/src/main/java/com/example/callog/data/telecom/RecordingStorageManager.kt) | `com.example.callog.data.telecom` | Finalizes recorded files into `Recordings/Callog/` and indexes them via Android MediaStore. |
| **Multi-Directory Scanner** | [`RecordingScanner`](file:///D:/repo/app/src/main/java/com/example/callog/data/provider/RecordingScanner.kt) | `com.example.callog.data.provider` | Scans dedicated, OEM, and MediaStore storage paths for recording files. |
| **OEM Regex Parser Chain** | [`RecordingParser.kt`](file:///D:/repo/app/src/main/java/com/example/callog/data/provider/RecordingParser.kt) | `com.example.callog.data.provider` | Parses phone, contact name, timestamp, and duration across Samsung, Xiaomi, Pixel, OnePlus, etc. |
| **Heuristic Matching Engine** | [`RecordingCorrelationMatcher`](file:///D:/repo/app/src/main/java/com/example/callog/domain/service/RecordingCorrelationMatcher.kt) | `com.example.callog.domain.service` | Evaluates weighted scoring matrix (Phone +0.40, Time +0.30, Duration +0.20, Order +0.10). |
| **Recording Repository** | [`RecordingRepositoryImpl`](file:///D:/repo/app/src/main/java/com/example/callog/data/repository/RecordingRepositoryImpl.kt) | `com.example.callog.data.repository` | Coordinates scanning, Room persistence, and triggers WorkManager upload enqueuing. |
| **Cloud Storage Upload Service** | [`UploadServiceImpl`](file:///D:/repo/app/src/main/java/com/example/callog/data/service/UploadServiceImpl.kt) | `com.example.callog.data.service` | Formats sanitized remote paths (`call_{id}_{name}_{number}.ext`) and streams audio to Firebase Cloud Storage. |
| **Background Upload Worker** | [`UploadRecordingWorker`](file:///D:/repo/app/src/main/java/com/example/callog/data/worker/UploadRecordingWorker.kt) | `com.example.callog.data.worker` | WorkManager worker executing uploads with `NetworkType.CONNECTED` constraints and exponential retries. |
| **Call Card UI Component** | [`CallCard`](file:///D:/repo/app/src/main/java/com/example/callog/presentation/components/CommonComponents.kt) | `com.example.callog.presentation.components` | Displays inline Play/Pause button with active playing state indicator in call logs and dashboard. |
| **Recordings Vault Screen** | [`RecordingManagerScreen`](file:///D:/repo/app/src/main/java/com/example/callog/presentation/screens/recordings/RecordingManagerScreen.kt) | `com.example.callog.presentation.screens.recordings` | Lists grouped recordings with inline Play/Pause controls, re-scan actions, and manual link picker. |

---

## 3. Step-by-Step Lifecycle Walkthrough

### 3.1. When a Call Disconnects
1. `InCallService` receives `Call.Callback.onStateChanged(Call.STATE_DISCONNECTED)`.
2. `CallSessionManager.handleStateChange(CallState.DISCONNECTED)` executes:
   - If in-app recording was running: Calls `controller.stop()`, verifies duration $\ge$ 800ms, and finalizes to `Recordings/Callog/Callog_<Caller>_<Date>.m4a`.
   - Fires `triggerPostCallRecordingScans(callId)`:
     - Launches 3 coroutines delayed by 1500ms, 3500ms, and 6500ms respectively.
     - Each pass executes `recordingRepository.scanRecordings()`.
3. Meanwhile, `RecordingMediaObserver` listens for `MediaStore.Audio.Media` insert/update events and schedules a 1200ms debounced reconciliation scan.

### 3.2. Scanning and Regex Parsing
1. `RecordingScanner.scanRecordings()` queries:
   - Dedicated path: `/Recordings/Callog/`
   - Custom user path (if configured)
   - OEM paths: `/Recordings/Call/`, `/MIUI/sound_recorder/call_rec/`, `/Recordings/Call Recordings/`
   - Android MediaStore database
2. Files are passed to `RecordingParserChain`:
   - `SamsungCallRecordingParser`: parses `Call recording <phone/name>_<YYMMDD>_<HHMMSS>`
   - `SamsungCallParser`: parses `Call <phone/name>_<YYMMDD>_<HHMMSS>`
   - `XiaomiCallParser`: parses `<phone>(<contact>)_<YYYYMMDDHHMMSS>`
   - `PixelCallParser`: parses `Call_<phone>_<YYYYMMDD>_<HHMMSS>`
   - `CallogNativeParser`: parses `Callog_<phone/name>_<YYYYMMDD_HHMMSS>`
3. Any 0-byte or corrupted temporary file is discarded.

### 3.3. Correlation & Database Linking
1. `RecordingCorrelationMatcher.matchRecordingToCall()` evaluates unmatched calls against the parsed metadata.
2. Weighted scoring calculates:
   - Phone match: $+0.40$ (Hard reject if phone numbers mismatch).
   - Proximity to disconnect: $+0.30$ (within $\pm 15$ seconds; falls off to $\pm 60$ seconds).
   - Audio vs call duration tolerance: $+0.20$ (within $\pm 4$ seconds; falls off to $\pm 15$ seconds).
   - Chronological order: $+0.10$ (file timestamp $\ge$ call start time).
3. If top score $\ge 0.70$ and difference from runner-up $\ge 0.10$:
   - Marked as `MatchStatus.MATCHED`.
   - `CallEntity.recordingPath` is populated with `recording.filePath`.
   - `enqueueUploadWork(callId)` triggers `UploadRecordingWorker`.

### 3.4. Cloud Upload & Storage Path Formatting
1. `UploadRecordingWorker` calls `UploadServiceImpl.uploadRecording(call, file)`.
2. `UploadServiceImpl.generateRemoteRecordingPath(normalizedDevicePhone, call, extension)` constructs:
   `users/{normalizedDevicePhone}/recordings/{year}/{month}/{day}/call_{callId}_{cleanCallerName}_{cleanNumber}.{extension}`
   - `cleanCallerName`: sanitized to alphanumeric, underscores, and hyphens (or `"Unknown"`).
   - `cleanNumber`: normalized digits (or `"UnknownNumber"`).
3. The audio file is streamed to Firebase Cloud Storage.
4. On success, `recordingUrl` and `recordingCloudPath` are updated in Room and synced to Firestore.

### 3.5. In-App Playback
1. On `CallLogsScreen` or `DashboardScreen`:
   - Calls with recordings display an inline Play/Pause button on `CallCard`.
   - Tapping Play loads the local audio file into `MediaPlayer`, starts playback, and tints the icon teal/progress amber.
   - If another recording is clicked, the previous player is released and new playback begins immediately.
2. On `RecordingManagerScreen`:
   - Each `RecordingChildItem` displays an inline Play/Pause button to quickly preview recordings from the vault.
3. On `CallDetailsScreen`:
   - Full scrubber, waveform visualization, and playback speed toggles (1.0x, 1.5x, 2.0x) are available.

---

## 4. How-To Developer Tasks

### 4.1. Adding a New OEM Regex Parser
To support a new phone manufacturer (e.g. Vivo/iQOO or Motorola):

1. Open [`RecordingParser.kt`](file:///D:/repo/app/src/main/java/com/example/callog/data/provider/RecordingParser.kt).
2. Create a new parser class implementing `RecordingParser`:
   ```kotlin
   class VivoCallRecordingParser : RecordingParser {
       private val regex = Regex("""^Record_([0-9+]+)_(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})(\d{2})\.(m4a|mp3)$""", RegexOption.IGNORE_CASE)

       override fun parse(fileName: String, lastModified: Long, duration: Long): ParsedRecording? {
           val match = regex.find(fileName) ?: return null
           val phone = match.groupValues[1]
           // Parse calendar date & time
           val timestamp = parseTimestamp(match)
           return ParsedRecording(
               phone = phone,
               contactName = null,
               timestamp = timestamp,
               duration = duration
           )
       }
   }
   ```
3. Register the new parser in `RecordingParserChain`:
   ```kotlin
   val parsers = listOf(
       VivoCallRecordingParser(),
       SamsungCallRecordingParser(),
       // ...
   )
   ```

### 4.2. Testing the Upload Remote Path Helper
To verify remote storage path formatting and sanitization:
Run `StorageUploadTest`:
```bash
./gradlew testDevDebugUnitTest --tests "com.example.callog.StorageUploadTest"
```

---

## 5. Troubleshooting & Diagnostics

### Problem: Recordings on Samsung or Xiaomi Are Not Matched
1. Open **Recording Diagnostics Screen** in the app drawer:
   `RecordingDiagnosticsScreen` displays real-time parser execution logs.
2. Check if the directory is monitored:
   - For Samsung: `/Recordings/Call/`
   - For Xiaomi: `/MIUI/sound_recorder/call_rec/`
3. Verify permissions:
   - Android 13+ requires `READ_MEDIA_AUDIO`.
   - Android 10-12 requires `READ_EXTERNAL_STORAGE`.
   - On Android 11+ OEM devices, ensure the user has granted All Files Access if recordings are saved in non-standard public roots.

### Problem: Audio Collision During In-App Playback
- Ensure playback is mediated by `activePlayingPath` state in the Compose screen so starting any audio track immediately pauses and releases the preceding one.
