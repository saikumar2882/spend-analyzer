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

- `MainActivity` holds the `SpendViewModel`. All navigation is state-based — there is no NavController. The `ActiveView` enum (`DASHBOARD`, `LEND_BORROW`, `HISTORY`, `ADD_SPEND`, `LEND_BORROW_HISTORY`, `RECURRING_BILLS`, `SETTINGS`) drives `AnimatedContent` in `MainContainer`. The active view is hoisted inside the `MainContainer` composable via `rememberSaveable` (survives config change / process death), not in `MainActivity` or the ViewModel. App configuration lives in a dedicated `SettingsScreen` (gear icon in the Dashboard toolbar) — appearance, security/biometric, AI defaults, account; the older Dashboard dropdown menu was removed in favor of it.
- `MainActivity` also handles **Biometric Authentication** for app locking and **Play In-App Updates**.
- `SpendViewModel` is the single source of truth for all UI state: spending data, time filters, AI processing, and chat history.

### Data layer

| Component | Role |
|-----------|------|
| `AppDatabase` (Room **v16**) | Local source of truth. Four entities: `Spend` (`spends`), `SpendHistory` (`spend_history`), `ChatMessage` (`chat_messages`), `RecurringBill` (`recurring_bills`). Explicit migrations cover 14→15 (`updatedAt` for LWW) and 15→16 (`deleted` tombstones on all four tables + `updatedAt` on history/chat); `fallbackToDestructiveMigration(true)` remains only for unknown pre-14 paths (wipes local data, mostly self-healing for signed-in users via the Firestore re-sync). `exportSchema = false`. |
| `SpendRepository` | Wraps the DAOs and manages Firestore real-time listeners (`startSync`/`stopSync`) for all four collections. Writes go to Room first, then Firestore. `startSync` calls `stopSync` first, so listeners are not duplicated. ⚠️ **All deletes are soft deletes** (spends, bills, history entries, chat messages): deleting writes a `deleted=true` tombstone with a fresh `updatedAt` instead of removing the Firestore doc (a hard delete would be resurrected by another device's `SyncWorker` re-upload). Every user-facing query filters `deleted = 0` — including `getBillsDueOn` (so deleted bills don't auto-log) and the chat rate-limit counts (so deleting a failed message refunds quota). Purging: spend/bill tombstones after 30 days via `cleanupOldHistory`→`cleanupOldTombstones`; history/chat tombstones keep their original `recordedAt`/`timestamp` and expire with the normal 30-day/12-hour TTL cleanups. |
| `SyncWorker` | `WorkManager` worker (every 3h) that uploads local rows to Firestore, all gated by an `updatedAt` last-write-wins check (`>=` wins). Uses the `*ForSync` DAO queries that include tombstones, so deletes performed while other devices were offline still propagate. The real-time listeners apply the same LWW gate on the way down. |
| `ChatDao` / `ChatMessage` | Stores AI history chat messages locally with a 12-hour TTL. |
| `AiPreferencesRepository` | DataStore-backed preferences (default currency, app, purpose, daily usage counter, biometric setting, dismissed update version). |

Firestore paths (all owner-scoped in `firestore.rules`): `users/{userId}/spends/{spendId}`, `.../recurring_bills/{id}`, `.../history/{id}`, `.../chat_messages/{id}`.

### AI features

Two separate AI flows. The **primary** provider is **Groq** (OpenAI-compatible, models `llama-3.1-8b-instant` / `llama-3.3-70b-versatile`) called via Retrofit (`GroqApiService`). **Gemini** (`gemini-3.5-flash`, via `google/generative-ai`) is the **fallback**, used only when the Groq key is blank. The model id is centralized in the `GEMINI_MODEL` constant in `SpendViewModel`.

1. **AI Track** (`processAiInput`) — parses a natural-language expense entry.  
   - Client-side rate limit: 15 uses/day (tracked in DataStore).  
   - `AiParser` runs first as a local heuristic baseline (amount extraction, app matching, purpose inference, date parsing). The LLM then refines it. If the LLM fails, the local baseline is used as fallback (no crash).  
   - The merged result surfaces as `_aiResult: StateFlow<Result<AiTransactionResponse>?>`.

2. **AI History Assistant** (`askAiAboutHistory`) — Q&A chat over the user's full spend history.  
   - Client-side limit: 2 sessions/day × 7 messages/session (tracked in Room).

**API keys are fetched at runtime from Firebase Remote Config** (`groq_api_key`, `gemini_api_key`) to keep usage free on the Spark plan — no keys are baked into the APK. Keys are held only in transient local vals. (Note: OkHttp body logging is debug-only and the `Authorization` header is redacted, so keys never reach Logcat.)

