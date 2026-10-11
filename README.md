# Spendly - Smart Spend Tracker (v2.3.5)

Spendly is a modern, privacy-first Android application designed to help users track daily expenses with intelligence, speed, and uncompromising data security. It combines offline-first local storage (Room DB v22) with end-to-end encrypted cloud synchronization (Firebase Firestore), AI-powered natural language expense parsing, voice recognition, and advanced financial analytics.

---

## 🚀 Key Features

- **End-to-End Encrypted Cloud Sync (E2EE)**: Zero plain-text cloud exposure. Financial logs, dues, bills, notes, and chat history are encrypted on-device using AES-GCM (`CloudEnvelope`) before syncing to Firestore.
- **4-Mode Privacy AI Engine**: Combines an Offline Baseline (`AiParser`), Local Correction Memory (`AiCorrectionMemory`), PII Sanitizer (`PiiSanitizer`), and AES-GCM Cloud Encryption for total data confidentiality.
- **Resilient Voice Expense Input**: Hands-free expense logging via natural speech with real-time waveform visualization, microphone permission auto-checks, Telugu/Hindi script recognition, and typed fallback.
- **Multi-Expense Batch Parsing**: Process complex multi-item entries (e.g., *"tea 20, auto 80 and lunch 150"*) in a single AI request with per-item confirmation and batch undo.
- **AI History Assistant (Financial Q&A)**: Interactive conversational assistant to query your full spending history (e.g., *"How much did I spend on food last month?"* or *"Who owes me money?"*).
- **Intuitive Dashboard & Monthly Budget Editor**: Modern financial overview with donut/bar charts, top apps, recent activity, and custom monthly budget tracking with real-time over-spending alerts.
- **Lend & Borrow (Dues)**: Dedicated tracking for money lent to or borrowed from contacts, isolated from regular expenses with dedicated balance summaries.
- **Notes & Recycle Bin**: Create customized notes with transaction entries, edit history tracking, 30-day soft-delete trash bin with restore capabilities, and single-tap *"Log Note as Transaction"* conversion.
- **PDF, CSV & Image Export**: Export spending logs and dues into structured PDF financial reports, CSV spreadsheets, or shareable visual PNG summary cards.
- **Subscription Finder & Recurring Bills**: Automatically detects ~monthly recurring payment patterns and provides double notification reminders (12:30 PM & 10:00 PM) with an idempotent *"Mark as Paid"* quick action.
- **Monthly Wrapped & Weekly Recaps**: On-device financial story recaps generated automatically with shareable visual cards (100% private, zero AI quota used).
- **Multi-Language Support**: Fully localized in **English**, **Hindi (हिन्दी)**, and **Telugu (తెలుగు)**.
- **Adaptive Theme & Layouts**: Supports Light, Dark, and **OLED Pitch-Black** modes, with responsive layouts for phones, foldables, and tablets (`CompactNavBar`, `CategorySelectionGrid`).
- **Widgets, Shortcuts & Share Sheet**: Home screen widgets (`QuickAddWidget`, `SpendSummaryWidget`), launcher long-press shortcuts, and *"Share to Spendly"* text sharing.
- **Biometric Security & Auth**: Google Sign-In via Credential Manager, Email/Password registration with mandatory email verification, and biometric app lock.
- **In-App & GitHub Updates**: Seamless Google Play updates plus GitHub Releases checker with per-version prompt suppression.

---

## 🔐 Authentication & Privacy Security

Spendly uses **Firebase Authentication** coupled with Android **Credential Manager** and an inline two-screen auth flow (`LoginScreen` & `RegisterScreen`):

- **Google Sign-In**: Powered by Credential Manager. Requires registering your SHA-1 fingerprint in the Firebase Console and Google Play Services on the device.
- **Email/Password Registration**: Create an account with email and password (minimum 6 characters). Email verification is strictly required before accessing the dashboard.
- **Smart Sign-In Error Mapping**: Detects unregistered email attempts and offers a direct **Register** action with pre-filled credentials.
- **End-to-End Encryption (E2EE)**: Account-level encryption policy stored at `users/{uid}/profile/security`. AES-GCM envelope payload seals all sensitive fields while retaining explicit plaintext metadata (`updatedAt`, `deleted`, `recordedAt`, `timestamp`, `historyType`) for LWW conflict resolution and tombstone management.
- **PII Sanitizer**: Automatically strips personal names, phone numbers, account numbers, and UPI IDs from natural text before forwarding queries to cloud AI models.

