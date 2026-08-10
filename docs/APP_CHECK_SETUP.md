# Firebase App Check activation

Firebase App Check with Play Integrity is the next backend-abuse hardening step for Meds Widget. It is intentionally **not enabled in the app yet** because the Firebase Android app must be registered first and the repository's dependency lock must be regenerated and reviewed when the client dependency is added.

## Why activation is staged

Authentication + Firestore Security Rules remain the authorization boundary. App Check is an additional abuse-prevention layer. Do not enable App Check enforcement before a signed Firebase App Distribution build containing the provider has been observed as valid in App Check metrics; enabling enforcement too early can lock legitimate users out of Authentication or Cloud Firestore.

## Current distribution channel

Meds Widget is distributed outside Google Play through Firebase App Distribution.

For that channel, configure the Android App Check registration as follows:

- provider: **Play Integrity**
- release signing SHA-256: `03a8c044b5b59782ac812d173a041806c9fc0a0bcad02c0a22c94aee6be6eabc`
- `PLAY_RECOGNIZED`: **not required**
- `LICENSED`: **not required**
- minimum device integrity: **Device integrity**

## Activation sequence

1. Firebase Console → **App Check** → **Apps**.
2. Register `io.github.ffelixq.medswidget` with Play Integrity and the release SHA-256 above.
3. Apply the outside-Google-Play advanced settings above.
4. Add `firebase-appcheck-playintegrity` using the existing Firebase BoM.
5. Regenerate `app/gradle.lockfile` with `./gradlew :app:dependencies --write-locks` and review the exact dependency changes instead of weakening dependency locking.
6. Initialize `PlayIntegrityAppCheckProviderFactory` in configured release builds.
7. Let all protected PR checks pass and distribute that signed release through Firebase App Distribution.
8. Install it on the physical Samsung device and exercise sign-in, medicine reads/writes, history, countdowns, widgets, reminders, offline/reconnect, account switching, export, and deletion.
9. In App Check metrics, confirm legitimate requests from that device are valid for **Cloud Firestore** and **Authentication**.
10. Enable enforcement for **Cloud Firestore** first and repeat physical validation.
11. Enable enforcement for **Authentication** only after its legitimate request metrics are also valid.

## CI/debug behavior for the future integration

The production Play Integrity provider should be installed only in configured non-debug builds. Config-free CI/debug builds should not require an App Check debug token and no debug token should be committed or printed to GitHub logs.

## Rollback

If valid users are unexpectedly rejected after enforcement, disable enforcement in Firebase Console while investigating. Do not weaken Firestore Security Rules as an App Check workaround.
