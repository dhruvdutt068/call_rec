# Call Log & Recording Vault (Callog)

A robust Android application designed to scan system call logs, match them with call recording files, and synchronize all metadata and audio assets to remote databases (Firebase Firestore, Firebase Cloud Storage, and Supabase).

---

## 🚀 Key Features

* **System Call Log Scanning**: Automatically reads incoming, outgoing, missed, and rejected calls from the Android `CallLog` provider.
* **Call Recording Matching**: Scans device audio files to associate specific recordings (e.g., `.m4a`, `.mp3`) with their corresponding call logs based on contact names, phone numbers, and time proximity.
* **Firebase Firestore Sync**: Uploads structured call metadata (caller identity, duration, timestamps, device model, and recording URL) to Google Cloud Firestore.
* **Firebase Cloud Storage Uploads**: Uploads audio recording files to secure storage folders structured by date and normalized device details.
* **Supabase Sales Integration**: Maps and synchronizes sales interaction logs to a remote Supabase Postgres database.
* **Background Sync Service**: Employs Android `WorkManager` with exponential backoff retries to automatically sync pending data and upload recordings in the background when connected to the internet.
* **Local Database Caching**: Uses Room SQLite DB to cache records, track favorite status, write notes, assign tags, and monitor upload progress.
* **SIM & Dual SIM Management**: Tracks and handles call routing and identification across multi-SIM hardware through dedicated SIM managers.

---

## 🛠️ Tech Stack & Architecture

* **Language**: Kotlin (Modern Android Development)
* **Dependency Injection**: Dagger Hilt
* **Database**: Room ORM (SQLite)
* **Background Processing**: WorkManager
* **Network & Remote Services**:
  * **Firebase**: Firestore (Metadata) & Cloud Storage (Audio assets)
  * **Supabase**: Postgrest Kotlin SDK (Sales calls logging)
* **UI**: Jetpack Compose (Dynamic configuration settings, recording managers, and dashboard view models)
* **Media**: Media3 ExoPlayer for audio playback
* **Image Loading**: Coil

---

## 📂 Project Structure

```text
app/src/main/java/com/example/callog/
│
├── core/                  # Core helpers, constants, and utilities
├── data/
│   ├── local/             # Room Database, DAOs, and Entities
│   ├── provider/          # CallLogProvider and RecordingScanner
│   ├── remote/            # Remote APIs (FirestoreService, SupabaseService)
│   ├── receiver/          # Broadcast receivers (CallReceiver)
│   └── repository/        # Repository implementations
│
├── di/                    # Dagger Hilt Dependency Injection modules
│
├── domain/
│   ├── model/             # Shared data models
│   ├── repository/        # Interface contracts for Repositories
│   └── usecase/           # Domain use cases orchestrating repository actions
│
├── presentation/
│   ├── screens/           # Jetpack Compose Screens
│   ├── theme/             # Material Design Styles and Themes
│   └── viewmodel/         # ViewModels managing UI state
│
└── sim/                   # SIM tracking, identification and management logic
```

---

## ⚙️ Setup & Configuration

### 1. Permissions Required
The app requires permissions to perform system reads and storage lookups. These are declared in `AndroidManifest.xml`:
* `READ_CALL_LOG` - Access call history.
* `READ_CONTACTS` - Match caller IDs with contact names.
* `READ_PHONE_STATE` - Monitor ongoing call state.
* `POST_NOTIFICATIONS` - Post foreground service and sync notifications (Android 13+).
* `READ_EXTERNAL_STORAGE` / `READ_MEDIA_AUDIO` - Access audio recordings.

*(Note: Depending on functionality, background network sync implicitly relies on Internet access provided by the system via Firebase/WorkManager.)*

### 2. Remote Configuration (Firebase & Supabase)
To establish server connections, configure details via the **Device Configuration** Settings screen inside the app:
* **Device Phone Number & Owner Name**: Used to attribute caller details and organize upload pathways.
* **Firebase Credentials**: Setup the required `google-services.json` or connect directly.
* **Supabase Client**: Configured to upsert records into your Postgres database.

---

## 🛠️ Build & Installation

Prerequisites:
* Android Studio (Ladybug or newer recommended)
* Android SDK (API Level 29+ / Android 10.0+)
* JDK 17
* Gradle 8.0+

### Gradle CLI Quickstart

1. **Build the Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```
2. **Run Unit Tests**:
   ```bash
   ./gradlew test
   ```
3. **Install on a Connected Device**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
