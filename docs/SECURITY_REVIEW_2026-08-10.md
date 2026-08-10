# Security review — 2026-08-10

Scope: static review of the Android client, manifest/components, local sharing and notification surfaces, Firebase client usage, Firestore rules/tests, CI/release controls, and the changes in `security/app-polish-hardening`.

This is a CodeRabbit-style defense-in-depth review, not a penetration test or a claim that the app is unhackable. Meds Widget stores health-related personal data, so privacy failures are treated as security findings even when they do not grant account takeover.

## Existing controls that passed review

- Firebase Authentication is the identity boundary and private documents live below `users/{uid}`.
- Firestore rules use path ownership, `ownerUid`, strict field schemas, deterministic state/event IDs, atomic state/event validation, immutable audit events, and recursive default deny.
- Firestore emulator tests cover unauthenticated and cross-user denial plus schema/transition invariants.
- Android backups are disabled and extraction/transfer rules exclude app data.
- FileProvider is non-exported and exposes only `cache/exports/` through temporary URI grants.
- Widget receivers are non-exported; the exported widget-configuration activity validates that the supplied widget ID belongs to the expected provider.
- Release builds are minified, signing is verified, widget callbacks are checked after R8, and deployment is gated behind protected `main` CI.
- Gitleaks, CodeQL, dependency review, wrapper validation, forbidden-file checks, and least-privilege Workload Identity Federation are already part of delivery.
- Production widget diagnostics do not log medicine names, emails, UIDs, tokens, or stack traces.

## Findings and actions

### P1 — Backend request attestation is not enforced yet — FOLLOW-UP REQUIRED

Authentication and Firestore Security Rules protect authorization, but a copied/repackaged client can still send valid Firebase requests if it has legitimate user credentials. Firebase App Check with Play Integrity is the appropriate additional abuse-resistance layer.

Do not enable enforcement until the production signing certificate and outside-Google-Play distribution settings are registered and real App Distribution devices are observed successfully in App Check metrics. Enforcement without that rollout can deny legitimate clients.

### P1 — CSV spreadsheet formula injection — FIXED

CSV quoting previously handled commas, quotes, and newlines but did not neutralize fields beginning with spreadsheet formula prefixes. Medicine names/notes or historical snapshots beginning with `=`, `+`, `-`, or `@` are now exported as literal text, including when whitespace precedes the formula marker. A unit test protects the behavior.

### P1 — Health data export had no explicit disclosure — FIXED

`Export CSV` previously opened the Android share flow immediately. The export contains medicine names, notes, schedule/history data, skip reasons, and timestamps. The app now presents a concise confirmation explaining what leaves the app and that the receiving app may keep a copy.

### P1 — Reminder privacy depended too heavily on device defaults — FIXED

Detailed reminder notifications contain medicine/slot information. The channel and detailed notification are now private, a generic public lock-screen version is supplied, notifications are marked local-only to avoid automatic companion-device bridging, and a dedicated status icon replaces the launcher icon.

### P2 — Cleartext-network policy was implicit — FIXED

The app already uses Firebase HTTPS endpoints, but the manifest did not explicitly deny cleartext traffic. The application now sets `usesCleartextTraffic=false` and attaches a Network Security Config whose base policy also denies cleartext and trusts system CAs only.

### P2 — Security posture could silently regress during vibe coding — FIXED

A new Android security invariant check now fails CI if a future change re-enables backups or cleartext, broadens FileProvider storage paths, exports a receiver, adds another exported component, enables `debuggable`/`testOnly`, removes private/public reminder handling, or removes the extraction/network protections. It is called from the existing required tracked-file security gate, so PR and `main` Android validation run it automatically.

## Deliberate non-actions

- No blanket biometric/fingerprint permission was added. An optional app lock is a product/privacy choice and should be implemented separately with recovery/accessibility design; widgets intentionally remain visible on the unlocked home screen.
- Firestore offline persistence remains enabled because offline medicine tracking is a product requirement. Account-scoped caches, sign-out handling, rules, and deletion cleanup remain the controls; a rooted/compromised device is outside what an app can fully defend against.
- No certificate pinning was added. Pinning Firebase/Google infrastructure would create brittle outage and rotation risk without replacing Auth, Rules, or App Check.
- No analytics, crash-reporting, advertising, or new health-data collection SDK was added.

## Release gate

Before merging this review branch:

1. Android security invariant guard passes.
2. Formatting, Detekt, lint, unit tests, debug/release builds pass.
3. Emulator instrumentation passes.
4. Firestore rule tests pass.
5. CodeQL, Gitleaks, dependency review, and wrapper validation pass.
6. Minified widget callback verification passes.
7. Only after protected `main` passes should the signed APK be distributed.

## Remaining external action

Roll out Firebase App Check with Play Integrity in monitor-first mode, verify App Distribution devices/signing identity, then enable Firestore enforcement. Track that separately so an external-console prerequisite cannot be confused with a completed code change.
