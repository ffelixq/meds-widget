# Repository Guidelines

## Project Structure & Module Organization

Production Kotlin lives under
`app/src/main/java/io/github/ffelixq/medswidget/`, organized into `domain`,
`data`, `firebase`, `sync`, `ui`, `widget`, and `util`. Android resources and
widget metadata are in `app/src/main/res/`. JVM, Compose, and Glance tests are
in `app/src/test/`; device tests are in `app/src/androidTest/`.
`firebase-tests/` contains Firestore emulator tests, `scripts/` holds validation
utilities, and `docs/` contains guides.

## Architecture & Scope

Keep domain logic independent from Compose and Glance. Repositories own
persistence; widgets render from the DataStore
snapshot and must not wait for the network. Preserve account isolation,
idempotent dose actions, immutable audit events, and app-only undo. This is an
Android tracking utility—not medical advice. Do not add web/iOS clients,
analytics, ads, paid services, Cloud Functions, or Play publishing.

## Build, Test, and Development Commands

- `./gradlew formatCheck` / `./gradlew formatApply` — check or correct Kotlin formatting.
- `./gradlew detekt lint testDebugUnitTest` — run static analysis, Android Lint, and JVM tests.
- `./gradlew assembleDebug` — build the development APK.
- `./gradlew connectedDebugAndroidTest` — run instrumentation tests on an emulator.
- `npm test --prefix firebase-tests` — test Firestore rules with the emulator.
- `./scripts/validate.sh` — run the practical local validation suite.

Android builds use Java 17; set `MEDS_GRADLE_JAVA_HOME` when auto-detection
fails. Firebase emulator tests require Java 21 or newer.

## Coding Style & Naming Conventions

Use Kotlin official style, four-space indentation, LF endings, and a 140-column
limit. Ktlint and Detekt enforce style. Use `PascalCase` for types/composables,
`camelCase` for functions and properties, and descriptive test names in
backticks. Pin dependencies in `gradle/libs.versions.toml`; never use dynamic
versions. Keep business logic out of composables and widget layout functions.

## Testing Guidelines

Add deterministic regression tests for behavior changes. Use JUnit/Robolectric
for domain, ViewModel, Compose, and Glance behavior; use AndroidX Test for
device flows and Node’s test runner for Firestore rules. Cover failure, offline,
account-switch, and process-recreation paths where relevant.

## Commit & Pull Request Guidelines

Follow Conventional Commits, for example `fix(widget): preserve Glance callback`.
Target `main` through a pull request; direct pushes and force pushes are blocked.
Describe the change, root cause, tests run, security impact, and remaining
physical-device work. All seven required CI checks must pass before squash merge.

## Security & Configuration

Never commit `google-services.json`, service-account files, keystores, passwords,
tokens, tester lists, local SDK paths, or user data. Never log medicine names,
emails, dose history, or authentication material. Keep Firebase on Spark without
billing, and retain restrictive `users/{uid}` Firestore rules.
