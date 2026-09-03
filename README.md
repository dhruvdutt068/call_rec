# Call Log & Recording Vault (Callog / AllSet CRM)

A high-performance Android application built using Kotlin and Jetpack Compose designed to scan system call logs, match them with call recording files, provide CRM workflows (Centralized Person Identity, Contacts, Tasks, Meetings, and Feedback), and synchronize all metadata and audio assets to remote backends (**Firebase Firestore**, **Firebase Cloud Storage**, and **Supabase PostgreSQL**).

---

## 🚀 Key Features

* **Centralized Contact & Person Identity Layer**: Treats device-local contacts as aliases resolving to a canonical `Person` entity. Supports multiple phone numbers per person, multi-device contact aliases, and phone number normalization.
* **Global CRM Search**: Real-time search across canonical display names, company names, phone numbers, and cross-device aliases with direct navigation to canonical contact profiles.
* **System Call Log Scanning**: Automatically reads incoming, outgoing, missed, and rejected calls from the Android `CallLog` provider.
* **Call Recording Matching**: Scans device audio files to associate specific recordings (`.m4a`, `.mp3`) with their corresponding call logs based on contact names, phone numbers, and time proximity.
* **Jetpack Navigation 3 CRM Architecture**: State-driven navigation system with multi-backstack state preservation, canonical `contactId`/`personId` routing, and conditional authentication.
* **Firebase Firestore Sync**: Uploads structured call metadata (caller identity, duration, timestamps, device model, and recording URL) to Google Cloud Firestore.
* **Firebase Cloud Storage Uploads**: Uploads audio recording files to secure storage folders structured by date and normalized device details.
* **Supabase Sales & Identity Integration**: Synchronizes canonical `people`, `phone_numbers`, `contact_aliases`, `devices`, and `sales_calls` to remote Supabase PostgreSQL tables.
* **Background Sync Service**: Employs Android `WorkManager` with exponential backoff retries to automatically sync pending data and upload recordings in the background when connected to the internet.
* **Local Database Caching (Room v17)**: Offline-first Room SQLite DB caching call records, canonical CRM people, phone numbers, device aliases, favorites, notes, tags, and sync status with `personId` indexing.
* **SIM & Dual SIM Management**: Tracks and handles call routing and identification across multi-SIM hardware through dedicated SIM managers.
* **Deep Linking**: Full support for `allset://` and `callog://` URIs for contacts (`allset://contact/{id}`), logs (`allset://logs`), search (`allset://contacts/search?q={query}`), tasks, and meetings.

---

## 🛠️ Tech Stack & Architecture

* **Language**: Kotlin 2.1.0 (Modern Android Development)
* **UI Framework**: Jetpack Compose with Material 3 (Compose BOM `2025.09.00`)
* **Navigation**: Jetpack Navigation 3 (State-first, Type-safe keys, Multi-backstack)
* **Dependency Injection**: Dagger Hilt 2.51.1
* **Database**: Room ORM 2.6.1 (SQLite Version 17 with non-destructive migrations)
* **Background Processing**: WorkManager
* **Network & Remote Services**:
  * **Firebase**: Firestore (Metadata) & Cloud Storage (Audio assets)
  * **Supabase**: Postgrest Kotlin SDK (Sales calls, Central Identity sync, Authoritative Person Resolution)
* **Media**: Media3 ExoPlayer for audio playback
* **Image Loading**: Coil

---

# 🏛️ Complete File Architecture & Module Breakdown

The codebase strictly follows **Clean Architecture + MVVM + Unidirectional Data Flow (UDF)**:

```text
callog/
│
├── 📁 Gradle & Configs               # Build configuration, Gradle wrapper, properties
├── 📁 AGENTS.md, DESIGN.md & README.md # Workspace rules, design system, project documentation
└── 📁 app/
    ├── 📁 src/main/AndroidManifest.xml# Permissions, receivers, deep-link intent filters
    └── 📁 src/main/java/com/example/callog/
        ├── CallVaultApp.kt           # Application class (@HiltAndroidApp), WorkManager setup
        ├── MainActivity.kt           # Single Activity, Intent handling, Compose Root
        ├── 📁 core/                  # Utilities, diagnostics, logging, connectivity, normalizers
        ├── 📁 data/                  # Data layer (Room DAOs, Entities, Providers, Workers, Remote)
        ├── 📁 di/                    # Dagger Hilt dependency injection modules
        ├── 📁 domain/                # Business logic, models, repository interfaces, use cases
        ├── 📁 presentation/          # Jetpack Compose UI, Navigation 3, ViewModels, Themes
        └── 📁 sim/                   # Dual-SIM detection, routing, and management
```

