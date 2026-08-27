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

- `MainActivity` holds the `SpendViewModel`. All navigation is state-based — there is no NavController. The `ActiveView` enum (`DASHBOARD`, `LEND_BORROW`, `HISTORY`, `HISTORY_TRASH`, `ADD_SPEND`, `LEND_BORROW_HISTORY`, `RECURRING_BILLS`, `NOTES`, `NOTES_HISTORY`, `SETTINGS`) drives `AnimatedContent` in `MainContainer`. The active view is hoisted inside the `MainContainer` composable via `rememberSaveable` (survives config change / process death), not in `MainActivity` or the ViewModel. App configuration lives in a dedicated `SettingsScreen` (gear icon in the Dashboard toolbar) — appearance, security/biometric, AI defaults, account; the older Dashboard dropdown menu was removed in favor of it.
- **Navigation history**: `MainContainer` keeps a `rememberSaveable` **backStack** (a `mutableStateList` of major screens: Dashboard, Dues, History, Recurring Bills, Notes, Settings). Navigate between major screens via the `goToMajor(view)` helper (pushes + switches) and `goBackMajor()` (pops), so system back / edge-swipe retraces real visit history instead of always jumping to Dashboard. Detail screens (`ADD_SPEND`, the three trash/history sub-screens) are **not** pushed — each has one fixed parent; the trash/history sub-screens register their own `BackHandler(onBack = ...)`. `ADD_SPEND` returns to wherever it was opened from via a separate `returnTo` state (captured at each entry point, used by both save and dismiss) rather than the stack.
- `MainActivity` also handles **Biometric Authentication** for app locking and **Play In-App Updates**.
- `SpendViewModel` is the single source of truth for all UI state: spending data, time filters, AI processing, and chat history.

### Data layer

| Component | Role |
|-----------|------|
| `AppDatabase` (Room **v21**) | Local source of truth. Seven entities: `Spend` (`spends`), `SpendHistory` (`spend_history`), `ChatMessage` (`chat_messages`), `RecurringBill` (`recurring_bills`), `Note` (`notes`), `NoteEntry` (`note_entries`), `NoteHistory` (`note_history`). Explicit migrations 14→21: 14→15 (`updatedAt` for LWW), 15→16 (`deleted` tombstones + history/chat `updatedAt`), 16→20 (Notes tables + `customFields`), 20→21 (`noteUuid` on `spends`/`spend_history` linking a spend to the Note it was logged from). `fallbackToDestructiveMigration(true)` remains only for unknown pre-14 paths (wipes local data, mostly self-healing for signed-in users via the Firestore re-sync). `exportSchema = false`. |
| `SpendRepository` | Wraps the DAOs and manages Firestore real-time listeners (`startSync`/`stopSync`) for all four collections, all seven built from one generic `startCollectionSync` helper. Three invariants live there: each snapshot is applied in **one** coroutine under a per-collection `Mutex` (a coroutine per document change let the read-then-write last-write-wins check interleave and apply changes out of order); every bulk write goes through `batchDelete`/`batchTombstone`, which chunk at 450 because a Firestore batch rejects more than 500 writes; and every Firestore write goes through `firestoreWrite`, which logs failures but **rethrows `CancellationException`**. Writes go to Room first, then Firestore. `startSync` calls `stopSync` first, so listeners are not duplicated. ⚠️ **All deletes are soft deletes** (spends, bills, history entries, chat messages): deleting writes a `deleted=true` tombstone with a fresh `updatedAt` instead of removing the Firestore doc (a hard delete would be resurrected by another device's `SyncWorker` re-upload). Every user-facing query filters `deleted = 0` — including `getBillsDueOn` (which is also **userId-scoped** — Room is shared across accounts on a device, so an unscoped query fired reminders for a signed-out user's bills) and the chat rate-limit counts (so deleting a failed message refunds quota). Purging: spend/bill tombstones after 30 days via `cleanupOldHistory`→`cleanupOldTombstones`; history/chat tombstones keep their original `recordedAt`/`timestamp` and expire with the normal 30-day/12-hour TTL cleanups. |
| `SyncWorker` | `WorkManager` worker (every 3h) that uploads local rows to Firestore, all gated by an `updatedAt` last-write-wins check (`>=` wins). Uses the `*ForSync` DAO queries that include tombstones, so deletes performed while other devices were offline still propagate. The real-time listeners apply the same LWW gate on the way down. |
| `ChatDao` / `ChatMessage` | Stores AI history chat messages locally with a 12-hour TTL. |
| `AiPreferencesRepository` | DataStore-backed preferences (default currency, app, purpose, daily usage counter, biometric setting, dismissed update version). |

