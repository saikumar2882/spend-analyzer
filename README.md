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
- **Safety First**: Discard confirmation dialogs ensure you don't accidentally lose your tracking progress.

## 🔐 Authentication

Spendly offers multiple secure ways to sign in, all handled via **Firebase Authentication**:

- **Google Sign-In**: Quick, one-tap access using your Google account.
- **Email/Password**: Traditional registration and login with strict security requirements:
    - Minimum 8 characters.
    - At least one Uppercase, one Lowercase, one Number, and one Special Symbol.
- **Passwordless Email Link**: Sign in securely by clicking a magic link sent to your inbox—no password required.

## 🛠 Tech Stack

- **UI**: Jetpack Compose (Modern Declarative UI)
- **Local Database**: Room (with multi-user support)
- **Backend/Cloud**: Firebase (Auth & Firestore)
- **AI Integration**: Gemini AI parsing for smart tracking
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
   - Add your `google-services.json` to the `app/` directory.
   - Enable **Google** and **Email/Password** (with Email Link) in the Firebase Auth console.
   - Update `strings.xml` with your `default_web_client_id`.
3. **Environment Variables**:
   - Create a `.env` file based on `.env.example`.
4. **Build & Run**:
   - Open in Android Studio and run on your emulator or device.

## 🛡 Security Rules (Firestore)

Ensure your Firestore rules are set to protect user data:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/spends/{spendId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```