---

## 1. ⚙️ Root Configuration & Project Files

| File Name | Description & Code Details |
|---|---|
| `build.gradle.kts` | **Root Gradle Build Script**: Defines plugins applied across all submodules (Android Application `8.7.3`, Kotlin Android `2.1.0`, Dagger Hilt `2.51.1`, Kotlin Serialization `2.1.0`, KSP `2.1.0-1.0.29`). |
| `settings.gradle.kts` | **Gradle Settings**: Configures plugin management repositories (Google, MavenCentral, Gradle Plugin Portal) and includes the `:app` module. |
| `gradle.properties` | **JVM & Build Properties**: Allocates JVM heap memory (`-Xmx2048m`), enables AndroidX (`android.useAndroidX=true`), and configures Kotlin compilation optimizations. |
| `gradlew` / `gradlew.bat` | **Gradle Wrapper**: Unix/Bash and Windows batch executables ensuring reproducible Gradle runtime execution. |
| `AGENTS.md` | **Agent Guidelines & Architectural Rules**: Comprehensive rules covering Clean Architecture layers, Kotlin Coroutines dispatchers (`Dispatchers.IO` for DB/network, `Dispatchers.Main` for UI), Compose UDF patterns, Room migration rules, and build/test commands. |
| `DESIGN.md` | **Design System & Visual Identity**: Design tokens, color palettes, editorial typography scales, glassmorphic card standards, and UX tenets. |
| `README.md` | **Project Documentation**: High-level overview of features, setup instructions, architecture diagrams, and complete file breakdown. |
| `app/build.gradle.kts` | **App Module Build Script**: Configures `compileSdk = 35`, `minSdk = 26`, `targetSdk = 35`, Jetpack Compose BOM `2025.09.00`, Material 3, Hilt, Room `2.6.1`, Retrofit, Firebase Firestore/Storage, Supabase, and JUnit testing dependencies. |
| `app/src/main/AndroidManifest.xml` | **Android Manifest**: Declares runtime permissions (`READ_CALL_LOG`, `READ_CONTACTS`, `READ_PHONE_STATE`, `READ_MEDIA_AUDIO`, `INTERNET`), registers `CallVaultApp`, `MainActivity`, `CallReceiver` broadcast receiver, and registers deep-link `<intent-filter>` for `allset://` and `callog://` URI schemes. |

---

## 2. 🚀 Application Root & Core Layer (`core/`)

| File Name | Description & Code Details |
|---|---|
| `CallVaultApp.kt` | **Application Class**: Annotated with `@HiltAndroidApp`. Implements `Configuration.Provider` to inject `HiltWorkerFactory` for dependency injection in background WorkManager jobs (`SyncWorker`, `UploadRecordingWorker`). |
| `MainActivity.kt` | **Main ComponentActivity**: Annotated with `@AndroidEntryPoint`. Sets up Edge-to-Edge Compose display, intercepts incoming deep links (`Intent.ACTION_VIEW`), passes parsed URIs to Navigation 3 / AllSet hosts, and hosts `NavGraph`. |
| `core/config/SupabaseDefaults.kt` | **Supabase Constants**: Holds default Supabase endpoint URLs, authentication anon keys, table names (`people`, `phone_numbers`, `contact_aliases`, `devices`, `sales_calls`), and fallback configuration schemas. |
| `core/constants/Constants.kt` | **Global Constants**: Storage keys, shared preferences identifiers, channel notification IDs, sync interval timers, and audio MIME types. |
| `core/diagnostics/DeveloperLogger.kt` | **In-Memory Diagnostic Logger**: Thread-safe circular buffer logging recording matching heuristics, SIM detection events, and sync results for display in `SyncLogsScreen` and `RecordingDiagnosticsScreen`. |
| `core/extensions/Extensions.kt` | **Kotlin Utility Extensions**: Formatting epoch timestamps to readable dates, phone number normalization, and duration formatting (`MM:SS`). |
| `core/utils/PhoneNumberNormalizer.kt` | **Canonical Phone Normalizer**: Strips formatting, country codes, and non-numeric characters down to consistent 10-digit subscriber numbers for cross-device identity resolution. |
| `core/utils/ConnectivityService.kt` | **Network Monitor**: Uses `ConnectivityManager.NetworkCallback` to emit reactive `Flow<Boolean>` network status for offline-first sync triggering. |

