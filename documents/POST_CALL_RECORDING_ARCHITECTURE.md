# Post-Call Audio Recording Pipeline Architecture Specification

## 1. Executive Summary & Problem Statement

In enterprise telecommunications and customer relationship management (CRM), capturing high-fidelity audio recordings of customer conversations is essential for compliance, dispute resolution, quality assurance, and automated speech-to-text intelligence.

However, implementing reliable call recording and post-call synchronization on modern Android presents steep architectural challenges:

1. **Android Platform Audio Restrictions (Android 10+)**:
   Since Android 10 (API 29) and Android 11+ restrictions, standard non-system applications cannot record two-way in-call audio directly via standard APIs unless configured as an Accessibility Service or utilizing specific OEM audio hooks. Consequently, users frequently rely on **OEM native system call recorders** built into devices by **Samsung (One UI)**, **Xiaomi (HyperOS / MIUI)**, **OnePlus (OxygenOS)**, and **Google Pixel**.
2. **Asynchronous OEM Disk Flushing**:
   OEM phone dialers write and flush audio files to disk asynchronously after a call ends. Some devices (Samsung) flush MP3/M4A tags within 1.5 seconds, while others (Xiaomi/MIUI or Transsion) may delay flushing audio headers for 3 to 6 seconds or until the MediaScanner processes the file. Single-shot immediate scanning results in missed files or 0-byte corrupted attachments.
3. **Correlation Under Imperfect Metadata**:
   OEM recorders store audio filenames with divergent conventions (e.g., `Call recording 919876543210_261004_153000.m4a` on Samsung vs `+919876543210(John Doe)_20261004153000.mp3` on Xiaomi). Contacts may be saved with nicknames, phone numbers formatted with or without country codes, or call durations slightly mismatching due to ring time vs talk time.
4. **Cloud Storage Scalability & Descriptive Object Keys**:
   Recordings must be uploaded reliably in the background without draining the battery. Remote storage object keys must be structured hierarchically and descriptively, incorporating user identity, call date, unique call ID, caller name, and contact number for seamless downstream auditing.
5. **Immediate Friction-Free In-App Playback**:
   Users should not have to navigate deep into detail sub-screens or modals just to review what was said on a call. Both call log cards and recording vault items must offer responsive, inline Play/Pause playback controls.

Callog / AllSet CRM solves these challenges through a **6-Stage Resilient Post-Call Recording Pipeline**.

---

## 2. High-Level System Architecture