---

## 🛠 Tech Stack

- **UI**: Jetpack Compose (Modern Declarative UI with Material 3 tokens, Adaptive Layouts, and Motion design)
- **Dependency Injection**: Hilt
- **Local Database**: Room (**v22**) with multi-user isolation, soft-delete tombstones, and explicit migrations (14→22)
- **Backend/Cloud**: Firebase Auth & Firestore with AES-GCM E2EE Cloud Envelope payload
- **AI Integration**: Groq (**GPT-OSS 20B/120B Reasoning models**) primary parser with **Gemini 3.5 Flash** fallback; local `AiParser` baseline for offline usage. Runtime keys provided via Firebase Remote Config.
- **Background Operations**: WorkManager (`SyncWorker`, `RecurringBillWorker`, `RecapWorker`)
- **App Widgets**: Jetpack Glance (`QuickAddWidget`, `SpendSummaryWidget`)
- **Security**: Android Biometric API, Credential Manager, AES-GCM Encryption (`CloudEnvelope`)
- **Architecture**: MVVM (Model-View-ViewModel) with StateFlow, Kotlin Coroutines, and single-Activity state-based navigation

---

## 📖 Setup & Configuration

### Prerequisites
- [Android Studio Ladybug](https://developer.android.com/studio) or newer (compileSdk 37, targetSdk 37).
- A Firebase Project with Auth & Firestore enabled.

### Local Setup
1. **Clone the repository**:
   ```bash
   git clone https://github.com/saikumar2882/spend-analyzer.git
   ```
2. **Firebase Setup**:
   - Register your app's debug/release **SHA-1 fingerprint** in Firebase Console.
   - Download `google-services.json` into the `app/` folder.
   - Enable **Google** and **Email/Password** under Authentication → Sign-in method.
3. **Build & Run**:
   - Open in Android Studio and run on device or emulator:
     ```bash
     ./gradlew assembleDebug
     ```

---

## 🛡 Security Rules (Firestore)

Owner-scoped rules protecting user subcollections and validating E2EE/Metadata schemas:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function isAuthenticated() { return request.auth != null; }
    function isOwner(userId) { return request.auth.uid == userId; }

    match /users/{userId} {
      match /profile/security {
        allow read, write: if isAuthenticated() && isOwner(userId);
      }
      match /spends/{spendId} {
        allow read, delete: if isAuthenticated() && isOwner(userId);
        allow create, update: if isAuthenticated() && isOwner(userId);
      }
      match /recurring_bills/{billId} { allow read, write: if isAuthenticated() && isOwner(userId); }
      match /history/{historyId}      { allow read, write: if isAuthenticated() && isOwner(userId); }
      match /chat_messages/{msgId}    { allow read, write: if isAuthenticated() && isOwner(userId); }
      match /notes/{noteId}           { allow read, write: if isAuthenticated() && isOwner(userId); }
      match /note_entries/{entryId}   { allow read, write: if isAuthenticated() && isOwner(userId); }
      match /note_history/{histId}    { allow read, write: if isAuthenticated() && isOwner(userId); }
    }
  }
}
```

---

## 🔄 App Workflow

```
[User Input: Manual / Voice / Batch AI / Share Sheet]
                         │
                         ▼
             [PII Sanitizer & Baseline] ──(Offline)──► [Room Local DB v22]
                         │                                      │
                         ▼ (Cloud AI / Groq / Gemini)           │
              [Parsed Result & Correction Memory]               │
                         │                                      │
                         ▼                                      ▼
               [User Confirmation] ───────────────► [AES-GCM Envelope Encryption]
                                                                │
                                                                ▼
                                                      [Firestore Cloud Sync]