---

## 3. 💾 Data Layer (`data/`)

### 3.1 Local Database (Room v16) — `data/local/`

| File Name | Description & Code Details |
|---|---|
| `local/database/CallVaultDatabase.kt` | **Room Database (v17)**: Manages database versioning, entities (`CallEntity`, `SalesCallEntity`, `RecordingEntity`, `SyncLogEntity`, `TracebackEntity`, `ReminderEntity`, `DeviceEntity`, `PersonEntity`, `PhoneNumberEntity`, `ContactAliasEntity`), and abstract DAO getters. |
| `local/database/Converters.kt` | **Room Type Converters**: Serializes/deserializes complex types (e.g. `List<String>`, `UploadStatus`, `MatchStatus`, Date objects) to/from SQLite compatible types. |
| `local/entity/DeviceEntity.kt` | **Device Entity**: Represents hardware device identities with unique `deviceIdentifier`, owner name, phone number, and last sync timestamp. |
| `local/entity/PersonEntity.kt` | **Canonical Person Entity**: Central CRM identity model (`id = "P..."`, `displayName`, `companyName`, `notes`, `syncStatus`). |
| `local/entity/PhoneNumberEntity.kt` | **Phone Number Entity**: Stores multiple phone numbers linked to a Person (`personId` FK with `CASCADE`), phone type, primary flag, and normalized number. |
| `local/entity/ContactAliasEntity.kt` | **Contact Alias Entity**: Captures device-specific contact names (`personId` & `deviceId` FKs with `CASCADE`, `androidContactId`, `aliasName`, `normalizedNumber`). |
| `local/entity/PersonWithDetails.kt` | **Composite Person Model**: Room `@Relation` combining a `PersonEntity` with all its `phone_numbers` and `contact_aliases`. |
| `local/entity/CallEntity.kt` | **Call Entity**: Primary table (`calls_research`) storing call logs (number, name, call type, timestamp, duration, SIM slot, notes, tags, isFavorite, recording path, indexed `personId`). |
| `local/entity/SalesCallEntity.kt` | **Sales Call Entity**: Structured CRM call records with salesperson info, buyer info, duration, timestamp, and linked `personId`. |
| `local/entity/RecordingEntity.kt` | **Recording Entity**: Discovered audio recording assets with file paths, durations, match confidence scores, and cloud upload status. |
| `local/entity/ReminderEntity.kt` | **Reminder Entity**: Scheduled call-back reminders and task deadlines with completion status. |
| `local/entity/SyncLogEntity.kt` | **Sync Log Entity**: Audit trail of sync execution runs, duration, success/failure counts, and error tracebacks. |
| `local/entity/TracebackEntity.kt` | **Diagnostic Traceback Entity**: Detailed error logs captured during audio scanning or network operations. |
| `local/dao/PersonDao.kt` | **Person DAO**: CRUD and reactive query methods for people, phones, aliases, devices, search queries, and sync states. |
| `local/dao/CallDao.kt` | **Call DAO**: CRUD operations, reactive queries for call logs, Person-specific call flows (`getCallsForPersonFlow`), and search queries. |
| `local/dao/SalesCallDao.kt` | **Sales Call DAO**: Queries and updates for CRM deal tracking and sales interaction records. |
| `local/dao/RecordingDao.kt` | **Recording DAO**: Queries for matched/unmatched audio assets and pending cloud upload queues. |
| `local/dao/ReminderDao.kt` | **Reminder DAO**: Queries for upcoming callback reminders and task alerts. |
| `local/dao/SyncLogDao.kt` | **Sync Log DAO**: Methods to append and query synchronization audit entries. |
| `local/dao/TracebackDao.kt` | **Traceback DAO**: Stores system tracebacks for developer inspection. |

