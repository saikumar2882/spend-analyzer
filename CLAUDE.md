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
| `AppDatabase` (Room **v22**) | Local source of truth. Seven entities: `Spend` (`spends`), `SpendHistory` (`spend_history`), `ChatMessage` (`chat_messages`), `RecurringBill` (`recurring_bills`), `Note` (`notes`), `NoteEntry` (`note_entries`), `NoteHistory` (`note_history`). Explicit migrations 14→22: 14→15 (`updatedAt` for LWW), 15→16 (`deleted` tombstones + history/chat `updatedAt`), 16→20 (Notes tables + `customFields`), 20→21 (`noteUuid` on `spends`/`spend_history` linking a spend to the Note it was logged from), 21→22 (`isCreditCard`/`cardLast4` on `recurring_bills`). `fallbackToDestructiveMigration(true)` remains only for unknown pre-14 paths (wipes local data, mostly self-healing for signed-in users via the Firestore re-sync). `exportSchema = false`. |
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
   - ⚠️ **Payment app resolution**: local alias match (`AiParser.APP_ALIASES`, which includes voice misspellings like "swiggi"/"zapto" and Telugu/Hindi script) → the LLM's `appName` → the user's default. The LLM's app is trusted even when it isn't spelled verbatim in the input; the only exception is an unmentioned "Google Pay", which the model guesses by reflex. An older rule required a literal match, so voice input for Zepto/Swiggy silently fell back to the default app. The prompt asks for `""` when no app is named.  
   - **Correction memory** (`AiCorrectionMemory` logic + `AiCorrectionRepository` storage): when the user changes the pre-filled app and/or purpose on `AiConfirmationScreen` and confirms with non-blank notes, up to 2 keywords from those notes (lowercased, ≥3 letters, stopwords dropped) are saved as `keyword → app/purpose` rules. `AiTransactionProcessor.parse` applies them to every result, including the offline baseline. Priority: app named in the input → learned rule → LLM → baseline → default. Hits are counted **per field**: an app rule applies after 1 hit, a purpose rule after 2, so one "Lent to Rahul" fix can't turn "dinner with Rahul" into Lending. Changing a correction resets that field's count. The longest matching keyword wins. Capped at 100 rules, evicting lowest hits then oldest, but never the rule just taught. The confirmation fields show "Learned from your corrections" in their `supportingText`. ⚠️ **Privacy**: stored in its own DataStore (`ai_corrections`), excluded in `backup_rules.xml` and `data_extraction_rules.xml`, never written to Firestore. Keep it that way. Rules are device-wide, not per account.  
   - ⚠️ **One sentence, several expenses** ("tea 20, auto 80 and lunch 150"). `AiTransactionProcessor.parse` returns `Result<List<AiTransactionResponse>>` (one call, one daily-quota use). The prompt asks for `{"transactions":[…]}` with a `src` field per entry — the user's own words for that expense — and `AiBatchParser` (pure, tested) merges each entry: the local app-alias match runs on that entry's `src`, **not** the whole sentence (otherwise every expense inherits the first app named anywhere), while the "Google Pay is only believed if the user said it" check still looks at the whole sentence because a payment app said once applies to all. Entries with no amount are dropped from a batch (a lone one is kept so the user can fill it in); capped at `MAX_TRANSACTIONS` (10); an old flat single object still parses. Offline, `AiParser.splitExpenses` splits on commas (not the one in `1,500`), `;`, newlines, `&`/`+` and and/aur/then/also/plus, but only when ≥2 pieces carry an amount, so "lent 500 to rahul and priya" stays one. `allowMultiple = false` (a shared payment receipt is one payment) restores the single-object prompt. Learned corrections are matched per entry against its `src`.
   - **Review before saving**: one result opens the ordinary `AiConfirmationScreen`; two or more open `AiBatchConfirmationScreen`, listing every log that will be saved (tap = the same form for that log, X = remove, one "Save N logs"; disabled while a log has no amount). It converts through `AiBatch.kt` (`toNewSpend`/`toResponse`) so the list shows exactly what is saved. Saved via `SpendViewModel.addSpends` (attempts every draft, reports "Saved X of N" on partial failure) with one banner whose Undo (`undoAddSpends`) soft-deletes the whole batch. The widget/shortcut hand-off carries the list as one JSON extra (`AiResultIntent`).
   - **Lending/Borrowing**: the response carries `personName` separately from `notes`. `AiConfirmationScreen` shows a "Name of Person" field for those purposes and saves through `buildLendBorrowNotes` (`SpendCards.kt`), producing the same `"Person - note"` encoding as `AddSpendScreen`, which `parseLendBorrowNotes` reads back.