```text
                                  ┌─────────────────────────────────┐
                                  │       Call Disconnection        │
                                  │   (Telecom / Telephony / App)   │
                                  └────────────────┬────────────────┘
                                                   │
                         ┌─────────────────────────┴─────────────────────────┐
                         ▼                                                   ▼
        ┌───────────────────────────────────┐               ┌───────────────────────────────────┐
        │   CHANNEL 1: IN-APP RECORDER      │               │   CHANNELS 2-4: OEM SCANNERS      │
        │   (AndroidCallRecordingController)│               │   (CallSessionManager & Observers)│
        ├───────────────────────────────────┤               ├───────────────────────────────────┤
        │ • Enforce >= 800ms duration       │               │ • Pass 1: Disconnect + 1.5s       │
        │ • MediaRecorder.stop() & release  │               │ • Pass 2: Disconnect + 3.5s       │
        │ • Finalize in Recordings/Callog/  │               │ • Pass 3: Disconnect + 6.5s       │
        │ • Notify MediaStore indexer       │               │ • MediaStore ContentObserver      │
        └─────────────────┬─────────────────┘               │ • Telephony IDLE fallback worker  │
                          │                                 └─────────────────┬─────────────────┘
                          │                                                   │
                          │        ┌──────────────────────────────────────────┘
                          ▼        ▼
        ┌───────────────────────────────────────────────────────────────────┐
        │              STAGE 2: DISCOVERY & MULTI-OEM PARSING               │
        │                     (RecordingScanner.kt)                         │
        ├───────────────────────────────────────────────────────────────────┤
        │ • Scan Dedicated Callog Path & User Custom Path                   │
        │ • Scan OEM Roots (/Recordings/Call, /MIUI/sound_recorder, etc.)   │
        │ • Query MediaStore.Audio.Media provider                           │
        │ • Regex Parsing Chain (Samsung, Xiaomi, Pixel, OnePlus, Native)   │
        └──────────────────────────────────┬────────────────────────────────┘
                                           │
                                           ▼
        ┌───────────────────────────────────────────────────────────────────┐
        │          STAGE 3: MULTI-FACTOR HEURISTIC CORRELATION              │
        │               (RecordingCorrelationMatcher.kt)                    │
        ├───────────────────────────────────────────────────────────────────┤
        │ • Phone Number Normalization Match (+0.40 score)                  │
        │ • Disconnect Timestamp Proximity (+0.30 score, +/- 15s to 60s)    │
        │ • Audio Duration vs Call Duration Tolerance (+0.20 score)         │
        │ • Temporal Ordering Check: File Time >= Call Start (+0.10 score)  │
        │ • Threshold: Score >= 0.70 -> MATCHED                             │
        │ • Score Delta < 0.10 between candidates -> AMBIGUOUS              │
        └──────────────────────────────────┬────────────────────────────────┘
                                           │
                                           ▼
        ┌───────────────────────────────────────────────────────────────────┐
        │          STAGE 4: ROOM DB PERSISTENCE & LOCAL LINKING             │
        │                   (RecordingDao & CallDao)                        │
        ├───────────────────────────────────────────────────────────────────┤
        │ • Upsert RecordingEntity (matchStatus=MATCHED, upload=PENDING)    │
        │ • Update CallEntity (recordingPath & recordingLocalPath linked)   │
        │ • Insert audit trail in recording_logs                            │
        └──────────────────────────────────┬────────────────────────────────┘
                                           │
                                           ▼
        ┌───────────────────────────────────────────────────────────────────┐
        │             STAGE 5: WORKMANAGER CLOUD STORAGE UPLOAD             │
        │          (UploadRecordingWorker & UploadServiceImpl)              │
        ├───────────────────────────────────────────────────────────────────┤
        │ • Enqueued with NetworkType.CONNECTED constraint                  │
        │ • Cloud Storage Object Path Formatted:                            │
        │   users/{device}/recordings/{Y}/{M}/{D}/call_{id}_{name}_{num}.ext│
        │ • Sanitize caller name and phone number for Cloud Storage keys    │
        │ • Upload file -> Retrieve Download URL -> Update Firestore/Room   │
        │ • Exponential backoff retry on transient network errors           │
        └──────────────────────────────────┬────────────────────────────────┘
                                           │
                                           ▼
        ┌───────────────────────────────────────────────────────────────────┐
        │          STAGE 6: IN-APP PLAYBACK & USER INTERFACE                │
        │     (CommonComponents.kt CallCard & RecordingManagerScreen.kt)    │
        ├───────────────────────────────────────────────────────────────────┤
        │ • Inline Play/Pause IconButton on CallCard in Call Logs screen    │
        │ • Inline Play/Pause IconButton on RecordingChildItem in Vault     │
        │ • Active playing status indicator (tint & animated state)         │
        │ • Single active audio player (toggling one pauses any other)      │
        │ • Full scrubbing waveform & speed controls in CallDetailsScreen   │
        └───────────────────────────────────────────────────────────────────┘
```

---

## 3. Component Deep Dive

### 3.1. Stage 1: Disconnect & Multi-Channel Triggers

Post-call recording reconciliation is triggered via four independent, complementary mechanisms:

