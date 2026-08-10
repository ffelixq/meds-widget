# Security hardening changelog

## 2026-08-10

- Added Firebase App Check Play Integrity client support for configured release builds.
- Added an HTTPS-only Network Security Config.
- Added Android overlay/tapjacking and recents-thumbnail protections.
- Made medicine reminder notifications private on the lock screen.
- Removed medicine names/custom labels from persisted WorkManager reminder input.
- Neutralized spreadsheet-formula prefixes in CSV exports.
- Added an Android security regression guard to protected CI.
- Clarified widget/export privacy in Settings.
- Added adaptive/themed launcher assets and a dedicated monochrome notification icon.

Server-side App Check enforcement is intentionally not listed as complete until Firebase Console metrics confirm legitimate signed App Distribution traffic and enforcement is explicitly enabled for Cloud Firestore and Authentication.
