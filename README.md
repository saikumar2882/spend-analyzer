# Spendly - Smart Spend Tracker

Spendly is a modern, privacy-focused Android application designed to help users track their daily expenses with ease. It features a robust architecture combining local storage for offline speed and cloud synchronization for data safety across devices.

## 🚀 Key Features

- **Intuitive Dashboard**: Overview of your spending with beautiful donut and bar charts.
- **Lend & Borrow**: Dedicated tracking for money lent to or borrowed from others, separated from regular expenses with monthly summaries.
- **AI Tracking**: Record expenses, lendings, and borrowings naturally using smart parsing of natural language inputs.
- **Categorized Tracking**: Organize expenses into categories like UPI Apps, Quick Commerce, Groceries, Rent, and more.
- **Detailed History**: Search and filter through your entire spending history.
- **Cloud Sync**: Automatically backup and sync your data with Firebase Firestore.
- **Offline First**: Works perfectly without internet, using Room database as the local source of truth.
- **Theming**: Supports Light, Dark, and System theme preferences.
- **Clean UI**: Minimalist design focused on readability and ease of use.
- **Biometric Security**: Secure your financial data with an app-level biometric lock.
- **App Widgets**: Quick access to tracking features directly from your home screen.
- **Safety First**: Discard confirmation dialogs ensure you don't accidentally lose your tracking progress.
- **In-App Updates**: Stay up to date with the latest features and fixes seamlessly.

## 🔐 Authentication

Spendly uses **Firebase Authentication** with two dedicated screens — a **Sign In** screen and a separate **Register** screen — that share the typed email when you switch between them.

- **Google Sign-In**: Quick access using your Google account via Android **Credential Manager**. Requires your app's SHA-1 registered in Firebase and Google Play Services on the device (emulators must use a "Google Play" system image).
- **Email/Password Registration**: On the Register screen, create an account with an email and a password (**minimum 6 characters**). A verification email is sent, and the account must be **email-verified** before you can sign in.
- **Smart sign-in errors**: If you try to sign in with an email that isn't registered, the app detects it and offers a **Register** button (with your email pre-filled) instead of showing a cryptic error.
- **Forgot Password**: Request a password-reset link from the Sign In screen.

> **Note on "email not registered" detection:** Firebase's **Email Enumeration Protection** (Console → Authentication → Settings) affects how precisely the app can tell an unknown email from a wrong password. Turn it **off** for exact "no account found → register" messages; leave it **on** (default) and the app shows a combined "incorrect email or password — register?" prompt (still with a Register button).

## 🛠 Tech Stack

- **UI**: Jetpack Compose (Modern Declarative UI)
- **Dependency Injection**: Hilt
- **Local Database**: Room (with multi-user support)
- **Backend/Cloud**: Firebase (Auth & Firestore)
- **AI Integration**: Groq (Llama 3.x) as the primary parser with **Gemini (3.5 Flash)** as fallback; a local heuristic `AiParser` baseline ensures it works even when the AI is unavailable. Keys are loaded at runtime from Firebase Remote Config (never baked into the APK).
- **Background Tasks**: WorkManager for cloud synchronization
- **App Widgets**: Jetpack Glance
- **Security**: Android Biometric API & Credential Manager
- **Updates**: Google Play In-App Updates **and** a GitHub Releases checker (prompts once per new version)
- **Architecture**: MVVM (Model-View-ViewModel)
- **Asynchronous**: Kotlin Coroutines & Flow

## 📖 Setup & Configuration

