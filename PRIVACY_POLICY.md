# Privacy Policy for Spendly

**Effective Date:** May 2024  
**Last Updated:** March 2025  

Welcome to **Spendly** ("we", "our", or "us"). We are committed to protecting your privacy and ensuring your personal financial data remains secure and confidential. This Privacy Policy explains how Spendly handles, collects, uses, and safeguards your information when you use our mobile application.

Spendly is designed as a **privacy-first, offline-capable expense tracking application**. We believe your financial data belongs strictly to you.

---

## 1. Information We Collect

### A. Personal & Account Information
* **Authentication Credentials:** When you register or sign in, we collect your email address and an encrypted hash of your password via Firebase Authentication. If you sign in using Google Sign-In, we receive your email address, display name, and profile photo URL from your Google account.
* **User Identifier (UID):** A unique alphanumeric string assigned to your account to associate your data with your account securely.

### B. Financial & Expense Data
* **Transaction Records:** Expense amounts, income, dates, payment categories, merchant/app names (e.g., GPay, Swiggy, Zomato), expense descriptions/purposes, notes, and recurring bill information.
* **Lend & Borrow Records:** Records of money you have lent to or borrowed from others, including contact names and amounts.

### C. Natural Language & Voice Data
* **Text Inputs:** Natural language entries you type into the smart expense tracker or AI chat assistant (e.g., *"Paid 500 for lunch via GPay"*).
* **Voice Inputs:** Audio converted to text via Android's Speech Recognition Service when using the voice input feature. **Note:** Spendly does not store raw audio recordings on our servers or on your device.

### D. Device & App Information
* App version, operating system version, and general system language settings used solely to ensure app compatibility and deliver localized content.

---

## 2. How We Use Your Information

We use the collected information strictly to provide, maintain, and improve Spendly's core features:
* **Expense Management:** To record, calculate, group, and visualize your daily, monthly, and category-wise spending habits.
* **Cloud Synchronization:** To securely back up and sync your financial entries across your devices when signed in.
* **AI Parsing & Insights:** To convert natural language inputs into structured expense entries and answer queries about your spending history.
* **Notifications:** To send reminders for recurring bills and periodic spending recaps.
* **Security:** To authenticate your account and restrict access to authorized devices.

---

## 3. Privacy-First AI & Data Protection Technologies

Spendly incorporates industry-leading privacy protection mechanisms for AI interactions:

### A. PII Sanitizer (Personally Identifiable Information Redaction)
Before any natural language expense query is transmitted to external AI processing services (Groq API or Google Gemini API), Spendly's built-in **PII Sanitizer** automatically detects and scrubs sensitive identifiers:
* **Card & Bank Account Numbers:** (12–16 digit numbers replaced with anonymized tokens e.g. `[CARD_1]`).
* **UPI IDs:** (e.g., `user@bank` replaced with tokens e.g. `[UPI_1]`).
* **Phone Numbers:** Replaced with tokens e.g. `[PHONE_1]`.
* **Personal Names:** Detected names replaced with tokens e.g. `[PERSON_1]`.

Once the AI processes the sanitized response, tokens are restored strictly on your local device.

### B. On-Device Local Models
Where supported by your device, Spendly prioritizes local on-device AI inference, keeping your text input entirely on your hardware without sending any data over the internet.

### C. Client-Side End-to-End Encryption (E2EE)
You can enable **End-to-End Cloud Privacy** in Settings:
* When enabled, your financial data is encrypted locally on your device using **AES-256-GCM** before being synced to cloud storage.
* Unencrypted financial data is never accessible to database administrators or third-party cloud services.

---

## 4. How We Store & Secure Your Data

* **Offline-First Storage:** All your data is stored locally on your device in a sandboxed **Room Database**. Spendly works fully offline without requiring an active internet connection.
* **Cloud Security (Firebase Firestore):** Cloud sync is governed by strict owner-isolated security rules (`request.auth.uid == userId`). Only your authenticated user account can read, write, or access your stored cloud database subcollections.
* **Biometric Lock:** You can lock the application behind Android Biometric Authentication (Fingerprint / Face Unlock / Device PIN) for app-level physical security.

---

## 5. Third-Party Services & Data Sharing

**We DO NOT sell, rent, monetize, or trade your personal or financial data to advertisers, data brokers, or third parties.**

To provide essential application functionalities, Spendly interacts with the following trusted third-party service providers:

| Provider | Purpose | Data Handled | Privacy Policy Link |
| :--- | :--- | :--- | :--- |
| **Firebase (Google LLC)** | Account Authentication & Cloud Database Sync | Email, Encrypted Credentials, Encrypted/Owner-scoped Spend Data | [Firebase Privacy Policy](https://firebase.google.com/support/privacy) |
| **Groq Inc.** | Primary AI Natural Language Parsing | Sanitized Text Inputs (PII Scrubbed) | [Groq Privacy Policy](https://groq.com/privacy-policy/) |
| **Google Gemini API** | Fallback AI Natural Language Parsing | Sanitized Text Inputs (PII Scrubbed) | [Google Privacy Policy](https://policies.google.com/privacy) |
| **Google Play / Speech Services** | Voice-to-Text Recognition | Real-time speech input (processed transiently) | [Google Privacy Policy](https://policies.google.com/privacy) |

---

## 6. Permissions Required & Justifications

Spendly requests the following Android permissions:

* `INTERNET`: Required for user authentication, cloud sync, and AI natural language processing.
* `ACCESS_NETWORK_STATE`: Used to verify network connectivity prior to triggering sync operations.
* `RECORD_AUDIO`: Used exclusively when you tap the voice microphone icon to dictate expenses. Audio is processed transiently by Android's speech service and is not recorded to disk.
* `POST_NOTIFICATIONS`: Used to alert you about upcoming recurring bills and periodic expense recaps.

---

## 7. Data Retention & Account Deletion

* **Local Data:** You can delete individual transaction entries, clear app storage, or uninstall the app to permanently erase all locally stored data.
* **Cloud Data & Account Deletion:** You can delete your records or request account deletion by navigating to **Profile / Settings** in the app or contacting support. Upon account deletion, all cloud-stored Firestore records associated with your UID are permanently purged.

---

## 8. Children's Privacy

Spendly is not directed to children under the age of 13 (or under 16 in certain jurisdictions). We do not knowingly collect personal information from children. If you believe a child has provided us with personal data, please contact us so we can immediately delete such information.

---

## 9. Your Rights & Choices

You have the right to:
* Access, export, or edit your financial data directly within the app.
* Toggle **End-to-End Encryption** on or off in Settings.
* Enable or disable **Biometric Security**.
* Grant or revoke optional permissions (Microphone, Notifications) at any time through Android System Settings.

---

## 10. Changes to This Privacy Policy

We may update this Privacy Policy periodically to reflect app updates or legal requirements. We will notify users of any significant changes by updating the "Last Updated" date at the top of this policy or through an in-app notice.

---

## 11. Contact Us

If you have any questions, concerns, or requests regarding this Privacy Policy or data security in Spendly, please contact us:

* **Email:** support@spendly.app *(or your preferred developer contact email)*
* **GitHub Repository:** [https://github.com/saikumar2882/spend-analyzer](https://github.com/saikumar2882/spend-analyzer)