Firestore paths (all owner-scoped in `firestore.rules`): `users/{userId}/spends/{spendId}`, `.../recurring_bills/{id}`, `.../history/{id}`, `.../chat_messages/{id}`.

### AI features

Two separate AI flows. The **primary** provider is **Groq** (OpenAI-compatible) called via Retrofit (`GroqApiService`). **Gemini** (`gemini-3.5-flash`, via `google/generative-ai`) is the **fallback**, used only when the Groq key is blank. The model id is centralized in the `GEMINI_MODEL` constant in `SpendViewModel`.

⚠️ Groq model ids live in **one place**: the `GroqModels` object in `GroqApiService.kt` — `FAST` (`openai/gpt-oss-20b`, expense parsing + intent classification) and `SMART` (`openai/gpt-oss-120b`, history Q&A). The previous `llama-3.1-8b-instant` / `llama-3.3-70b-versatile` pair was **decommissioned by Groq on 2026-08-16**, which broke both AI flows silently — AI Track fell back to the local `AiParser` baseline and the history assistant returned a generic error. Both GPT-OSS models are **reasoning** models, so every request passes `include_reasoning = false` (otherwise the chain-of-thought is what lands in `message.content`) plus `reasoning_effort`; they accept `include_reasoning`, **not** `reasoning_format`. Groq HTTP errors now carry a truncated response body in the exception message, because `model_decommissioned` is reported only there — a bare status code hid the whole sunset.

1. **AI Track** (`processAiInput`) — parses a natural-language expense entry.  
   - Client-side rate limit: 15 uses/day (tracked in DataStore).  
   - `AiParser` runs first as a local heuristic baseline (amount extraction, app matching, purpose inference, date parsing). The LLM then refines it. If the LLM fails, the local baseline is used as fallback (no crash).  
   - The merged result surfaces as `_aiResult: StateFlow<Result<AiTransactionResponse>?>`.

2. **AI History Assistant** (`askAiAboutHistory`) — Q&A chat over the user's full spend history.  
   - Client-side limit: 2 sessions/day × 7 messages/session (tracked in Room).  
   - A cheap `GroqModels.FAST` classifier gates off-topic questions first. It **fails open**: only a verdict containing `OFF_TOPIC` *and not* `FINANCIAL` blocks the question, so a chatty or decorated verdict never costs the user a real answer.  
   - `resolveQueryRange` turns the question's wording into a `[start, endExclusive)` window. It understands `last`/`previous`/`past` as a shift **back one period** and rolling windows like "last 7 days" — without that, "spent last month?" matched the bare `month` branch and returned **this** month.  
   - If the filter lands on zero rows while the user does have transactions, the context falls back to the 200 most recent spends with an explicit note to the model. Previously an empty filter produced "No transactions found for this query." and the assistant had nothing to answer from.  
   - ⚠️ Two things shape the *quality* of the answer. **Context rows** are `- date | amount | purpose | app [| note: text]`; a blank note omits the segment rather than emitting a bare `—`, which the model used to echo back as the literal word "note". And the `RESPONSE FORMAT` template marks placeholders with `<angle brackets>` for the same reason. **Output budget**: a per-person breakdown is a long answer, and `reasoning_effort` shares the `max_completion_tokens` ceiling — at "medium"/1500 replies arrived cut off mid-line, so the call runs `"low"`/4096 and logs a warning when `finish_reason == "length"`.

**API keys are fetched at runtime from Firebase Remote Config** (`groq_api_key`, `gemini_api_key`) to keep usage free on the Spark plan — no keys are baked into the APK. Keys are held only in transient local vals. (Note: OkHttp body logging is debug-only and the `Authorization` header is redacted, so keys never reach Logcat.)

### Authentication & Security

Auth is handled **inline in Composables** (no auth ViewModel; the `auth/AuthManager.kt` class is currently unused). The auth UI is **two dedicated screens**, switched by state in `MainContainer` (`showRegister` boolean + a shared `authEmail` that carries the typed email across the switch):