### 3.2 Providers, Workers & Repositories — `data/`

| File Name | Description & Code Details |
|---|---|
| `provider/CallLogProvider.kt` | **Call Log Scanner**: Queries Android's `CallLog.Calls` content provider to extract raw call metadata across single and dual-SIM slots. |
| `provider/ContactsProvider.kt` | **Android Contacts Scanner**: Queries Android's `ContactsContract` content provider to match caller IDs with local contact names and photos. |
| `provider/RecordingScanner.kt` | **Audio Media Scanner**: Scans internal storage directories for audio recording files and uses fuzzy string matching and timestamp heuristics to associate recordings with call logs. |
| `receiver/CallReceiver.kt` | **Telephony Broadcast Receiver**: Listens for phone state changes (`EXTRA_STATE_RINGING`, `EXTRA_STATE_OFFHOOK`, `EXTRA_STATE_IDLE`) to trigger automatic post-call recording rescanning. |
| `remote/FirestoreService.kt` | **Firebase Firestore Client**: Uploads structured call metadata, creates date-partitioned collections, and manages document updates. |
| `remote/SupabaseService.kt` | **Supabase PostgREST Client**: Handles CRUD and batch sync operations for `sales_calls`, `people`, `phone_numbers`, `contact_aliases`, and `devices`. |
| `remote/model/SupabaseIdentityModels.kt` | **Supabase Identity DTOs**: Serialization data classes (`SupabasePerson`, `SupabasePhoneNumber`, `SupabaseContactAlias`, `SupabaseDevice`) for PostgREST upserts. |
| `repository/PersonRepositoryImpl.kt` | **Person Repository Implementation**: Orchestrates Room database caching, device registration, normalization, and contact synchronization. |
| `repository/CallRepositoryImpl.kt` | **Call Repository Implementation**: Implements `CallRepository` interface, coordinating between local Room DB, Android system providers, and `PersonDao` identity resolution. |
| `repository/FirestoreRepositoryImpl.kt` | **Firestore Repository Implementation**: Implements `FirestoreRepository`, managing remote metadata synchronization and offline queuing. |
| `repository/RecordingRepositoryImpl.kt` | **Recording Repository Implementation**: Coordinates audio file discovery, local database caching, and playback source preparation. |
| `service/UploadServiceImpl.kt` | **Recording Upload Coordinator**: Streams audio files to Firebase Cloud Storage with progress updates. |
| `worker/SyncWorker.kt` | **Background Sync Worker**: Scheduled periodic WorkManager task that resolves contacts to canonical people, syncs call logs, and pushes pending records to Firebase and Supabase. |
| `worker/UploadRecordingWorker.kt` | **Recording Upload Worker**: Dedicated WorkManager worker executing sequential background uploads of audio files to Firebase Cloud Storage. |

---

## 4. 💉 Dependency Injection (`di/`)

| File Name | Description & Code Details |
|---|---|
| `di/AppModule.kt` | **Application Module**: Provides application context, singletons, `SimManager`, and binds repository implementations (`CallRepository`, `PersonRepository`, `RecordingRepository`, `FirestoreRepository`). |
| `di/DatabaseModule.kt` | **Database Module**: Builds `CallVaultDatabase` singleton with `MIGRATION_5_6`, `MIGRATION_13_14`, `MIGRATION_14_15`, `MIGRATION_15_16`, and `MIGRATION_16_17`, providing all DAOs (`PersonDao`, `CallDao`, `SalesCallDao`, `RecordingDao`, `SyncLogDao`, `TracebackDao`, `ReminderDao`). |
| `di/NetworkModule.kt` | **Network Module**: Provides `SupabaseService`, `FirestoreService`, and network-related singletons. |

---

## 5. 🧠 Domain Layer (`domain/`)

