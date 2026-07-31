# Repository Guidelines

## Project Structure

`app/` is the native Android application. Kotlin sources are grouped under
`domain`, `data`, `firebase`, `ui`, `widget`, `sync`, `settings`, and `util`.
JVM tests mirror those packages in `app/src/test`; Compose and device tests live
in `app/src/androidTest`. Firestore rules and indexes are at the repository
root, with emulator tests in `firebase-tests/`. Operational documentation is in
`docs/`, automation in `scripts/`, and CI definitions in `.github/`.

## Build and Test Commands

Use JDK 17 for Gradle and JDK 21+ for the Firebase emulator:

- `./gradlew formatCheck detekt lint testDebugUnitTest assembleDebug` runs the
  practical Android checks.
- `./gradlew ktlintFormat` applies Kotlin formatting.
- `npm test --prefix firebase-tests` runs restrictive Firestore rule tests.
- `./scripts/validate.sh` runs the portable local validation suite.
- `./scripts/verify-release-widget-callback.sh <apk> <mapping.txt>` proves
  `CheckDoseAction` and `StartCountdownAction` remain reflectively instantiable
  after R8.

## Architecture and Conventions

Use four-space Kotlin indentation and ktlint defaults. Classes and composables
use `PascalCase`; functions and properties use `camelCase`; constants use
`UPPER_SNAKE_CASE`. Keep business logic out of Compose and Glance layouts.
Countdowns are timestamp-derived and per medicine/slot/logical day—never
persist a decrementing counter. Widgets use `SizeMode.Exact`, `LocalSize`, and
shared compact/standard/spacious tokens. Runtime Glance callbacks require a
public zero-argument constructor and narrow keep behavior.

## Testing Guidelines

Name tests as readable behavior statements, such as
`` `rapid repeated starts remain idempotent` ``. Cover domain edge cases,
ViewModels, DataStore snapshots, Glance actions/rendering, Compose semantics,
and Firestore ownership rules. New widget behavior must include minified APK
verification. Physical Samsung checks remain explicitly
`REQUIRES_PHYSICAL_SAMSUNG_VALIDATION`.

## Commits, Pull Requests, and Security

Use conventional commits (`feat:`, `fix:`, `test:`, `docs:`). Work on a feature
branch, describe behavior and validation in the PR, and merge only after all
required checks pass. Never commit `google-services.json`, keystores,
`keystore.properties`, tester lists, tokens, or `local.properties`. Do not log
medicine names, email addresses, UIDs, or dose/countdown history. Preserve the
Spark/no-billing boundary and never weaken Firestore rules, R8, or protected
branch checks.
