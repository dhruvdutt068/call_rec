# AGENTS.md - Agent Instructions & Workspace Guidelines

Welcome to the **Callog (Call Log & Recording Vault)** codebase. This document outlines the guidelines, architectural rules, workspace conventions, and instructions for AI agents (and human contributors) interacting with and maintaining this project.

---

## 🎯 Project Overview & Purpose

**Callog** is a modern Android application built using Kotlin and Jetpack Compose designed to:
1. Scan and parse system call logs and contacts across single and dual-SIM devices.
2. Discover, match, and play local audio call recordings (`.m4a`, `.mp3`, etc.) using media timestamps, caller proximity, and metadata heuristics.
3. Synchronize structured call logs and sales interactions to **Firebase Firestore** and **Supabase (PostgreSQL)**.
4. Upload call recording audio assets to **Firebase Cloud Storage**.
5. Provide offline-first caching via **Room Database** and robust background sync using **WorkManager** with network-awareness and exponential backoff.

---

## 🏗️ Architecture & Layering

The codebase strictly follows **Clean Architecture + MVVM + Unidirectional Data Flow (UDF)**:

```text
app/src/main/java/com/example/callog/
│
├── core/                  # Utility classes, diagnostics (DeveloperLogger), connectivity monitors
├── data/                  # Data layer (Data Sources, DAOs, Entities, Repositories, Providers, Workers)
│   ├── local/             # Room DB, DAOs (CallDao, SalesCallDao, SyncLogDao, etc.), Entities
│   ├── provider/          # CallLogProvider, RecordingScanner
│   ├── receiver/          # Broadcast receivers (CallReceiver for phone state)
│   ├── remote/            # Remote service clients (FirestoreService, SupabaseService)
│   ├── repository/        # Repository implementations
│   └── worker/            # Background WorkManager workers (SyncWorker, etc.)
│
├── di/                    # Dagger Hilt dependency injection modules (DatabaseModule, NetworkModule, etc.)
│
├── domain/                # Pure business logic layer (Framework-independent where possible)
│   ├── model/             # Domain models (CallLog, FirebaseConfig, etc.)
│   ├── repository/        # Interface definitions for repositories
│   ├── service/           # Domain managers/coordinators (SyncManager)
│   └── usecase/           # Atomic business use cases
│
├── presentation/          # UI layer (Jetpack Compose)
│   ├── components/        # Reusable Compose widgets and cards
│   ├── navigation/        # Compose Navigation graph & destinations
│   ├── screens/           # Full-screen Compose UI (Dashboard, Recordings, Settings, Analytics)
│   ├── theme/             # Material 3 Theme, Typography, Color palette
│   └── viewmodel/         # Android ViewModels (CallViewModel, AnalyticsViewModel)
│
└── sim/                   # SIM card detection, Multi-SIM management, and routing
```

---

## 📌 Coding Standards & Conventions

### 1. Kotlin & Coroutines
- Use idiomatic Kotlin with explicit type contracts where ambiguity exists.
- Prefer `StateFlow` and `SharedFlow` over legacy LiveData.
- Always dispatch I/O operations (Room, Network, File scanning) on `Dispatchers.IO` and UI state emissions on `Dispatchers.Main`.
- Avoid blocking calls; use coroutines and structured concurrency with `viewModelScope` / `lifecycleScope`.

### 2. Jetpack Compose
- Adhere to **Unidirectional Data Flow (UDF)**: State flows down, events flow up.
- Pass lambdas for event callbacks rather than passing ViewModels into sub-components.
- Use `remember` and `rememberSaveable` appropriately.
- Support Dynamic/Dark Theming via `MaterialTheme.colorScheme`.

### 3. Dependency Injection (Hilt)
- Provide dependencies in appropriate modules inside `di/` (`DatabaseModule`, `NetworkModule`, `AppModule`, etc.).
- Inject dependencies via constructor injection (`@Inject constructor(...)`).
- Use `@HiltViewModel` for ViewModels and `@AndroidEntryPoint` for Activities / Fragments / Services.

### 4. Database & Caching (Room)
- Always write explicit migrations when changing schema entities in `data/local/entity/`.
- Use Flow-based DAO methods for reactive UI updates.

---

## 📂 Workspace & Obsidian Vault Guidelines (`all_set`)

When working across workspaces or linking project knowledge with documentation vaults (such as your **Obsidian `all_set` folder**):

1. **Keep Documentation in Sync**:
   - Record significant architecture decisions, new schema models, or API endpoint updates in markdown documentation.
   - When modifying sync logic, data models, or adding remote providers, ensure `README.md` and related notes in `all_set` reflect the changes.
2. **Modular Notes Structure**:
   - Maintain clean cross-references using standard markdown links.
   - Tag notes with `#callog #android #compose #supabase #firebase` for easy retrieval.
3. **Preserve Environment Secrets**:
   - Never commit sensitive Firebase Service Account keys, Supabase Service Role keys, or production keystores directly into markdown notes or git repositories.

---

## 🛠️ Build, Test & Run Commands

| Task | Command |
|---|---|
| **Build Debug APK** | `./gradlew assembleDebug` |
| **Run Unit Tests** | `./gradlew test` |
| **Run Lint Checks** | `./gradlew lintDebug` |
| **Clean Project** | `./gradlew clean` |
| **Install on Device** | `adb install -r app/build/outputs/apk/debug/app-debug.apk` |

---

## 🤖 Agent Operating Rules

When fulfilling tasks in this repository, agents must:
1. **Analyze Before Modifying**: Read the relevant files, check existing DI modules, and inspect entity relationships before making code changes.
2. **Follow Clean Architecture**: Do not put business logic or data fetching inside Compose UI files or ViewModels directly without going through UseCases/Repositories.
3. **Respect Android Permissions**: Any new hardware or system data access must be declared in `AndroidManifest.xml` and handled via runtime permission requests.
4. **Preserve Existing Code & Comments**: Keep relevant comments, docstrings, and established naming patterns intact.