| File Name | Description & Code Details |
|---|---|
| `model/Person.kt` | **Domain Identity Models**: Core business models (`Person`, `PhoneNumber`, `ContactAlias`, `Device`). |
| `model/CallLogEntry.kt` | **Call Log Domain Model**: Clean business model representing an individual call log entry with matched audio details. |
| `model/RecordingFile.kt` | **Recording Domain Model**: Represents an audio recording file with duration, size, and match metadata. |
| `model/SalesCall.kt` | **Sales Call Model**: Domain entity for sales calls with deal stages and customer relationship metrics. |
| `model/FirebaseConfig.kt` & `SupabaseConfig.kt` | **Remote Configuration Models**: Typed connection credentials for backend cloud providers. |
| `repository/PersonRepository.kt` | **Person Repository Interface**: Domain contract for canonical identity resolution, people queries, search, and device syncing. |
| `repository/CallRepository.kt` | **Call Repository Interface**: Defines contracts for fetching, filtering, syncing, and modifying call logs. |
| `repository/FirestoreRepository.kt` | **Firestore Repository Interface**: Defines contracts for remote cloud synchronization. |
| `repository/RecordingRepository.kt` | **Recording Repository Interface**: Defines contracts for audio file scanning and matching. |
| `service/SyncManager.kt` | **Sync Coordination Service**: Domain manager scheduling periodic background sync runs, monitoring network states, and triggering immediate sync requests. |
| `usecase/ResolveAndAttachContactUseCase.kt` | **Identity Resolution Use Case**: Resolves device contacts by normalized phone number, creating canonical people and linking multi-device aliases without duplication. |
| `usecase/SyncCallLogsUseCase.kt` | **Call Log Sync Use Case**: Orchestrates scanning system call logs and updating the local Room database. |
| `usecase/SyncPendingCallsUseCase.kt` | **Cloud Sync Use Case**: Pushes pending local changes to remote Firebase and Supabase backends. |
| `usecase/GetCallLogsUseCase.kt` | **Call Query Use Case**: Reactive queries for filtered, sorted, and searched call logs. |
| `usecase/UpdateCallNotesUseCase.kt` & `UpdateCallTagsUseCase.kt` | **Annotation Use Cases**: Modifies notes and tags on call records. |
| `usecase/ToggleCallFavoriteUseCase.kt` & `DeleteCallUseCase.kt` | **Mutation Use Cases**: Stars/unstars calls or removes entries from the local vault. |
| `usecase/AddReminderUseCase.kt`, `CompleteReminderUseCase.kt`, `DeleteReminderUseCase.kt` | **Reminder Use Cases**: Manages callback reminder lifecycles. |
| `usecase/ClearAllDataUseCase.kt` | **Data Purge Use Case**: Clears local Room databases and resets sync state upon user request. |

---

## 6. 🎨 Presentation Layer (`presentation/`)

### 6.1 Navigation 3 & AllSet Architecture (`presentation/navigation3/`)

| File Name | Description & Code Details |
|---|---|
| `navigation3/Nav3Key.kt` | **Type-Safe Navigation Keys**: Defines destination keys for CRM Tabs (`Dashboard`, `Contacts`, `Tasks`, `Meetings`, `Settings`, `CallLogs`, `DeveloperLogs`), Contacts flow, Tasks, Meetings, and Auth. |
| `navigation3/Nav3BackStack.kt` | **Observable Backstack**: Encapsulates `SnapshotStateList<T>` of typed navigation keys with `navigate`, `pop`, and `popTo` methods. |
| `navigation3/Nav3Display.kt` | **Declarative Nav3 Display**: Observes the topmost key in `Nav3BackStack`, handles smooth page transitions with `AnimatedContent`, and manages hardware back presses. |
| `navigation3/Nav3MultiBackStack.kt` | **Multi-Backstack State**: Maintains isolated backstacks per CRM tab to preserve navigation history and scroll state across tab switches. |
| `navigation3/CrmNav3Container.kt` | **CRM Container Host**: Hosts the bottom navigation bar and renders the active Navigation 3 destination stack. |
| `navigation3/allset/AllSetNavKey.kt` | **AllSet CRM Navigation Keys**: Typed keys for all top-level tabs, contact details, global search, and sub-screen flows. |
| `navigation3/allset/AllSetDeepLinkParser.kt` | **Deep Link Router**: Pure Kotlin URI parser converting `allset://` and `callog://` URIs (`allset://contact/{id}`, `allset://logs`, `allset://contacts/search?q={query}`) to typed navigation keys. |
| `navigation3/allset/AllSetCrmHost.kt` | **AllSet CRM Host Screen**: Implements Navigation 3 display with top-level tabs, adaptive contact layouts, and backstack management. |
| `navigation3/allset/AdaptiveContactsPane.kt` | **Adaptive Large-Screen Layout**: Implements dual-pane layout (List + Details) for tablets and foldables. |
| `navigation3/allset/AllSetResultManager.kt` | **Decoupled Result Passing**: Manages transient result passing between screens without tight coupling. |