2. **AI History Assistant** (`askAiAboutHistory`) — Q&A chat over the user's full spend history.  
   - Client-side limit: 2 sessions/day × 7 messages/session (tracked in Room).  
   - A cheap `GroqModels.FAST` classifier gates off-topic questions first. It **fails open**: only a verdict containing `OFF_TOPIC` *and not* `FINANCIAL` blocks the question, so a chatty or decorated verdict never costs the user a real answer.  
   - `HistoryQuery` (pure, tested) decides what the model sees for a question: `parseScope` → `select` → rendered context. It replaced `filterSpendsByQuery`/`resolveQueryRange` in the ViewModel. **Period**: `last`/`previous`/`past` shift **back one period** (without that "spent last month?" returned **this** month), plus rolling windows ("last 7 days" = today and the 6 days before), named months ("in August", "mar 2025" — with no year, the most recent such month that has begun; "may" counts only after a preposition or with a year), and a year only when a word precedes it (a bare `2000` is an amount). Rates ("per month", "monthly") are not windows. **Purpose/app**: category-level words ("food", "rent", "travel"…) map to presets and app aliases reuse `AiParser.mentionsApp`; the old exact-preset-name match ("Groceries & Food") never fired, so every question was answered over the whole log.  
   - ⚠️ **Dues are not spending.** Lending/Borrowing rows are in scope only when the question uses a dues word (`lent`, `borrowed`, `owe`, `dues`, `udhar`…): `DuesMode.ONLY`, narrowed to a named person; `INCLUDE` ("…including lending") renders them in a separate section, never summed into spending. Otherwise they are excluded, matching the dashboard — before, a "summarise my spend" added every loan to the total.  
   - ⚠️ **"Clear chat" hides, it does not delete.** The trash-sweep icon in the sheet header calls `SpendViewModel.clearChatHistory`, which only stores a per-user `chatClearedAt` cutoff (`AiPreferencesRepository`, DataStore); the sheet shows messages with `timestamp > clearedAt`. The rows stay because the daily limit is counted from them — a real delete (tombstone) would refund the quota, the same way deleting a failed message does. Consequently the sheet is handed **every** message and the "N/7 left" chip still counts the hidden ones; the empty state also renders the status notice, since after a clear at the limit that is the only explanation for the disabled suggestions. The button is disabled while an answer is being written (a reply landing after the cutoff would sit alone in an empty chat). Hidden rows are purged by the normal 12-hour TTL; the cutoff is device-local and not synced.
   - **Suggestion chips** (`AssistantSuggestions`): one tap sends. Each pairs a localized label (shown in the chat) with an **English** question that is what `HistoryQuery.build` reads (`askAiAboutHistory(question, scopeText)`), because its keywords are English and a Hindi/Telugu label would silently scope to "all time". Every chip has a test asserting it resolves to the scope its label promises; the dues chips are hidden when the user has no dues, and all chips are disabled while an answer is being written (a second send would cancel the first). There is no "compare X vs Y" chip: the assistant answers one period at a time.
   - ⚠️ **Totals are computed in code, not by the model.** The context carries exact `TOTALS` (total/count/average/largest, by purpose, by app, by month when it spans months; for dues, lent/borrowed/net and per person) over *every* matching row, plus a capped sample of rows (`MAX_ROWS` 100, `MAX_SUMMARY_ROWS` 30 for summarise/overview questions). The old context sent up to 200 rows, computed its "Total" over that silently truncated list, and asked the model to add the rows again. The prompt tells the model to quote TOTALS and never re-add rows.  
   - If nothing matches, the context says `0 transactions match` plus a compact overview of the user's everyday spending (count, date span, by purpose, last 6 months) so the model can point at the nearest data. This replaced the fallback that dumped the 200 most recent spends: it over-fetched and answered a different question than the one asked.  
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

### Engagement features

