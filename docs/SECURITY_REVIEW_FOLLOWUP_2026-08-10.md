# Security review follow-up — 2026-08-10

This review continues the CodeRabbit-style security pass after PR #13. It focuses only on residual findings not already fixed there. Meds Widget is a personal health-data tracker; this is defense in depth, not a claim that the client can be made unhackable.

## Existing controls retained from PR #13

- UID-scoped Firebase Authentication + strict Firestore Security Rules remain the authorization boundary.
- Firestore state/event writes retain their deterministic IDs, schema validation, immutable audit events, and atomic `getAfter()` pairing.
- Android backups remain disabled.
- Cleartext traffic is explicitly denied by manifest and Network Security Config.
- FileProvider remains non-exported and limited to the export cache directory.
- Reminder notifications retain private detailed content, a generic public lock-screen version, and `localOnly` behavior.
- CSV formula injection protection and explicit export confirmation remain in place.
- Release R8/minified Glance callback verification, Gitleaks, dependency review, Firestore emulator tests, Android instrumentation, and CodeQL remain required.
- Firebase App Check rollout remains tracked separately in issue #12 and is not falsely claimed as enforced.

## Residual findings fixed here

### P2 — overlay/tapjacking exposure

A health-data activity should not accept interaction through a hostile third-party overlay. This follow-up declares `HIDE_OVERLAY_WINDOWS`, asks Android 12+ to hide application overlays, and filters obscured touches in both the main health UI and the exported widget-configuration activity.

### P2 — passive disclosure through Recents thumbnails

On Android 13+, the two user-facing activities disable Recents screenshots. This avoids leaving a medicine/history snapshot in Overview while deliberately preserving the user's ability to take an intentional screenshot; global `FLAG_SECURE` is not enabled.

### P2 — unnecessary medicine text persisted in WorkManager input

PR #13 protected reminder presentation on the lock screen, but periodic WorkManager input still persisted the medicine display name and custom slot label in WorkManager's local database. This follow-up persists only account/medicine/slot/course identifiers needed to identify the reminder. The worker resolves privacy-aware display text from the account-bound widget snapshot only at delivery time and falls back to a generic reminder if that snapshot is unavailable or belongs to another account.

### P3 — cached health exports accumulated

Before generating a new CSV, the app now removes stale files from its private `cache/exports` directory. Sharing still requires the existing explicit confirmation and temporary FileProvider URI grant.

### P3 — privacy behavior was fragmented across Settings

Settings now presents a concise Privacy & security card explaining account-scoped data, backup/network protections, Recents/overlay protection on supported devices, and the intentional visibility of unlocked home-screen widgets. Reminder/export copy also states the relevant privacy boundary at the point of use.

### P3 — launcher identity did not match the current design system

The adaptive icon architecture from PR #13 is retained, but its launcher field now uses the same semantic blue as the app's primary actions. The white capsule remains familiar and a small green completion badge matches the app's success/taken language. Android 13 themed-icon support remains intact.

## CI regression gates

The existing Android security invariant script is extended so protected CI fails if a later change:

- removes `HIDE_OVERLAY_WINDOWS`;
- requests overlay creation, package installation, or all-files access;
- stores medicine names or custom labels back into reminder WorkManager input;
- removes private/public/local-only notification behavior; or
- removes Recents, overlay, or obscured-touch protection from either sensitive activity.

## Open item

Firebase App Check / Play Integrity remains issue #12. It requires Firebase Console registration for the signed outside-Google-Play distribution, a dedicated reviewed dependency-lock update, monitor-first real-device validation, and staged enforcement. Authentication and Firestore Rules must not be weakened when App Check is introduced.

## Residual risk

A rooted or otherwise fully compromised device is outside the application's trust boundary. Home-screen widgets intentionally reveal the configured display name/status while the device is unlocked, subject to the app's nickname/hidden-name privacy options. User-initiated CSV sharing also transfers a copy to the selected destination and cannot be revoked by Meds Widget afterward.