- `LoginScreen` — **sign-in only**. On failure it maps the Firebase exception instead of surfacing a raw error: `FirebaseAuthInvalidUserException` (user-not-found) or `FirebaseAuthInvalidCredentialsException` shows a "Sign-in failed / register?" dialog with a **Register** button that jumps to `RegisterScreen` with the email pre-filled. Also hosts Forgot-Password and the unverified-email gate.
- `RegisterScreen` — email + password + **Continue with Google**. Creates the account, sends a verification email, then signs out and shows the verify dialog. `FirebaseAuthUserCollisionException` (email already registered) offers a **Sign in** button.
- `AuthComponents.kt` — shared pieces used by both screens: `AuthScaffold` (branded layout), `GoogleButton`, `rememberGoogleSignIn` (Credential Manager → Firebase; `NoCredentialException` yields a clear "no Google account on this device" message), and `EmailVerificationDialog`.

⚠️ `AuthScaffold` applies `imePadding()` **outside** its `verticalScroll`. `MainActivity` declares no `windowSoftInputMode` and draws edge-to-edge, so without it the keyboard simply overlaid the card: the password field sat under the IME and the scroll container — still sized to the whole window — had nothing left to scroll, leaving no way to reach it.

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

Design tokens live in `ui/theme/` and components are expected to use them rather than literal values:

- `Dimens.kt` — `Spacing` (4/8/12/16/20/24/32/48), `Radius`, `Sizes` (`minTouchTarget`, icon sizes, date-badge sizes), and `Dp.scaledByFont()`.
- `Type.kt` — `TextStyle.asMoney()` turns any step of the scale into a currency style (Space Grotesk, tabular figures). Weight caps at `Bold`: the bundled variable fonts top out at 700, so `ExtraBold`/`Black` get faux-bolded by the rasterizer.
- `Motion.kt` — `rememberReduceMotion()` + `motionDuration()`. Never read `Settings.Global.ANIMATOR_DURATION_SCALE` directly in a composable; it is a ContentResolver query and ran on every animation frame before this existed.
- ⚠️ `isAppInDarkTheme` (from `Theme.kt`), **not** `isSystemInDarkTheme()`, is what components must read. The latter reports the device setting and so gives the wrong answer whenever the user has overridden it — it was picking the dark chart palette for a light canvas.
- `Color.kt` holds the `Cat*` (category) and `Purpose*` (purpose) chart palettes plus `OnGradient*` for accents drawn on the hero gradient, where `colorScheme` roles have no reliable contrast.

### Key UI components

- `LoginScreen` / `RegisterScreen` / `AuthComponents` → the two-screen auth flow (see Authentication & Security)
- `ProfileDialog` → display-name editor, shared by the Dashboard greeting and the Settings account row
- `AiInputBottomSheet` → user types natural language → `SpendViewModel.processAiInput`
- `AiConfirmationScreen` → user confirms/edits the parsed result before saving
- `AiHistoryAssistantSheet` → chat UI for history Q&A
- `SpendingCharts` / `DashboardCards` — consume `SpendingAnalytics` derived state from the ViewModel
- `SearchField` — the shared search box on History and Dues. ⚠️ It deliberately sets **no** fixed height. `OutlinedTextField` reserves a 56dp minimum internally, so the `Modifier.height(50.dp)` the call sites used to pass did not shrink the field, it **clipped** it — which is why the box rendered visibly cut off, and why it got worse as the system font scale grew.
- `ExpensePresets` / `APP_PRESETS` / `PURPOSE_PRESETS` — canonical lists used by both `AiParser` and the manual `AddSpendScreen`

### Dues (lending & borrowing)

User-facing this section is called **Dues** — the nav label, the screen title, the hero chip, the export subjects and `LendBorrowHistoryScreen`'s title all say "Dues". Only the *display* name changed: `ActiveView.LEND_BORROW`, `LendBorrowScreen`, `LendBorrowHistoryScreen` and the `"Lending"` / `"Borrowing"` purpose values are unchanged, because the purpose strings are persisted in Room and Firestore and renaming them would orphan every existing record.

Lending and borrowing are stored as regular `Spend` records with `purpose = "Lending"` or `"Borrowing"`. They are filtered out of dashboard analytics and the main history list, and shown exclusively in `LendBorrowScreen`.

### Spending trend chart