### 6.2 Screens (`presentation/screens/`)

| File Name | Description & Code Details |
|---|---|
| `contacts/ContactsScreen.kt` | **Contacts List**: Displays client profiles with real-time filtering, avatars, and direct phone/email shortcuts. |
| `contacts/ContactDetailsScreen.kt` | **Contact Profile**: Displays canonical person identity, company, notes, multiple phone numbers, device aliases, and recent interactions. |
| `contacts/GlobalSearchScreen.kt` | **Global CRM Search**: Real-time multi-attribute search across names, phone numbers, company names, and cross-device aliases. |
| `contacts/ContactSubScreens.kt` | **Contact Sub-Flows**: `WhatsAppScreen`, `EditContactScreen` (returns edited result), and `AddFeedbackScreen` (5-star rating + notes). |
| `tasks/TaskScreens.kt` | **Task Management Flows**: `TaskListScreen`, `CreateTaskScreen` (with result callback), and `TaskDetailsScreen`. |
| `meetings/MeetingScreens.kt` | **Meeting Flows**: `MeetingListScreen`, `ScheduleMeetingScreen`, and `MeetingDetailsScreen`. |
| `auth/AuthScreens.kt` | **Authentication Screens**: `LoginScreen`, `RegisterScreen`, `ForgotPasswordScreen`, and `OtpVerificationScreen`. |
| `home/HomeScreen.kt` & `dashboard/DashboardScreen.kt` | **Home & Dashboard Screens**: Quick stats overview, recent interactions, sync health status widget, and quick actions ("View All" calls). |
| `logs/CallLogsScreen.kt` | **Call Logs Feed**: Main list of incoming, outgoing, and missed calls with audio player controls, search, and back navigation. |
| `details/CallDetailsScreen.kt` & `DetailsScreen.kt` | **Call Detail Screens**: Displays specific call metadata, duration, SIM slot used, recording waveform/playback, tags, and editable notes. |
| `recordings/RecordingManagerScreen.kt` | **Recording Manager**: Lists discovered call recordings, match confidence indicators, and manual matching controls. |
| `analytics/AnalyticsScreen.kt` | **Call Analytics**: Visual charts for call volume, duration metrics, talk-time per SIM, and peak interaction hours. |
| `favorites/FavoritesScreen.kt` | **Favorites Screen**: Filtered feed of starred calls and key client interactions. |
| `settings/SettingsScreen.kt` | **Settings Screen**: Cloud backend credentials configuration (Firebase/Supabase), auto-sync toggles, and business SIM selection. |
| `developer/DeveloperDashboardScreen.kt`, `RecordingDiagnosticsScreen.kt`, `SyncLogsScreen.kt` | **Developer Diagnostic Suite**: Real-time diagnostic monitors for audio scanning heuristics, background sync attempts, and live system tracebacks. |
| `permission/PermissionScreen.kt`, `onboarding/OnboardingScreen.kt`, `splash/SplashScreen.kt` | **Onboarding & Permissions**: Runtime permission rationale dialogs, introductory walkthroughs, and animated splash screen. |

### 6.3 Components, ViewModels, Theme & Navigation

