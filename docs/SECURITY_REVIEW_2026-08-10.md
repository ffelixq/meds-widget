# Security review — 2026-08-10

This review treats Meds Widget as a health-data application even though it is a personal medication tracker rather than a regulated clinical system. It is a defense-in-depth review, not a claim that the app is unhackable.

## Review model

Severity follows a CodeRabbit-style triage:

- **P0 — critical:** immediate compromise or broad unauthorized disclosure.
- **P1 — high:** realistic path to meaningful health/account data exposure or unauthorized backend use.
- **P2 — medium:** defense-in-depth weakness with a plausible exploit or privacy impact.
- **P3 — low:** hardening, maintainability, or privacy polish.

Threats considered:

- unauthenticated and cross-account Firestore access;
- repackaged or scripted Firebase clients;
- stolen/abused authenticated sessions;
- malicious Android overlays/tapjacking;
- passive disclosure through lock-screen notifications and recents thumbnails;
- malicious spreadsheet formulas in exported health data;
- accidental cleartext network regressions;
- exported Android components and URI grants;
- secrets committed to Git/GitHub Actions;
- R8/reflection breakage in widget callbacks;
- data remaining on rooted/compromised devices.

## Existing strengths verified

The codebase already has unusually strong foundations for a small personal app:

- Firestore ends in recursive default-deny and every private collection is under `users/{uid}`.
- Rules bind the authenticated UID, document owner, deterministic IDs, exact schemas, and allowed state transitions.
- Dose/countdown state changes are atomically paired with immutable audit events using `getAfter()`.
- Backups are disabled and the export `FileProvider` is non-exported with temporary URI grants.
- Widget actions treat callback parameters as untrusted and re-check the current Firebase account and widget ownership.
- Account deletion requires Firebase reauthentication and attempts to clear app-managed caches and Firestore persistence.
- Release builds use R8/resource shrinking and CI verifies reflectively loaded Glance callbacks in the minified APK.
- CI runs Gitleaks, dependency review, Android lint/static analysis/tests, Firestore emulator tests, instrumentation, and CodeQL.
- Production deployment uses short-lived GitHub OIDC/WIF credentials rather than a committed or long-lived service-account key.

## Findings and remediation

### P1 — Firebase App Check was not integrated

**Risk:** Firestore Security Rules prevent cross-user access, but without app attestation an attacker can more easily automate Firebase calls from a copied/custom client using their own or stolen valid session. Authentication and rules remain the authorization boundary, but App Check adds an important abuse barrier.

**Fix in this branch:** add Firebase App Check with the Play Integrity provider to release builds.

**Important deployment requirement:** client integration alone does not enforce App Check. Register the Android app in Firebase App Check using the release signing SHA-256, configure it for an app distributed outside Google Play, monitor App Check metrics, then enable enforcement for **Cloud Firestore** and **Authentication**. Do not enable enforcement before a legitimate App Distribution build is observed as valid.

For the current Firebase-App-Distribution-only channel, Firebase's guidance says `PLAY_RECOGNIZED` and `LICENSED` should not be required, while **Device integrity** is the recommended minimum device integrity level.

**Status:** client-ready in code; **console enforcement remains a required operational step**.

### P1 — lock-screen reminder content disclosed medicine details

**Risk:** a locked device could reveal medicine names/custom labels through notification content depending on system notification settings.

**Fix:** reminder notifications now use `VISIBILITY_PRIVATE`, a generic public version, and a private notification channel. The scheduled WorkManager request no longer persists medicine names or labels; content is resolved from the account-bound app snapshot only at delivery time.

**Status:** fixed in code.

### P2 — CSV spreadsheet-formula injection

**Risk:** medicine names, labels, notes, or skip reasons are user-controlled. When CSV is opened in spreadsheet software, values beginning with `=`, `+`, `-`, `@`, or a tab can be interpreted as formulas.

**Fix:** potentially executable cell prefixes are neutralized with a leading apostrophe before normal CSV escaping. Regression tests cover the dangerous prefixes.

**Status:** fixed and tested.

### P2 — no explicit all-version cleartext network denial

**Risk:** the current target SDK already defaults to no cleartext traffic on modern Android, but the app supports API 26 and an accidental future client/domain change could create an HTTP regression on older devices.

**Fix:** `android:usesCleartextTraffic="false"` plus a Network Security Config whose base policy denies cleartext and trusts only system CAs.

**Status:** fixed and CI-guarded.

### P2 — passive disclosure from Android recents

**Risk:** medicine/history screens can be captured by Android for the Overview/Recents thumbnail.

