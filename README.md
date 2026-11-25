# FINORA

Native Android personal finance app built from scratch using the uploaded FINORA web app as its visual reference. The app uses the supplied Android registration for project `finoraaaaaa` and application ID / namespace `com.finora.app`.

## Build and sign in

Install JDK 17 and Android SDK Platform 35. Place the supplied Firebase Android client configuration at `app/google-services.json` and run `./gradlew testDebugUnitTest assembleDebug`. Install the output with `adb install -r app/build/outputs/apk/debug/app-debug.apk`. The checked-in build under `apk/` includes the supplied client registration. A build without registration opens a setup message.

1. Enable Email/Password in Firebase Authentication. Create a Firestore database and deploy the owner-scoped rules and index with `firebase deploy --project finoraaaaaa --only firestore`. Review existing rules before deploying to an existing database.
2. For Google login, register the signing key's SHA-1 and SHA-256 from `./gradlew signingReport`, enable the Google provider and download the refreshed Android configuration with its web OAuth client. The originally supplied JSON has no OAuth clients.
3. Register the app for Play Integrity before enforcing Firebase App Check. Test enforcement on a Play-distributed, signed build. Keep production signing material outside this repository.
4. For Actions, set repository secret `FIREBASE_ANDROID_JSON` to the client JSON. The pipeline without it exercises the setup-state build. Download the APK artifact from a successful build.

## Implemented flows

Firebase sign-in and account creation, optional multi-bank selection, five-tab navigation, Firestore-backed income and expense editing, monthly grouping, search, budget progress, opening balance, period and category analysis, record-based assistant questions, themes, savings contributions, dated bills and subscriptions, payment confirmation, local notification review and Parent Connect preferences. All amounts are stored as integer paise. Time periods in the dashboard use the device time zone. The primary bank is the first selected bank. A calculated balance requires a user-entered opening balance.

The listener checks four candidate banking package IDs, ignores other senders, rejects authentication content and ambiguous amounts, and stores only structured candidates locally for review. Device coverage and bank notification formats require field testing. Entries can be discarded locally or deleted from Transactions after import. Marking a bill paid writes its expense and status in a single Firestore transaction.

## Integration and verification limits

Recipient verification and sending run in the separate FINOR-RABDER service. The Android app never holds Meta or Firebase Admin credentials. Bank Account Aggregator integration requires a contracted provider. The analytics assistant uses deterministic calculations. App restriction, live balance sync, full transaction reconciliation, notification-based reminder delivery, PDF reporting and automatic recurring bill generation are not part of this build. Live Google authentication, real bank notification formats, production Firebase permissions and WhatsApp delivery require deployment/device verification.
