# AGENTS.md

## Scope

Meds Widget is an Android-only Kotlin application. Do not add iOS, web,
notifications, analytics, ads, medical advice, paid services, Cloud Functions,
Cloud Storage, or Google Play publication.

## Architecture

Keep domain logic independent from Android UI. Use the `domain`, `data`,
`firebase`, `settings`, `sync`, `ui`, `widget`, and `util` packages. Compose
screens and Glance widgets consume state; repositories own persistence and
synchronisation. Widget rendering must use a compact local snapshot and must
not wait on the network.

## Commands

- `./gradlew formatCheck` — formatting
- `./gradlew detekt lint testDebugUnitTest` — static and unit validation
- `./gradlew assembleDebug` — debug APK
- `./gradlew connectedDebugAndroidTest` — emulator tests
- `npm test --prefix firebase-tests` — Firestore rules tests
- `./scripts/validate.sh` — practical local suite

## Conventions

- Kotlin DSL and `gradle/libs.versions.toml`; pin versions.
- Java 17 toolchain; coroutines and Flow; no business logic in composables.
- Deterministic dose-state IDs and immutable random-ID audit events.
- Widgets may check but never undo. Undo requires confirmation in the app.
- Recompute logical medication day at every app/widget/sync/time entry point.
- Keep user-visible strings in resources and include accessibility semantics.

## Security and privacy

- Never commit `google-services.json`, service-account files, keystores,
  passwords, tester lists, tokens, local SDK paths, user data, or emulator data.
- Never log medicine names, emails, dose history, or authentication material.
- Firestore access is always scoped to `users/{uid}` and validates `ownerUid`.
- Keep Firebase on Spark with no billing account. Do not enable paid APIs.
- Widget content is visible on the unlocked home screen.

## CI expectations

Pull requests must pass formatting, Detekt, Android Lint, tests, rules tests,
secret scanning, dependency review, CodeQL, wrapper validation, debug build,
and instrumentation tests. Deployment runs only after successful validation on
`main`; forked pull requests never receive deployment secrets.