| File Name | Description & Code Details |
|---|---|
| `components/CallCard.kt` | **Call Interaction Card**: Reusable card with caller avatar, call type badge (incoming/outgoing/missed), SIM indicator, duration, and audio play button. |
| `components/GlassyCard.kt` | **Glassmorphic Card**: Translucent elevated surface card with soft 1dp border and theme-aware lighting. |
| `components/ContactAvatar.kt` | **Contact Avatar**: Gradient-backed circular avatar displaying contact photos via Coil or generating two-letter initials. |
| `components/BusinessSimWizardDialog.kt` | **Dual-SIM Configuration Wizard**: Interactive dialog to select which SIM slot is designated for business call syncing. |
| `components/CommonComponents.kt`, `EmptyState.kt`, `LoadingView.kt`, `PermissionDialog.kt`, `SearchBar.kt` | **Design System Components**: Custom search bars, empty state placeholders, loading spinners, and permission dialogs. |
| `viewmodel/ContactDetailsViewModel.kt` | **Contact Details ViewModel**: Receives canonical `contactId: String`, queries repository for details and call logs, and manages UI state. |
| `viewmodel/CallViewModel.kt` & `HomeViewModel.kt` | **Main & Home ViewModels**: Manage call log state flows, search query filtering, manual sync triggers, and audio playback state. |
| `viewmodel/AnalyticsViewModel.kt`, `FavoritesViewModel.kt`, `SettingsViewModel.kt` | **Feature ViewModels**: Manage analytics aggregations, starred calls, and app settings state flows. |
| `theme/Color.kt`, `Theme.kt`, `Type.kt` | **Material 3 Design System**: Custom dark/light color schemes, slate/indigo palettes, typography scales, and shapes. |
| `navigation/BottomNavigationBar.kt`, `NavGraph.kt`, `Screen.kt` | **Navigation Graph**: Bridge routing between existing screens and the new Navigation 3 CRM host. |

---

## 7. 📶 Dual-SIM Management (`sim/`)

| File Name | Description & Code Details |
|---|---|
| `sim/SimInfo.kt` | **SIM Data Model**: Represents SIM slot index (`0` or `1`), subscription ID, carrier name, country ISO, display name, and icon tint. |
| `sim/SimManager.kt` | **Hardware SIM Manager**: Interacts with Android's `SubscriptionManager`, detects active SIM subscriptions, identifies dual-SIM hardware, and maps call logs to physical SIM slots. |
| `sim/SimRepository.kt` | **SIM Preferences Repository**: Persists user selection for which SIM is designated for business syncing. |

---

## 8. 🧪 Unit Test Suites (`app/src/test/`)

51 total unit tests passing with 0 failures:

| Test Suite | Focus & Verified Behavior |
|---|---|
| `CallPersonIdentityResolutionTest.kt` | **Phase 3 Call-to-Person Resolution Tests**: Verifies incoming call normalization and matching to canonical `personId`, unknown number `personId = null` handling (zero phantom contacts), multi-alias name independence, 5-device multi-call resolution, `CallDao` Person queries, and `SalesCallEntity` / `SupabaseSalesCall` `person_id` propagation. |
| `ServerIdentityResolutionTest.kt` | **Phase 2 Server-Side Identity Tests**: Verifies atomic RPC identity resolution, alias idempotency, the 5-device scenario (*Rahul Sharma*, *Rahul*, *Rahul Sir*, *R Sharma*, *ABC Client* &rarr; 1 canonical `Person`, 1 phone, 5 aliases), offline-first fallback, and network error handling. |
| `CentralPersonIdentityTest.kt` | **Central Person Identity Tests**: Verifies canonical `Person` creation, phone number normalization, multi-device alias aggregation (e.g. *Rahul Sharma*, *Rahul Sir*, *ABC Client* &rarr; 1 `Person`), multi-phone storage, and canonical deep links. |
| `AllSetBackNavigationTest.kt` | **Back Navigation Unit Tests**: Tests `push`, `pop`, `popTo`, and `resetToRoot` backstack manipulations. |
| `AllSetDeepLinkTest.kt` | **Deep Link Parser Tests**: Tests URI routing (`allset://contact/{id}`, `whatsapp/{id}`, `task/{id}`, `meeting/{id}`, `logs`, `contacts/search`). |
| `AllSetAuthRoutingTest.kt` | **Conditional Auth Tests**: Tests navigation state transitions between `Unauthenticated` and `Authenticated`. |
| `AllSetContactNavigationTest.kt` | **Canonical ContactId Tests**: Tests contact navigation using canonical IDs and sub-screen transitions. |
| `AllSetMultiBackStackTest.kt` | **Multi-Backstack Isolation Tests**: Tests state preservation across tab switches (Contacts &rarr; Meetings &rarr; Tasks &rarr; Contacts). |
| `AllSetResultPassingTest.kt` | **Result Passing Tests**: Tests `AllSetResultManager` result propagation for `EditContact`, `AddFeedback`, and `CreateTask`. |