- **Undo**: `AppNotification` takes an optional `NotificationAction`; `MainContainer` shows "Deleted/Saved/Updated · Undo" for 5s (`UNDO_WINDOW_MS`). Spend-delete undo reuses the trash restore (`restoreFromHistory`); new-save undo soft-deletes the pre-generated uuid; edit undo re-saves the previous values. Bills and notes are covered too.
- **Bill "Mark as paid"**: an action on the `RecurringBillWorker` reminder, handled in `NotificationActionReceiver` (Hilt `@EntryPoint`, `goAsync`). Logs the spend exactly like the bill sheet, is idempotent via `findMatchingSpend`, and checks the bill's userId against the signed-in user.
- **Subscription finder**: `SubscriptionFinder` (pure, tested) spots same-app/purpose/amount spends ~monthly (25–35 days apart, ≥3 payments, or 2 for named bills) and surfaces them atop `RecurringBillsScreen`; "Add" opens the existing `BillEditDialog` pre-filled. Dismissals live in their own DataStore (`SubscriptionDismissalRepository`).
- **Summary widget**: `SpendSummaryWidget` (Glance) shows today / this month / top purpose, styled like Quick Add. `SpendSummaryWidgetUpdater` refreshes it from Room's invalidation tracker on the `spends` table (debounced) plus a 00:01 WorkManager job for day rollover. The `FirebaseAuth` listener is guarded by `FirebaseApp.getApps()` because Robolectric has no FirebaseApp.
- **Weekly recap & monthly Wrapped**: `RecapWorker` runs daily at ~7 PM; `SpendRecap` (pure, tested) decides if a Sunday recap or a "Wrapped is ready" nudge (days 1–3 of the month) is due, and `RecapPreferences` dedupes per user+period. Stats exclude Lending/Borrowing, and "spent most on" ranks keywords from notes (reusing `AiCorrectionMemory.tokenize`/`STOPWORDS`, needing ≥2 spends). `WrappedSheet` opens from the notification (`EXTRA_WRAPPED_MONTH`) or Settings → Monthly Wrapped, is held behind the biometric lock, and shares the card as a PNG via the FileProvider. Everything is on-device; no AI quota is used. ⚠️ **Wrapped is announced two ways.** (1) The notification (days 1–3, evening) goes through its **own channel** `spend_wrapped` at `IMPORTANCE_DEFAULT` (the weekly recap stays on `spend_recaps`, LOW) and carries a teaser with the month's real total and top category; a channel's importance can't be raised once it exists on a device, hence a new id. (2) The **in-app row** (`WrappedBanner`, top of the Dashboard) shows on days 1–7 (`SpendRecap.WRAPPED_BANNER_DAYS`, via `bannerWrappedMonth`) when last month has spends, and does not depend on notification permission. It is deliberately a **flat row, not a card** (no fill, border or gradient; theme text colours with `primary` as the only accent — the "NEW" word and the icon), matching the rest of the Dashboard's quiet chrome; the first version was a brand-gradient card and read as too loud. It hides once the user has opened that month's Wrapped by *any* route (card, notification, Settings) or dismissed it: `MainContainer` records `userId:yyyy-MM` in `RecapPreferences.wrappedSeen`, only for the card's own month (opening another month from Settings must not count), and only once the sheet is actually composed (so not behind the app lock). `wrappedSeen` is separate from `lastMonthlyWrapped`, which only means "the notification went out".

- **Share to Spendly**: an exported `activity-alias` (`.ShareToSpendly`, label "Track in Spendly") puts the app in the share sheet for `text/plain` and targets the unexported `QuickAddInputActivity`. `SharedPaymentText` (pure, tested) strips transaction/UTR ids, UPI ids, account/phone numbers and links before the text reaches the LLM, and appends "via <app>" from the sharing app's package (`referrer`) when the text names no app. `QuickAddViewModel.processShared` falls back to the local `AiParser` baseline when AI can't run (daily limit, offline), then hands off to the AI confirmation sheet like the widget does.

### Launcher shortcuts

