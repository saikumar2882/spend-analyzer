# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD env vars)
./gradlew assembleRelease

# Run all unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.alpha.spendtracker.GreetingScreenshotTest"

# Run screenshot tests (Roborazzi)
./gradlew recordRoborazziDebug    # record golden images
./gradlew verifyRoborazziDebug    # compare against golden images

# Lint
./gradlew lint
```

Open in Android Studio Ladybug or newer, add `app/google-services.json` from your Firebase project before building.

## Architecture

**MVVM, offline-first, single-Activity, Hilt DI.**

- `MainActivity` holds the `SpendViewModel`. All navigation is state-based — there is no NavController. `ActiveView` enum (`DASHBOARD`, `HISTORY`, `LEND_BORROW`, `ADD_SPEND`) drives `AnimatedContent` in `MainContainer`.
- `MainActivity` also handles **Biometric Authentication** for app locking and **Play In-App Updates**.
- `SpendViewModel` is the single source of truth for all UI state: spending data, time filters, AI processing, and chat history.

### Data layer

| Component | Role |
|-----------|------|
| `AppDatabase` (Room v5) | Local source of truth. Uses explicit migrations where possible (destructive migration disabled for production safety). |
| `SpendRepository` | Wraps `SpendDao` and manages a Firestore real-time listener (`startSync`/`stopSync`). Writes go to Room first, then Firestore. |
| `SyncWorker` | `WorkManager` worker that performs periodic background synchronization to Firestore. |
| `ChatDao` / `ChatMessage` | Stores AI history chat messages locally with a 12-hour TTL. |
| `AiPreferencesRepository` | DataStore-backed preferences (default currency, app, purpose, daily usage counter, biometric setting). |

Firestore path: `users/{userId}/spends/{spendId}`.

### AI features

Two separate AI flows, both using Gemini (`gemini-1.5-flash`) via `google/generative-ai`:

1. **AI Track** (`processAiInput`) — parses a natural-language expense entry.  
   - Client-side rate limit: 15 uses/day (tracked in DataStore).  
   - `AiParser` runs first as a local heuristic baseline (amount extraction, app matching, purpose inference, date parsing). Gemini then refines it. If Gemini fails, the local result is used as fallback.  
   - The merged result surfaces as `_aiResult: StateFlow<Result<AiTransactionResponse>?>`.

2. **AI History Assistant** (`askAiAboutHistory`) — Q&A chat over the user's full spend history.  
   - Client-side limit: 2 sessions/day × 7 messages/session (tracked in Room).

**Gemini API key is fetched at runtime from Firebase Remote Config** (`gemini_api_key`) to ensure 100% free usage on the Spark plan.

### Authentication & Security

- **Credential Manager**: Used for modern Google Sign-In flow.
- **Biometric API**: Used in `MainActivity` to lock the app. State managed in `SpendViewModel`.
- **Firebase Auth**: Supports Google, Email/Password, and Email Link.

### Widgets

- **QuickAddWidget**: Built with **Jetpack Glance**, allows quick access to AI logging from the home screen.

### Update Mechanism

- **Play Store**: Uses Google Play In-App Update API.
- **GitHub/Free**: Uses a custom `UpdateChecker` to detect new releases via GitHub API.

### Monitoring

- **Firebase Crashlytics**: Real-time crash reporting.
- **Firebase Analytics**: User growth and behavior tracking.

### Theme

`ThemePreference` (Light / Dark / System) is persisted via `rememberThemePreference()` (DataStore). Cycling is triggered from the Dashboard toolbar and propagated down through `MainContainer`.

### Key UI components

- `AiInputBottomSheet` → user types natural language → `SpendViewModel.processAiInput`
- `AiConfirmationScreen` → user confirms/edits the parsed result before saving
- `AiHistoryAssistantSheet` → chat UI for history Q&A
- `SpendingCharts` / `DashboardCards` — consume `SpendingAnalytics` derived state from the ViewModel
- `ExpensePresets` / `APP_PRESETS` / `PURPOSE_PRESETS` — canonical lists used by both `AiParser` and the manual `AddSpendScreen`

### Lend & Borrow

Lending and borrowing are stored as regular `Spend` records with `purpose = "Lending"` or `"Borrowing"`. They are filtered out of dashboard analytics and the main history list, and shown exclusively in `LendBorrowScreen`.