---

## 9. 🗄️ Phase 2 Server-Side Centralized Identity Resolution (Supabase PostgreSQL)

### PostgreSQL Atomic RPC: `resolve_and_attach_contact`
Located in `supabase/migrations/20260904_phase2_identity_resolution.sql`:
* **Atomic Concurrency-Safe Resolution**: Performs an atomic `INSERT INTO phone_numbers ... ON CONFLICT (normalized_number) DO UPDATE` returning the canonical `person_id` and `phone_number_id`.
* **Idempotent Device Aliases**: Enforces `UNIQUE (device_id, normalized_number)` on `contact_aliases` so repeated syncs from the same device never duplicate aliases.
* **5-Device Acceptance Test Verified**: Sending requests from 5 distinct devices for the same phone number creates exactly **1 Person**, **1 Phone Number**, and **5 Device Aliases**.

```sql
SELECT public.resolve_and_attach_contact(
    p_device_id := 'DEV_001',
    p_android_contact_id := 'c_101',
    p_alias_name := 'Rahul Sharma',
    p_phone_number := '+91 98765 43210',
    p_normalized_number := '9876543210',
    p_device_name := 'Pixel 8 Pro'
);
```

---

## 10. 📞 Phase 3: Connect Call Logs to Canonical Person Identity

Phase 3 anchors all Android call log records directly to the authoritative CRM canonical `Person`:

```text
CallLogProvider (system calls)
       ↓
PhoneNumberNormalizer (E.164 / normalized digits)
       ↓
PersonRepository / PersonDao (canonical identity lookup)
       ↓
canonical personId
       ↓
CallEntity (Room DB indexed column)
       ↓
SyncWorker & SalesCallDao
       ↓
Supabase (sales_calls.person_id)
```

### Key Architectural Decisions & Guarantees
1. **Name-Independent Identity**: A call's ownership is determined exclusively by its normalized phone number matching `phone_numbers.normalized_number`, never by raw Android contact names or cached caller labels.
2. **Strict Unknown-Number Handling**: Ingestion of call logs from unregistered numbers stores `personId = null` directly; no phantom CRM people are created for raw calls.
3. **Room Database v17**: `MIGRATION_16_17` adds `personId` with index to `calls_research` and `sales_calls` tables.
4. **360° Interaction History**: `ContactDetailsViewModel` queries call history exclusively through `callRepository.getCallsForPerson(person.id)`.
5. **PostgreSQL Supabase Migration**: `supabase/migrations/20260904_phase3_calls_person_link.sql` adds `person_id TEXT REFERENCES public.people(id) ON DELETE SET NULL` and a safe backfill query using `public.normalize_phone_number(raw)`.

---

## ⚙️ Setup & Configuration

### 1. Permissions Required
The app requires permissions to perform system reads and storage lookups declared in `AndroidManifest.xml`:
* `READ_CALL_LOG` - Access call history.
* `READ_CONTACTS` - Match caller IDs with contact names.
* `READ_PHONE_STATE` - Monitor ongoing call state.
* `POST_NOTIFICATIONS` - Post foreground service and sync notifications (Android 13+).
* `READ_EXTERNAL_STORAGE` / `READ_MEDIA_AUDIO` - Access audio recordings.

### 2. Remote Configuration (Firebase & Supabase)
To establish server connections, configure details via the **Device Configuration** Settings screen inside the app:
* **Device Phone Number & Owner Name**: Used to attribute caller details and organize upload pathways.
* **Firebase Credentials**: Setup the required `google-services.json` or connect directly.
* **Supabase Client**: Configured to upsert `people`, `phone_numbers`, `contact_aliases`, `devices`, and `sales_calls` into your PostgreSQL database.

---

## 🛠️ Build & Installation

### Gradle CLI Quickstart

1. **Build the Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```
2. **Run All Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest
   ```
3. **Install on a Connected Device**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. **Test Deep Links on Device**:
   ```bash
   adb shell am start -W -a android.intent.action.VIEW -d "allset://logs" com.example.callog
   adb shell am start -W -a android.intent.action.VIEW -d "allset://contact/P001" com.example.callog
   ```
