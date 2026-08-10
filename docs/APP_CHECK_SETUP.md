# Firebase App Check activation

Meds Widget includes the Play Integrity App Check provider in release builds. App Check only becomes a backend enforcement control after the Firebase project is registered and enforcement is enabled in Firebase Console.

## Why activation is staged

Do not enable enforcement before a signed Firebase App Distribution build has been observed in App Check metrics. Enabling enforcement too early can lock legitimate users out of Authentication or Cloud Firestore.

## Current distribution channel

Meds Widget is distributed outside Google Play through Firebase App Distribution.

For that channel, configure the Android App Check registration as follows:

- provider: **Play Integrity**
- release signing SHA-256: `03a8c044b5b59782ac812d173a041806c9fc0a0bcad02c0a22c94aee6be6eabc`
- `PLAY_RECOGNIZED`: **not required**
- `LICENSED`: **not required**
- minimum device integrity: **Device integrity**

These settings follow Firebase's guidance for apps distributed exclusively outside Google Play.

## Activation sequence

1. Firebase Console → **App Check** → **Apps**.
2. Register `io.github.ffelixq.medswidget` with Play Integrity and the release SHA-256 above.
3. Apply the outside-Google-Play advanced settings above.
4. Merge and distribute a release that contains the App Check client integration.
5. Install that release on the physical Samsung device through Firebase App Distribution.
6. Exercise sign-in, medicine reads/writes, history, countdowns, widgets, reminders, offline/reconnect, and account switching.
7. In App Check metrics, confirm legitimate requests from that device are valid for **Cloud Firestore** and **Authentication**.
8. Enable enforcement for **Cloud Firestore**.
9. Repeat the physical validation flow.
10. Enable enforcement for **Authentication** only after its legitimate request metrics are also valid.

## Debug/CI behavior

The production Play Integrity provider is installed only in configured non-debug builds. Config-free CI/debug builds therefore do not need an App Check debug token and do not leak one into repository history or GitHub logs.

## Rollback

If valid users are unexpectedly rejected after enforcement, disable enforcement in Firebase Console while investigating. Do not weaken Firestore Security Rules as an App Check workaround: Authentication + Rules remain the authorization boundary, while App Check is an additional abuse-prevention layer.