### Prerequisites
- [Android Studio Ladybug](https://developer.android.com/studio) or newer.
- A Firebase Project.

### Local Setup
1. **Clone the project**:
   ```bash
   git clone https://github.com/saikumar2882/spend-analyzer.git
   ```
2. **Firebase Configuration**:
   - In the Firebase Console, register your app's **SHA-1 fingerprint** (Project Settings → your Android app → Add fingerprint). This is required for Google Sign-In — without it, Credential Manager fails with *"No credentials available."* Get your debug SHA-1 with `./gradlew signingReport`.
   - Enable **Google** and **Email/Password** under Authentication → Sign-in method.
   - Download the resulting `google-services.json` (its `oauth_client` array should be **non-empty**) into the `app/` directory.
   - Ensure `strings.xml` has your **Web** OAuth client id in `default_web_client_id`.
   - (Optional) Disable **Email Enumeration Protection** under Authentication → Settings for precise "email not registered → register" detection.
3. **Environment Variables**:
   - Create a `.env` file based on `.env.example`.
4. **Build & Run**:
   - Open in Android Studio and run on your emulator or device.

## 🛡 Security Rules (Firestore)

Ensure your Firestore rules are set to protect user data:

The repo ships a complete `firestore.rules`. Every subcollection is owner-scoped (a user can only access their own data), and `spends` writes are additionally schema-validated:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function isAuthenticated() { return request.auth != null; }
    function isOwner(userId) { return request.auth.uid == userId; }

    match /users/{userId} {
      match /spends/{spendId} {
        allow read, delete: if isAuthenticated() && isOwner(userId);
        allow create, update: if isAuthenticated() && isOwner(userId)
          && request.resource.data.amount is number
          && request.resource.data.appName is string
          && request.resource.data.purpose is string
          && request.resource.data.userId == request.auth.uid;
      }
      match /recurring_bills/{billId} { allow read, write: if isAuthenticated() && isOwner(userId); }
      match /history/{historyId}      { allow read, write: if isAuthenticated() && isOwner(userId); }
      match /chat_messages/{msgId}    { allow read, write: if isAuthenticated() && isOwner(userId); }
    }
  }
}
```

## 🔄 App Workflow

### Workflow Description
1.  **Authentication**: New users **Register** (email + password, with email verification) or continue with Google; returning users **Sign In**. Google uses Credential Manager. A unique `userId` is assigned to maintain data privacy.
2.  **Security**: If enabled, the app prompts for biometric authentication before granting access to the dashboard.
3.  **Dashboard**: The app fetches local data from **Room DB** to show immediate analytics.
4.  **Data Entry**:
    *   **Manual**: User fills out amount, app, and purpose.
    *   **AI (Smart)**: User types a natural sentence (e.g., "Paid 500 for lunch via GPay"). An **LLM (Groq, with Gemini fallback)** refines a local heuristic parse into structured data.
5.  **Processing**: Data is first saved to the local **Room Database** (Offline-first).
6.  **Synchronization**: A background **SyncWorker** (via WorkManager) ensures local data is periodically backed up to **Firebase Firestore**.
7.  **Insights**: The **Analytics Engine** groups data to generate donut/bar charts and provides an **AI Chat Assistant** for historical queries.

### Flowchart

![App Workflow](./app/src/main/assets/img.png)

## 📖 User Manual

### Introduction
**Spend Tracker** is a comprehensive personal finance management tool designed to help you monitor your expenses, visualize spending habits, and manage debts with the help of AI-powered insights.

### 1. Getting Started
#### Login & Security
*   **Register**: New here? Tap **Register** to create an account with your email and a password (at least 6 characters), or continue with Google. You'll receive a verification email — confirm it, then sign in.
*   **Sign In**: Returning users log in with email and password. If you enter an email that isn't registered, the app offers to take you straight to the Register screen.
*   **Reset Password**: If you forget your password, use the "Forgot Password" option on the Sign In screen to receive a reset link via email.
*   **Update Password**: Change your password anytime from the **Dashboard > Security Settings** (shield icon).

### 2. Dashboard Overview
The Dashboard is your financial control center:
*   **Spending Summary**: View your total spent and transaction count for a specific period.
*   **Time Filters**: Quickly switch between Today, Week, Month, Year, or a **Custom Date Range**.
*   **Category Breakdown**: An interactive donut chart showing which categories (Food, Shopping, etc.) consume your budget.
*   **Spending Trends**: A bar chart visualizing your spending patterns over time.
*   **Top Apps**: See which applications or wallets (like Swiggy, Amazon, Zomato) you use the most.
*   **Recent Activity**: A quick glance at your latest transactions.

### 3. Managing Transactions
#### Logging a New Expense
1.  **Manual Entry**:
    *   Tap the **Add** button.
    *   **Amount**: Enter the exact amount spent.
    *   **App/Wallet**: Select from presets or choose "Other".
    *   **Date**: Defaults to today, but can be customized.
    *   **Purpose**: Select a category or type a custom one.
    *   **Notes**: Add optional details.
2.  **Quick AI Entry**:
    *   In the **Add** screen (or through the AI prompt), you can simply type or say what you spent.
    *   Example: *"Spent 300 on biryani using PhonePe"*
    *   The AI will automatically parse the amount, app, and purpose for you!
    *   **Daily Limit**: Note that AI processing has a daily request limit shown in the input box.

#### Editing & Deleting
*   Navigate to **History** or **Lend/Borrow**.
*   Tap on any transaction card to **Edit** its details.
*   Long-press or use the delete action to remove a transaction after confirmation.

### 4. History & Advanced Filtering
View every rupee spent in the **History Screen**:
*   **Search**: Use the search bar to find transactions by App Name, Purpose, or Notes.
*   **Filter**: Use category chips to isolate specific types of spending (e.g., only "Food").
*   **Monthly Grouping**: Transactions are automatically grouped by month with sub-totals for easy tracking.

### 5. Lending & Borrowing
Keep track of money owed or borrowed:
*   Use the **Lend & Borrow** screen to separate these special transactions.
*   Toggle between the **Lending** and **Borrowing** tabs to see your current balances.

### 6. AI History Assistant
Leverage AI to understand your finances:
*   Tap the **AI Assistant** (sparkle icon) on any major screen.
*   Ask questions like "How much did I spend on food last month?" or "What are my biggest expenses?"
*   Get conversational insights and summaries of your financial data.

### 7. Personalization & Security
*   **Theme Switching**: Cycle between **Light Mode**, **Dark Mode**, and **System Default** using the theme icon on the Dashboard.
*   **Biometric Lock**: Enable "App Lock" in settings to require Fingerprint or Face ID whenever you open the app.
*   **Notifications**: The app provides real-time feedback for successful saves, errors, and security updates.

### 8. Updates
*   **Auto-Update**: The app checks for newer versions on both the Play Store and GitHub. The GitHub prompt appears **only once per new version** — if you tap "Later" or "Download", that version won't nag you again; you'll only be prompted when a strictly newer release is published.

### 9. Tips for Success
*   **Be Descriptive**: Use the "Notes" field to remember specific details about unusual expenses.
*   **Review Weekly**: Use the "This Week" filter every Sunday to stay on top of your budget.
*   **Categorize Correctly**: Consistently using the same categories makes the "Category Breakdown" chart more accurate.