1. **Staggered 3-Pass Disconnect Scans** ([`CallSessionManager.kt`](file:///D:/repo/app/src/main/java/com/example/callog/domain/service/CallSessionManager.kt)):
   When `CallService` receives `CallState.DISCONNECTED`, it launches three staggered background scans:
   - **Pass 1 (+1.5s)**: Captures immediate flushes on Samsung and Pixel devices.
   - **Pass 2 (+3.5s)**: Standard flush window on most devices.
   - **Pass 3 (+6.5s)**: Slower OEM media indexers and Transsion/Infinix background tag flushes.
2. **ContentObserver Real-Time Listener** ([`RecordingMediaObserver.kt`](file:///D:/repo/app/src/main/java/com/example/callog/data/provider/RecordingMediaObserver.kt)):
   Registered on `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI`. Whenever external recorder apps write to public storage, a 1200ms debounced event fires to reconcile unmatched calls.
3. **Telephony IDLE Fallback** ([`CallReceiver.kt`](file:///D:/repo/app/src/main/java/com/example/callog/data/receiver/CallReceiver.kt)):
   Listens for Android's `ACTION_PHONE_STATE_CHANGED` with `EXTRA_STATE_IDLE` to catch calls handled outside the foreground in-call screen.
4. **In-App Recorder Finalizer** ([`AndroidCallRecordingController.kt`](file:///D:/repo/app/src/main/java/com/example/callog/data/telecom/AndroidCallRecordingController.kt)):
   Enforces a minimum duration of 800ms before calling `MediaRecorder.stop()` to eliminate `-1007` hardware stop crashes, moves files to `Recordings/Callog/`, and invokes `MediaScannerConnection`.

---

### 3.2. Stage 2: Discovery & Multi-OEM Regex Parsing

Implemented in [`RecordingScanner.kt`](file:///D:/repo/app/src/main/java/com/example/callog/data/provider/RecordingScanner.kt) and [`RecordingParser.kt`](file:///D:/repo/app/src/main/java/com/example/callog/data/provider/RecordingParser.kt).

#### Monitored Storage Locations
- App-dedicated storage: `/storage/emulated/0/Recordings/Callog/`
- User-configured directory (custom setting)
- Standard OEM folders:
  - Samsung One UI: `/storage/emulated/0/Recordings/Call/`
  - Xiaomi MIUI / HyperOS: `/storage/emulated/0/MIUI/sound_recorder/call_rec/`
  - OnePlus / ColorOS: `/storage/emulated/0/Recordings/Call Recordings/`
- `MediaStore.Audio.Media` database query filtered by `IS_RECORDING == 1` or MIME type audio.

#### Multi-OEM Parser Chain
The parser chain extracts contact name, phone number, timestamp, and duration:

| Parser | Format Pattern | Example |
| :--- | :--- | :--- |
| `SamsungCallRecordingParser` | `Call recording <phone/name>_<YYMMDD>_<HHMMSS>` | `Call recording 919876543210_261004_153000.m4a` |
| `SamsungCallParser` | `Call <phone/name>_<YYMMDD>_<HHMMSS>` | `Call John Doe_261004_153000.m4a` |
| `XiaomiCallParser` | `<phone>(<contact>)_<YYYYMMDDHHMMSS>` | `919876543210(John Doe)_20261004153000.mp3` |
| `PixelCallParser` | `Call_<phone>_<YYYYMMDD>_<HHMMSS>` | `Call_919876543210_20261004_153000.m4a` |
| `CallogNativeParser` | `Callog_<phone/name>_<YYYYMMDD_HHMMSS>` | `Callog_John_Doe_20261004_153000.m4a` |

---

### 3.3. Stage 3: Multi-Factor Heuristic Correlation

Implemented in [`RecordingCorrelationMatcher.kt`](file:///D:/repo/app/src/main/java/com/example/callog/domain/service/RecordingCorrelationMatcher.kt).

The matching engine scores candidate calls against the recording:

$$\text{Total Score} = S_{\text{phone}} + S_{\text{time}} + S_{\text{duration}} + S_{\text{order}}$$

1. **Normalized Phone Match ($S_{\text{phone}} = 0.40$)**:
   - Matching normalized phone number yields $+0.40$.
   - Contradictory phone numbers cause immediate rejection ($S = 0$).
2. **Timestamp Proximity ($S_{\text{time}} = 0.30$)**:
   - Compares recording creation timestamp with call end timestamp.
   - Within $\pm 15$ seconds: Full $+0.30$.
   - Linear falloff between $\pm 15\text{s}$ and $\pm 60\text{s}$. Beyond $60\text{s}$: $0$.
3. **Audio vs Call Duration Tolerance ($S_{\text{duration}} = 0.20$)**:
   - Difference within $\pm 4$ seconds: Full $+0.20$.
   - Linear falloff between $\pm 4\text{s}$ and $\pm 15\text{s}$.
4. **Temporal Ordering ($S_{\text{order}} = 0.10$)**:
   - $+0.10$ if recording creation time $\ge$ call start time.

#### Match Classification
- **$\text{Score} \ge 0.70$ (Unique)**: `MatchStatus.MATCHED`. Automatically linked to `CallEntity`.
- **Top 2 Candidates Score Delta $< 0.10$**: `MatchStatus.AMBIGUOUS`. Placed in review queue in Recordings Vault.
- **$\text{Score} < 0.70$**: `MatchStatus.UNMATCHED`. Preserved in DB with diagnostic reasoning.

---

### 3.4. Stage 4: Database Persistence & Linking

When matched:
1. `RecordingEntity` is saved to Room DB:
   - `matchedCallId = call.id`
   - `matchStatus = MatchStatus.MATCHED`
   - `uploadStatus = UploadStatus.PENDING`
2. `CallEntity` in `calls_research` is updated:
   - `recordingPath = rec.filePath`
   - `recordingLocalPath = rec.filePath`
   - `recordingUploadStatus = "PENDING"`
3. An audit record is logged into `recording_logs` for diagnostic inspection.

---

### 3.5. Stage 5: WorkManager Cloud Storage Upload & Object Naming

Implemented in [`UploadRecordingWorker.kt`](file:///D:/repo/app/src/main/java/com/example/callog/data/worker/UploadRecordingWorker.kt) and [`UploadServiceImpl.kt`](file:///D:/repo/app/src/main/java/com/example/callog/data/service/UploadServiceImpl.kt).

#### Remote Object Path Convention
Cloud Storage object keys follow a deterministic, sanitized path structure:

```
users/{normalizedDevicePhone}/recordings/{year}/{month}/{day}/call_{callId}_{cleanCallerName}_{cleanNumber}.{extension}
```

#### Path Generation & Sanitization Logic
In [`UploadServiceImpl.kt`](file:///D:/repo/app/src/main/java/com/example/callog/data/service/UploadServiceImpl.kt#L50):

```kotlin
fun generateRemoteRecordingPath(
    normalizedDevicePhone: String,
    call: CallEntity,
    extension: String
): String {
    val date = Date(call.timestamp)
    val year = SimpleDateFormat("yyyy", Locale.US).format(date)
    val month = SimpleDateFormat("MM", Locale.US).format(date)
    val day = SimpleDateFormat("dd", Locale.US).format(date)

    val rawCallerName = call.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown"
    val cleanCallerName = rawCallerName
        .replace(Regex("[^a-zA-Z0-9_-]"), "_")
        .replace(Regex("_+"), "_")
        .trim { it == '_' || it == '-' }
        .ifEmpty { "Unknown" }

    val rawNumber = FirestoreRepositoryImpl.normalizePhoneNumber(call.number)
    val cleanNumber = rawNumber.replace(Regex("[^0-9+]"), "").ifEmpty { "UnknownNumber" }

    val cleanExt = extension.ifEmpty { "mp3" }
    val remoteFileName = "call_${call.id}_${cleanCallerName}_${cleanNumber}.$cleanExt"
    return "users/$normalizedDevicePhone/recordings/$year/$month/$day/$remoteFileName"
}
```

- Spaces, slashes, and special characters are replaced with underscores to prevent Cloud Storage URL encoding breakage.
- Null or blank names fall back cleanly to `"Unknown"`.
- Clean phone numbers remove whitespace and formatting dashes while preserving standard international dial digits.

#### Upload Lifecycle & WorkManager Execution
1. Enqueued with `NetworkType.CONNECTED` constraints.
2. Streams audio to Firebase Cloud Storage.
3. Retrieves public download URL.
4. Updates `CallEntity`:
   - `recordingCloudPath = remotePath`
   - `recordingUrl = downloadUrl`
   - `recordingUploadStatus = "SUCCESS"`
   - `recordingUploadedAt = System.currentTimeMillis()`
5. Updates metadata on Firestore and Supabase.
6. Handles errors via `StorageErrorCategory`:
   - Transient network issues trigger `Result.retry()` with exponential backoff.
   - Non-retryable permission denials or missing files trigger `Result.failure()` to avoid loop storms.

---

### 3.6. Stage 6: In-App Audio Playback & UI Architecture

#### Inline Play/Pause Controls
To maximize operational efficiency, users can listen to recordings directly from call lists:

1. **`CallCard` in Call Logs & Dashboard** ([`CommonComponents.kt`](file:///D:/repo/app/src/main/java/com/example/callog/presentation/components/CommonComponents.kt#L302)):
   - When `call.recordingPath != null`:
     - Displays an interactive Play/Pause `IconButton` (`Icons.Default.PlayCircle` / `Icons.Default.PauseCircle`).
     - Toggles audio playback instantly using an in-memory `MediaPlayer`.
     - Highlights the active call with animated `CallogSemanticColors.SyncInProgress` tint on both the Play icon and the caller header Mic icon.
     - Automatically resets when playback finishes or when the user navigates away.
2. **`RecordingChildItem` in Recordings Vault** ([`RecordingManagerScreen.kt`](file:///D:/repo/app/src/main/java/com/example/callog/presentation/screens/recordings/RecordingManagerScreen.kt#L374)):
   - Renders an inline Play/Pause button on each item card.
   - Allows users to listen to matched, unmatched, or ambiguous recordings immediately without opening the details modal.
3. **`CallDetailsScreen` Media Player** ([`CallDetailsScreen.kt`](file:///D:/repo/app/src/main/java/com/example/callog/presentation/screens/details/CallDetailsScreen.kt)):
   - Dedicated audio player card with waveform scrubbing, playback speed adjustments (1.0x, 1.5x, 2.0x), elapsed duration display, and direct sharing actions.

---

## 4. Architectural Guarantees & Resilience

| Characteristic | Mechanism |
| :--- | :--- |
| **Zero Lost Recordings** | Staggered 3-pass scanning (+1.5s, +3.5s, +6.5s) + real-time ContentObserver + Telephony IDLE fallback worker. |
| **No Corrupted Audio Files** | 0-byte check filter + minimum 800ms duration check prior to `MediaRecorder.stop()`. |
| **Offline-First Preservation** | Local recording path remains untouched even if cloud upload fails or credentials expire. |
| **Deterministic Storage Keys** | Sanitized caller name and contact number included in object paths for instant file inspection. |
| **Single Active Audio Stream** | Playing any recording automatically pauses previously active playback to prevent sound overlap. |
| **Battery Efficient** | Uploads execute exclusively via WorkManager when device network is connected. |