```

---

## 📖 Comprehensive User Manual

### 1. Dashboard, Budget Editor & Theme Options
* **Spending Summary**: Overview of total spent, transaction count, top payment apps, and category donut/bar breakdowns.
* **Time Filters**: Switch between Today, Week, Month, Year, or a Custom Date Range.
* **Monthly Budget Editor**: Tap the budget card on the Dashboard to set target limits per category/month and monitor real-time spending progress with warning indicators.
* **OLED Dark Mode**: Choose between Light, Dark, and OLED Pitch-Black (`#000000`) modes from the Dashboard toolbar or Settings.

### 2. Multi-Mode AI & Voice Expense Logging
* **Manual Entry**: Tap **Add** to select categories via `CategorySelectionGrid` with search and preset shortcuts.
* **Voice Logging**: Tap the microphone icon on the FAB or overlay to speak naturally (e.g., *"Spent 350 for dinner on Swiggy"*). Includes real-time waveform visualization, mic permission auto-prompts, and Telugu/Hindi translation support.
* **Batch Logging**: Speak or type multi-expense sentences (*"Tea 20, Cab 150 and Lunch 300"*). The AI splits them into individual logs for review.
* **Correction Memory**: Edits made on the confirmation screen automatically teach local keyword rules saved in DataStore, improving future AI accuracy without sending data off-device.

### 3. AI History Assistant (Financial Q&A)
* **Ask Anything**: Tap the AI Assistant (sparkle icon) on any main screen to ask natural questions like *"How much did I spend on food in August?"* or *"Who owes me money?"*.
* **Code-Calculated Totals**: Financial totals, counts, and averages are calculated in code for 100% mathematical precision rather than generated by the AI model.
* **Soft Clear Chat**: Tap the clear chat icon to hide previous messages while preserving daily rate-limit quotas.

### 4. Dues (Lending & Borrowing)
* **Separate Tracking**: Track money lent to or borrowed from contacts isolated from regular personal spending.
* **Person Summaries**: View total balances grouped by contact name with monthly expandable sections.
* **Settle & Convert**: Quickly update, settle, or delete dues entries.

### 5. Notes & Recycle Bin (Trash)
* **Custom Notes**: Group related transaction items into named collections with custom fields and colors.
* **Log as Transaction**: Convert an entire note's total into a tracked spend record with a single tap.
* **Trash Recycle Bin**: Deleted spends, dues, and notes are held in the Trash Bin for 30 days before permanent deletion, allowing full restore at any time.

### 6. Subscription Finder & Recurring Bills
* **Subscription Detection**: Automatically analyzes history to spot same-app/purpose/amount transactions occurring ~monthly and suggests adding them as recurring bills.
* **Due Bill Reminders**: Sends double notifications at 12:30 PM and 10:00 PM on due dates with a one-tap *"Mark as Paid"* quick action.

### 7. Monthly Wrapped & Weekly Recaps
* **Sunday Recaps**: Receive a weekly spending summary nudge every Sunday evening.
* **Monthly Wrapped**: Days 1–7 of each month feature an in-app `WrappedBanner` and notification story highlighting top categories, total spending, and key trends with shareable PNG cards.

### 8. Reports & Exports
* **PDF Reports**: Export comprehensive financial reports formatted for printing or sharing.
* **CSV Spreadsheets**: Download raw transaction history into standard CSV format.
* **PNG Share Cards**: Share individual spend summaries or Wrapped cards as high-resolution images.

### 9. Widgets, Shortcuts & Share Sheet
* **Home Screen Widgets**: Access `QuickAddWidget` for fast AI logging and `SpendSummaryWidget` for live daily/monthly totals.
* **Launcher Shortcuts**: Long-press the app icon for instant access to AI Track, Voice Input, and Add Expense.
* **Share to Spendly**: Share payment SMS/receipt text from any app (or UPI app) directly to Spendly via the Android system share sheet.

### 10. Security & Privacy
* **Biometric Lock**: Enable App Lock in Settings to protect financial data with Fingerprint or Face ID on app launch.
* **E2EE Cloud Sync**: Toggle End-to-End Encryption in Settings to seal all Firestore cloud records with AES-GCM local encryption.
* **In-App Privacy Policy**: View the complete privacy policy anytime via **Settings > Privacy Policy**.