`SpendViewModel.calculateTrendPoints(spends, filter, range)` produces the `TrendPoint` list `SpendingTrendBarChart` draws. Bucketing per filter: DAY → hour, WEEK → day of week, YEAR → month, ALL → year.

- **MONTH and long CUSTOM ranges bucket by calendar *week***, not by day. Weeks start on **Monday** (`TREND_WEEK_START`) — deliberately not the locale's `firstDayOfWeek`, which is Sunday in en-IN and would split every weekend across two bars. The first bucket is only the part of its week inside the range, so August 2026 comes out as `1–2 · 3–9 · 10–16 · 17–23 · 24–30 · 31`, and a custom range starting mid-week runs from the chosen day to that Sunday before full weeks resume. Empty buckets are still emitted so the axis stays a continuous timeline.
- A CUSTOM range of `CUSTOM_TREND_DAILY_MAX_DAYS` (14) or fewer keeps **one bar per day**; below that, weekly buckets would collapse the range to two or three bars and say nothing.
- ⚠️ **No average is drawn.** There is no dashed guideline, no above/below-average bar recolouring and no legend — every bar is one colour and carries its own amount. A mean is still computed inside `displayMax` purely to floor the plot ceiling; it is never rendered.
- The month view used to draw 31 day-bars in ~330dp (~10dp a slot), which is why the labels had to be rotated -90°. With 5–6 week-bars the chart picks its horizontal layout automatically — `rotateLabels` / `rotateValues` only trigger when a horizontal label genuinely doesn't fit a slot, which now only happens on a short daily CUSTOM range.

### Density & font scale

The app has to survive the system font-size setting (Settings → Display → Font size), so two rules hold across the UI:

- **Never give a text-bearing box a fixed `height`/`size`** — use `heightIn(min = …)` / `sizeIn(min… = …)`. A hard height does not shrink text, it crops it: that is what cut "aug" off the bottom of `DateBadge`, sliced the History category chips, and clipped the search field. `Sizes.minTouchTarget` is the floor for hand-rolled tap targets.
- **Every `maxLines = 1` needs `overflow = TextOverflow.Ellipsis`.** The default is `Clip`, which runs text off the edge mid-glyph; with ellipsis a segmented-control label degrades to "Cat…" instead. Segments and chips that are a fixed fraction of a row (`ChartToggle`, `TimeFilterSelectorRow`, `SegmentedTabs`, the nav bar labels) all rely on this.
- For the few things that need a concrete size and cannot use a minimum — a `Canvas`, a fixed-height scrolling grid — `Dp.scaledByFont()` (in `Dimens.kt`) multiplies by the clamped font scale.

### Recurring Bills

`RecurringBillWorker` (Hilt `CoroutineWorker`, scheduled from `MainActivity`) checks `SpendRepository.getBillsDueOn(dayOfMonth)` and fires a notification at two windows per due bill (12:30 PM, 10:00 PM), tracked via `notifiedAt1230`/`notifiedAt2200`/`lastNotifiedDate` flags on `RecurringBill` so each window fires once per day. Before notifying, it calls `SpendRepository.findMatchingSpend` (same user/app/purpose within the day) to skip bills already logged that day. Tapping the notification opens `MainActivity` with `BILL_*` intent extras, which it reads once and clears (`intent.removeExtra`) to pre-fill `ADD_SPEND` with the bill's details.

### Notes → transaction

Notes (`Note` + `NoteEntry`) are a standalone collection whose entry amounts never touch spend analytics — until the user chooses to roll a whole note up into the main log. "Log as transaction" (in a note tile's 3-dot menu and in the open-note toolbar) calls `SpendViewModel.logNoteAsTransaction(note, defaultApp)`, which **upserts** a single `Spend` per note (keyed by `noteUuid` via `SpendDao.getActiveSpendByNoteUuid`, so re-logging updates rather than duplicates): `amount` = sum of the note's entry amounts, `purpose` = note title, `appName` = the user's default payment app (`AiPreferences.defaultApp`, "Google Pay" by default), `category` derived from that app's preset, `noteUuid` = the note. In `HistoryScreen`, note-linked spends (`noteUuid` non-blank) show a small note glyph and are tappable — `onOpenNote` sets `pendingNoteUuid` and navigates to `NOTES`, where `NotesScreen`'s `initialNoteUuid` auto-opens that note. A Notes shortcut icon also sits next to the AI button in the Dashboard header.