**Fix:** on Android 13+, sensitive activities call `setRecentsScreenshotEnabled(false)`. This intentionally does **not** globally set `FLAG_SECURE`, so a user can still deliberately take a screenshot when they choose to.

**Status:** fixed while preserving user agency.

### P2 — overlay/tapjacking defense was absent

**Risk:** a malicious overlay could attempt to trick the user into pressing account, export, medication, or other controls.

**Fix:** declare `HIDE_OVERLAY_WINDOWS`, hide third-party application overlays on Android 12+, and filter obscured touches at the activity window root. The exported widget-configuration activity applies the same policy.

**Status:** fixed.

### P3 — health export files accumulated in cache

**Risk:** each export created another health-data CSV in app cache.

**Fix:** stale cached export files are removed before creating a new export. The FileProvider continues granting read access only through a temporary content URI to the user's chosen share target.

**Status:** fixed.

### P3 — privacy controls need to be visible to users

**Risk:** strong protections that are invisible are hard to understand, while widgets are intentionally visible on an unlocked home screen.

**Fix:** Settings now explains the local privacy protections, lock-screen reminder behavior, widget privacy tradeoff, and sensitivity of exported CSV files.

**Status:** fixed.

### P3 — launcher/notification identity was visually stale

**Risk:** not a security vulnerability, but inconsistent launcher and notification assets reduce polish/trust and made the notification small icon unsuitable for Android's monochrome treatment.

**Fix:** add an adaptive launcher icon with Android 13 themed-icon support, plus a dedicated monochrome notification icon. The mark keeps the familiar medicine + completion concept while using the app's current blue/green design language.

**Status:** fixed.

## CI hardening

`scripts/check-android-security.sh` now fails CI if a future change:

- re-enables Android backup;
- enables cleartext network traffic;
- removes the Network Security Config;
- removes overlay protection;
- removes the App Check dependency;
- introduces high-risk permissions such as external-storage, overlay, SMS, contacts, camera, microphone, broad package visibility, or package-install privileges;
- forces the production app debuggable;
- exports an unexpected application component; or
- makes the FileProvider exported.

The existing `check-forbidden-files.sh` invokes this guard, so it runs in the protected Android validation job without adding another required-status-check name to branch protection.

## Residual risk / deliberately not implemented

### App Check enforcement is not automatic

The app code can request Play Integrity-backed App Check tokens, but Firebase Console registration and enforcement are separate server-side operations. Do not document App Check as "enforced" until Cloud Firestore and Authentication metrics show valid legitimate traffic and enforcement is enabled.

### No homemade local-data encryption

The app deliberately does not add custom crypto around DataStore or Firestore's SDK cache. Android's app sandbox is the normal local boundary. Custom encryption would introduce key-management and widget/background reliability risks without protecting a fully rooted/compromised device. Minimize cached sensitive data instead, keep backups disabled, and use widget privacy controls.

### No certificate pinning

The app uses Firebase/Google infrastructure with system trust and HTTPS-only policy. Certificate pinning is intentionally not added because pin lifecycle/rotation can create availability failures and is not needed to establish the app's authorization boundary.

### No global `FLAG_SECURE`

Screenshots are not globally disabled. A user may legitimately want to show a medication list to a clinician or caregiver. Recents previews and overlays are blocked instead. If the threat model changes, a future optional "Block screenshots" privacy setting can use `FLAG_SECURE` with clear user control.

### Rooted/compromised devices remain outside the trust boundary

A sufficiently compromised Android device can inspect application memory/storage or manipulate the UI despite normal application protections. App Check raises the backend-abuse bar but is not a substitute for authentication, Firestore Rules, or device security.

## App Check activation checklist

1. Firebase Console → **App Check** → select the Android app.
2. Register the release signing certificate SHA-256.
3. Select Play Integrity.
4. Because this build is distributed through Firebase App Distribution rather than Google Play, configure advanced settings so **PLAY_RECOGNIZED is not required**, **LICENSED is not required**, and minimum device integrity is **Device integrity**.
5. Install the new signed release through Firebase App Distribution on the real Samsung test device.
6. Monitor App Check metrics for **Cloud Firestore** and **Authentication**.
7. Confirm legitimate requests are reported valid.
8. Enable enforcement for Cloud Firestore first, validate normal medicine/widget/countdown flows, then enable Authentication enforcement.
9. Repeat physical Samsung tests including offline/reconnect, reboot, real 2×2/4×2/4×4 widget actions, reminders, export, account switching, and deletion.

## Review outcome

No P0 finding was identified in this review. The existing authorization model is materially stronger than a typical prototype. The remaining highest-value operational action after this branch is validated and shipped is Firebase App Check registration/metrics/enforcement on the real signed App Distribution build.
