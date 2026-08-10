# Security hardening changelog

## 2026-08-10

- Added an HTTPS-only Network Security Config.
- Added Android overlay/tapjacking and recents-thumbnail protections.
- Made medicine reminder notifications private on the lock screen.
- Removed medicine names/custom labels from persisted WorkManager reminder input.
- Neutralized spreadsheet-formula prefixes in CSV exports.
- Added an Android security regression guard to protected CI.
- Clarified widget/export privacy in Settings.
- Added adaptive/themed launcher assets and a dedicated monochrome notification icon.
- Documented Firebase App Check/Play Integrity as the next staged backend-abuse hardening step instead of weakening dependency locking or falsely claiming enforcement before Firebase registration.

App Check remains deliberately open until Firebase Console registration, a reviewed dependency-lock regeneration, signed-device metric validation, and explicit Cloud Firestore/Authentication enforcement are completed.