### Authentication & Security

Auth is handled **inline in Composables** (no auth ViewModel; the `auth/AuthManager.kt` class is currently unused). The auth UI is **two dedicated screens**, switched by state in `MainContainer` (`showRegister` boolean + a shared `authEmail` that carries the typed email across the switch):

- `LoginScreen` — **sign-in only**. On failure it maps the Firebase exception instead of surfacing a raw error: `FirebaseAuthInvalidUserException` (user-not-found) or `FirebaseAuthInvalidCredentialsException` shows a "Sign-in failed / register?" dialog with a **Register** button that jumps to `RegisterScreen` with the email pre-filled. Also hosts Forgot-Password and the unverified-email gate.
- `RegisterScreen` — email + password + **Continue with Google**. Creates the account, sends a verification email, then signs out and shows the verify dialog. `FirebaseAuthUserCollisionException` (email already registered) offers a **Sign in** button.
- `AuthComponents.kt` — shared pieces used by both screens: `AuthScaffold` (branded layout), `GoogleButton`, `rememberGoogleSignIn` (Credential Manager → Firebase; `NoCredentialException` yields a clear "no Google account on this device" message), and `EmailVerificationDialog`.

⚠️ **Email verification is required** before a session is considered signed in — both flows sign the user back out and gate on `isEmailVerified` via the shared dialog.

⚠️ **Precise "email not registered" detection depends on Firebase's Email Enumeration Protection** (Console → Authentication → Settings). When **ON** (default), `user-not-found` and `wrong-password` return the *same* generic `INVALID_CREDENTIAL` error, so the app shows a combined "incorrect email or password — register?" dialog. Turn it **OFF** to get the exact `USER_NOT_FOUND` → "no account, register" message. `fetchSignInMethodsForEmail` is intentionally **not** used (deprecated / unreliable under enumeration protection).

⚠️ The auth screens **early-return** in `MainContainer` before the main notification banner is composed, so that banner is also rendered inside the auth branch — otherwise `onShowNotification` messages on Login/Register are set but never displayed.

- **Credential Manager**: Used for modern Google Sign-In flow (requires a registered SHA-1 in Firebase + Google Play Services on the device; emulators must use a "Google Play" image).
- **Biometric API**: Used in `MainActivity` to lock the app. State managed in `SpendViewModel`.
- **Firebase Auth**: Supports Google and Email/Password (with email verification). Password validation is a **minimum of 6 characters** (`RegisterScreen.isValidPassword`); an email-link/passwordless flow is **not** wired up (only a half-implemented receiver stub in `MainActivity.handleEmailLink`).

### Widgets

- **QuickAddWidget**: Built with **Jetpack Glance**, allows quick access to AI logging from the home screen.

### Update Mechanism

- **Play Store**: Uses Google Play In-App Update API (`IMMEDIATE`).
- **GitHub/Free**: A custom `UpdateChecker` polls GitHub Releases (`releases/latest`) and compares the tag against the installed `versionName`. The "new version" dialog is suppressed **per-version**: once a version is dismissed or downloaded it is recorded in `dismissedUpdateVersion` (DataStore) and never prompted again — only a strictly newer release re-triggers it. Version comparison pads components with zeros so `1.0.5` and `1.0.5.0` are equal.

### Monitoring

- **Firebase Crashlytics**: Real-time crash reporting.
- **Firebase Analytics**: User growth and behavior tracking.

### Theme

`ThemePreference` (Light / Dark / System) is persisted via `rememberThemePreference()` (DataStore). Cycling is triggered from the Dashboard toolbar and propagated down through `MainContainer`.

### Key UI components

- `LoginScreen` / `RegisterScreen` / `AuthComponents` → the two-screen auth flow (see Authentication & Security)
- `AiInputBottomSheet` → user types natural language → `SpendViewModel.processAiInput`
- `AiConfirmationScreen` → user confirms/edits the parsed result before saving
- `AiHistoryAssistantSheet` → chat UI for history Q&A
- `SpendingCharts` / `DashboardCards` — consume `SpendingAnalytics` derived state from the ViewModel
- `ExpensePresets` / `APP_PRESETS` / `PURPOSE_PRESETS` — canonical lists used by both `AiParser` and the manual `AddSpendScreen`

### Lend & Borrow

Lending and borrowing are stored as regular `Spend` records with `purpose = "Lending"` or `"Borrowing"`. They are filtered out of dashboard analytics and the main history list, and shown exclusively in `LendBorrowScreen`.
