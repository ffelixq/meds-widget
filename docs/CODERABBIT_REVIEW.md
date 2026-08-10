# CodeRabbit-style review summary

This file is intentionally short; the detailed threat model and remediation evidence are in `SECURITY_REVIEW_2026-08-10.md`.

## P0 — critical

- **None identified.**

## P1 — high

- **Firebase App Check not enforced:** intentionally staged as a follow-up because it needs Firebase Console registration plus a reviewed Gradle lockfile update before client rollout. Authentication + strict Firestore Rules remain the current authorization boundary.
- **Medication details on lock screen / WorkManager storage:** private notification visibility/public fallback added; medicine names and custom labels removed from scheduled WorkManager input.

## P2 — medium

- **CSV formula injection:** neutralized and covered by regression tests.
- **Explicit cleartext denial missing:** Network Security Config + manifest denial added.
- **Recents thumbnail health-data disclosure:** recents screenshots disabled on Android 13+ without globally blocking user screenshots.
- **Overlay/tapjacking exposure:** `HIDE_OVERLAY_WINDOWS` plus obscured-touch filtering added to sensitive activities.

## P3 — low / hardening

- **Export cache accumulation:** old cached CSV exports are removed before a new export.
- **Privacy controls were not obvious:** Settings now explains widget visibility, private reminders, and export sensitivity.
- **Launcher/notification visual identity was stale:** adaptive/themed launcher assets and a dedicated monochrome notification icon added.
- **Future regression risk:** Android security guard added to protected CI.

## Review posture

Authentication + strict Firestore Rules remain the authorization boundary. Android window protections, network policy, R8, CodeQL, Gitleaks, dependency review, and device protections are defense in depth. App Check is the highest-value open backend-abuse hardening step and must not be described as integrated or enforced until its staged rollout is complete. The project does not claim resistance to a fully rooted/compromised device or a user who intentionally exports/shares their data.
