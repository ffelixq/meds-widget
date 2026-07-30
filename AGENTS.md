# AGENTS.md

## Scope

Meds Widget is an Android-only Kotlin application. Do not add iOS, web,
notifications, analytics, ads, medical advice, paid services, Cloud Functions,
Cloud Storage, or Google Play publication.

## Architecture

Keep domain logic independent from Android UI. Use the `domain`, `data`,
`firebase`, `sync`, `ui`, `widget`, and `util` packages. There is no standalone
`settings` package: its repository contract is in `data`, Firestore/DataStore
implementation is in `firebase`, and screen/ViewModel are in `ui`. Compose
screens and Glance widgets consume state; repositories own persistence and
synchronisation. Widget rendering must use a compact local snapshot and must
not wait on the network. Keep Firestore snapshot listeners bound to the process
foreground lifecycle so they are cancelled while the app is backgrounded.
Route medicine, dose, settings, and widget mutations through the graph-owned
`AccountOperationGate`; account deletion is exclusive and the successful gate
remains closed until `AppGraph` is rebuilt. Keep widget snapshot read-modify-
write transitions inside one atomic DataStore edit, and retain action IDs until
their asynchronous Firestore outcomes resolve. Schedule durable widget-action
reconciliation before rendering an optimistic row; mark accepted repository
submissions, and preserve cancellation. Repository projections must preserve
unresolved optimistic rows without resurrecting pending markers that a
concurrent completion already removed. Account deletion must drain the
UID-scoped outstanding Firestore tasks before deleting cloud data.

## Commands

- `./gradlew formatCheck` — formatting
- `./gradlew detekt lint testDebugUnitTest` — static and unit validation
- `./gradlew assembleDebug` — debug APK
- `./gradlew connectedDebugAndroidTest` — emulator tests
- `npm test --prefix firebase-tests` — Firestore rules tests
- `./scripts/validate.sh` — practical local suite

## Conventions

- Kotlin DSL and `gradle/libs.versions.toml`; pin versions.
- Java 17 Android toolchain (`MEDS_GRADLE_JAVA_HOME` overrides auto-detection);
  Firebase emulator tests require Java 21+ (set `FIREBASE_JAVA_HOME` for
  `scripts/validate.sh`); coroutines and Flow; no
  business logic in composables.
- Deterministic dose-state IDs and immutable random-ID audit events.
- Widgets may check but never undo. Undo requires confirmation in the app.
- Recompute logical medication day at every app/widget/sync/time entry point.
- Keep XML-declared app/widget labels in string resources. Compose screen copy
  is currently mostly inline; preserve wording and accessibility semantics when
  editing it, and avoid introducing duplicate variants of shared text.

## Security and privacy

- Never commit `google-services.json`, service-account files, keystores,
  passwords, tester lists, tokens, local SDK paths, user data, or emulator data.
- Never log medicine names, emails, dose history, or authentication material.
- Firestore access is always scoped to `users/{uid}` and validates `ownerUid`.
- Account deletion must attempt every app DataStore/widget clear, terminate
  Firestore, call `clearPersistence()`, rebuild `AppGraph`, and restart the
  activity task. Local cleanup after Authentication deletion is best effort and
  must still reach the graph restart; keep authentication/account actions
  blocked until then. Treat the SDK cache clear as logical deletion, not secure
  physical overwrite.
- Keep Firebase on Spark with no billing account. Do not enable paid APIs.
- Widget content is visible on the unlocked home screen.

## CI expectations

Pull requests must pass formatting, Detekt, Android Lint, tests, rules tests,
secret scanning, dependency review, CodeQL, wrapper validation, debug build,
and instrumentation tests. Deployment runs only after successful validation on
`main`; forked pull requests never receive deployment secrets. Production uses
the `meds-widget-github`/`meds-widget-main` WIF provider with a main-only
immutable repository/owner-ID condition; the JSON service-account secret is an
unprovisioned emergency fallback, not the normal path.