Long-pressing the app icon offers AI Track, Voice and Add expense (`res/xml/shortcuts.xml`, mirroring the Dashboard FAB). AI and Voice open `QuickAddInputActivity` (the widget's overlay) so the whole app isn't loaded to type a sentence; Add expense opens `MainActivity` with `OPEN_ADD_SPEND`. `QuickAddInputActivity` now asks for the microphone itself on a voice launch (the FAB asked first, the widget and shortcut did not, so a first-time user just saw a speech error) and falls back to the typed sheet if it is refused.

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
- `AiConfirmationScreen` → user confirms/edits the parsed result before saving (one expense)
- `AiBatchConfirmationScreen` → the same, as a list, when one sentence described several expenses
- `AiHistoryAssistantSheet` → chat UI for history Q&A
- `SpendingCharts` / `DashboardCards` — consume `SpendingAnalytics` derived state from the ViewModel
- `SearchField` — the shared search box on History and Dues. ⚠️ It deliberately sets **no** fixed height. `OutlinedTextField` reserves a 56dp minimum internally, so the `Modifier.height(50.dp)` the call sites used to pass did not shrink the field, it **clipped** it — which is why the box rendered visibly cut off, and why it got worse as the system font scale grew.
- `HistoryScreen` / `LendBorrowScreen` month sections — each month's header (`MonthHeader`) is a button that shows/hides that month's logs. State lives in `MonthSections.kt`: `MonthChoices` (the user's explicit open/closed result per month) + `MonthSections` (the rule). A month the user opened or closed **stays that way**; an untouched month falls back to the default (only the newest open), and *every* month is open while a search query is active (hits hidden behind a tap read as "no results"; a tap during a search closes a month for that search only). ⚠️ `MonthChoices` is created in `MainContainer` via `rememberMonthChoices()` and passed down (`historyMonthChoices`, `duesMonthChoices`), **not** inside the screen: `AnimatedContent` drops a screen's state when you navigate away, so in-screen state reopened months the user had closed. It stores the explicit open/closed result rather than a flip away from the default, because a flip reopens a closed month as soon as a newer month becomes "the newest". Dues keys are `"<tab>|<month>"` since "You lent" and "You borrowed" are separate lists. In-memory/saved-instance-state only; not persisted across app restarts.
- `ExpensePresets` / `APP_PRESETS` / `PURPOSE_PRESETS` — canonical lists used by both `AiParser` and the manual `AddSpendScreen`

### Dues (lending & borrowing)

User-facing this section is called **Dues** — the nav label, the screen title, the hero chip, the export subjects and `LendBorrowHistoryScreen`'s title all say "Dues". Only the *display* name changed: `ActiveView.LEND_BORROW`, `LendBorrowScreen`, `LendBorrowHistoryScreen` and the `"Lending"` / `"Borrowing"` purpose values are unchanged, because the purpose strings are persisted in Room and Firestore and renaming them would orphan every existing record.

Lending and borrowing are stored as regular `Spend` records with `purpose = "Lending"` or `"Borrowing"`. They are filtered out of dashboard analytics and the main history list, and shown exclusively in `LendBorrowScreen`.

**Month sections**: `LendBorrowScreen` uses the same `MonthHeader` / `MonthSections` rule as History (see Key UI components), with the tab in the key so closing a month under "You lent" does not close it under "You borrowed".

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

### UI/UX Design Standards & Clean Minimal Memory Rules (CRITICAL)

All future UI additions, screens, dialogs, cards, sheets, or component modifications MUST strictly follow these rules:

1. **Clean & Minimal Aesthetic**:
   - Keep all screens clutter-free, minimal, and focused on core user tasks.
   - Maintain clear visual hierarchy without introducing unnecessary decorative containers, redundant text, or visual noise.

2. **Strict Reuse of Existing UI/UX Patterns**:
   - **Reuse Established Components**: Use existing components (`TotalSpentHeroCard`, `EmptyStateCard`, `QuickStatsRow`, `SearchField`, `AppAvatar`, `WhereItWentCard`, `SpendCards`, `AiConfirmationScreen`, etc.) rather than introducing custom one-off designs.
   - **System Color Palette Only**: Never use arbitrary hardcoded colors or unstyled boxes. Always use `MaterialTheme.colorScheme` tokens (`surface`, `surfaceContainer`, `onSurface`, `primary`, `outlineVariant`, etc.) and established alpha levels (`0.05f`, `0.12f`, `0.85f`).
   - **Shapes & Elevation**: Use `RoundedCornerShape(Radius.sm / Radius.md / Radius.lg)` matching `Theme.kt` and `Dimens.kt`. Avoid heavy drop shadows or bright outlines — rely on subtle surface container tones and border alphas.

3. **Typography & Formatting Consistency**:
   - Use standard `MaterialTheme.typography` styles (`headlineMedium`, `titleMedium`, `labelMedium`, `bodyMedium`).
   - Format monetary figures with `.asMoney()`, `formatCurrency()`, or `formatCurrencyRounded()`.
   - Maintain `maxLines = 1` with `overflow = TextOverflow.Ellipsis` for constrained text fields.
   - Use `heightIn(min = ...)` rather than hardcoded fixed container heights to support system font scaling.

4. **Interaction Patterns & Localized Strings**:
   - Apply `rememberPressScale` and subtle haptic feedback (`HapticFeedbackType.TextHandleMove`) on interactive cards and buttons.
   - Keep touch targets at least 44–48dp (`Sizes.minTouchTarget`).
   - Store all user-visible strings in `res/values/strings.xml` (and corresponding `strings.xml` for Hindi and Telugu).

