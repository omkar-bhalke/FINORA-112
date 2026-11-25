# FINORA

Native Android personal finance app built from scratch using the uploaded FINORA web app as its visual reference. The app uses the supplied Android registration for project `finoraaaaaa` and application ID / namespace `com.finora.app`.

## Build and sign in

Install JDK 17 and Android SDK Platform 35. The Firebase Android client registration is included at `app/google-services.json`. Run `./gradlew testDebugUnitTest assembleDebug`. Install the output with `adb install -r app/build/outputs/apk/debug/app-debug.apk`. The checked-in build under `apk/` includes the supplied client registration. Gradle requires that registration to build the APK.

1. Enable Email/Password in Firebase Authentication. Create a Firestore database and deploy the owner-scoped rules and index with `firebase deploy --project finoraaaaaa --only firestore,storage`. Review existing rules before deploying to an existing database.
2. For Google login, register the signing key's SHA-1 and SHA-256 from `./gradlew signingReport`, enable the Google provider and download the refreshed Android configuration with its web OAuth client. The originally supplied JSON has no OAuth clients.
3. Register the app for Play Integrity before enforcing Firebase App Check. Test enforcement on a Play-distributed, signed build. Keep production signing material outside this repository.
4. GitHub Actions uses the same Android client registration as the local build and tests the signed-out startup screen. Download the APK artifact from a successful build.

## Implemented flows

Firebase sign-in and account creation, optional multi-bank selection, five-tab navigation, Firestore-backed income and expense editing, monthly grouping, search, budget progress, opening balance, period and category analysis, record-based assistant questions, themes, savings contributions, WorkManager reminders, recurring bills and subscriptions, CSV/PDF exports, custom categories, manual bank accounts, payment confirmation, local notification review and Parent Connect preferences. All amounts are stored as integer paise. Time periods in the dashboard use the device time zone. The primary bank is the first selected bank. A calculated balance requires a user-entered opening balance.

The listener checks twelve allowlisted banking package IDs, ignores other senders, rejects authentication content and ambiguous amounts, and stores only structured candidates locally for review. Device coverage and bank notification formats require field testing. Entries can be discarded locally or deleted from Transactions after import. Marking a bill paid writes its expense and status in a single Firestore transaction.

## Integration and verification limits

Recipient verification and sending run in the separate FINORA-RABDER service. The Android app never holds Meta or Firebase Admin credentials. Bank Account Aggregator integration requires a contracted provider. The assistant uses deterministic ledger calculations. Spending control operates on launches through FINORA. Device-wide app restriction, live bank synchronization and cross-source statement reconciliation are unavailable. Live Google authentication, real bank notification formats, production Firebase permissions and WhatsApp delivery require deployment/device verification.

## Banking and reporting setup

The Account Aggregator route requires an eligible regulated Financial Information User and an onboarded provider. Confirm the partnership and consent scope before implementing account discovery, authorization, retrieval and revocation with that provider. See [Sahamati onboarding](https://sahamati.org.in/how-to-join-the-account-aggregator-network-to-share-and-access-financial-data/) and the [participant directory](https://sahamati.org.in/providers/).

Set Gradle property `finoraApiUrl` to the deployed FINORA-RABDER HTTPS origin before building to enable verification, delivery status and server-side account deletion. Users can save report preferences before deployment. Approval of templates and recipient consent must precede live sending.

The checked-in APK's debug signing certificate has SHA-1 `45:FF:5B:C2:C8:73:19:E6:D3:4A:82:DD:69:AD:95:F3:F6:8F:56:EE`. Other machines and CI generate different debug keys. Register the fingerprint for each installation you test.

## Verification

Run `./gradlew testDebugUnitTest assembleDebug`. With a Firestore emulator listening on localhost:8181 using project demo-finora and this repository's rules, run `python3 tests/firestore_rules.py` to check user isolation, blocked production permits, and invalid amounts. Bank eligibility, notification access and reminder timing require physical-device acceptance testing. The Android scheduler is best-effort under OS power management.

See [Parent Connect deployment](PARENT-CONNECT.md) for Render, Firebase Realtime Database and WhatsApp setup. The APK supports runtime service discovery at `serviceConfig/reportApiUrl`.
