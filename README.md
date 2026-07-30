# Meds Widget

Meds Widget is a focused, native Android medicine-tracking utility. It keeps an
afternoon/night checklist in the app and in two home-screen widgets. It is a
tracking utility only: it does not provide medical advice, dosing advice,
reminders, treatment recommendations, or drug information.

- Application ID: `io.github.ffelixq.medswidget`
- Repository: <https://github.com/ffelixq/meds-widget>
- Firebase project ID: `meds-widget-ffelixq`
- Firebase Android App ID:
  `1:648847295725:android:15e7b95037f6ff897678e4`
- Platform: Android 8.0 (API 26) and newer
- V1 distribution: signed APK through Firebase App Distribution and GitHub
  Actions artifacts; no Google Play publication
- Licence: no open-source licence has been assigned

## Project status

The repository contains the Android application, the Firestore rules and
indexes, emulator-based rule tests, and automation definitions used for V1.
External resource identifiers, deployment results, APK hashes, and links to
completed CI runs belong in `VALIDATION_REPORT.md`; they must not be inferred
from configuration templates or an unexecuted workflow.

The initial production delivery is verified for commit
`956a1f26c58adfeb19c46e1306536ba9fa68f46b`: [CI run 30514348334,
attempt 2](https://github.com/ffelixq/meds-widget/actions/runs/30514348334/attempts/2)
completed successfully, including Firestore rules/index deployment and Firebase
App Distribution. The run used short-lived WIF credentials; its JSON-key
fallback was skipped. Cloud Billing was disabled before and after enabling the
identity APIs required by WIF. The temporary user-managed deployment key was
then deleted, and the deployment service account has zero user-managed keys.

`main` is protected by active ruleset `20019671` (`Protect main`). It has no
bypass actors and requires a pull request, squash merging, the seven documented
checks, resolved conversations, an up-to-date branch, and linear history; it
also blocks deletion and force pushes.

Physical Samsung validation is intentionally separate from automated testing.
See [Samsung validation](docs/SAMSUNG_VALIDATION.md). It remains
`REQUIRES_PHYSICAL_SAMSUNG_VALIDATION` until every step has been performed on a
real Samsung phone.

## Screenshots

Screenshots are intentionally pending emulator and physical-device capture.
No fabricated UI images are included.

| Surface | Placeholder |
| --- | --- |
| Sign-in and account creation | `SCREENSHOT_PENDING_VALIDATED_BUILD` |
| Main medicine checklist | `SCREENSHOT_PENDING_VALIDATED_BUILD` |
| 2×2 single-medicine widget on Samsung One UI | `REQUIRES_PHYSICAL_SAMSUNG_VALIDATION` |
| 4×2 all-medicines widget on Samsung One UI | `REQUIRES_PHYSICAL_SAMSUNG_VALIDATION` |

## Features

- Email/password registration, sign-in, password reset, and sign-out with
  Firebase Authentication.
- Google authentication through Android Credential Manager and Sign in with
  Google, not the deprecated Google Sign-In integration.
- Medicine creation, editing, archiving, and deletion.
- Independently enabled afternoon and night slots, with custom per-medicine
  labels. At least one slot is required. Blank labels are rejected for enabled
  slots; a blank disabled-slot label is normalized to that slot's default.
- A logical medication day with a configurable local reset time. The default is
  midnight.
- Idempotent check intent, exact device occurrence time, device timezone, and
  source attribution.
- App-only undo with confirmation and an immutable check/undo audit history.
- A responsive 2×2 Glance widget configured to one active medicine per widget
  instance.
- A responsive, vertically scrollable 4×2 Glance widget containing every
  active dose.
- Functional in-app previews for the single-medicine and all-medicines widgets.
- Immediate local widget snapshots and Firestore's Android offline queue.
- Light, dark, and system themes.
- Compact history grouped by logical medication day.
- Client-side account deletion with reauthentication, account-wide mutation
  exclusion, best-effort app-cache clearing, and Firestore persistence reset.

## No-cost boundary

This project is deliberately restricted to the Firebase Spark plan. Do not
attach a Cloud Billing account and do not upgrade to Blaze. The application
uses only:

- Firebase Authentication with email/password and Google;
- one Cloud Firestore Standard database;
- Firebase App Distribution; and
- the local Firebase Emulator Suite.

Firebase documents App Distribution and most Authentication methods as
no-cost, and gives a limited no-cost quota for Cloud Firestore on Spark:
<https://firebase.google.com/docs/projects/billing/firebase-pricing-plans>.
Firestore's current free quota and excluded billed features are documented at
<https://firebase.google.com/docs/firestore/quotas>.

Production WIF additionally uses the IAM, Service Account Credentials, and
Security Token Service APIs described in Google's
[deployment-pipeline WIF guide](https://cloud.google.com/iam/docs/workload-identity-federation-with-deployment-pipelines).
They were enabled without linking billing; Google documents IAM API use as
free in [IAM pricing](https://cloud.google.com/iam/pricing).

On Spark, exhausting a Firestore quota stops that product until the quota
resets; it does not automatically upgrade the project. The app shows cached or
pending state and must fail safely. Do not add Cloud Functions, Cloud Storage,
Realtime Database, SQL Connect, paid APIs, analytics, ads, or subscriptions.

## Architecture

The app uses a small manual dependency graph and repository interfaces:

```text
Compose UI / Glance widgets
            |
      ViewModels / actions
            |
   shared domain calculations
            |
 repository interfaces + DataStore snapshot
            |
 Firebase Authentication / Cloud Firestore
```

`AppGraph` creates the Firebase-backed repositories when
`google-services.json` successfully configures Firebase. A deliberately limited
unavailable implementation renders a friendly configuration error in builds
without that file. While the application process is in the foreground, one
application-scoped listener pipeline combines the signed-in UID, active
medicines, UID-keyed settings, current logical day, and dose states into a
shared `AccountDaySnapshot` consumed by the app and widget projection. Android
process-lifecycle callbacks cancel those Firestore listeners in the background
and recreate them on return. A temporal tick restarts the day-specific listener
after reset/time/timezone entry points. Domain code owns logical day,
validation, row construction, progress, source mapping, and undo policy.
One graph-owned `AccountOperationGate` serializes medicine, dose, settings, and
widget mutations against account deletion. A UID-scoped outstanding-write
tracker also waits for the Firestore tasks already dispatched by those
mutations, rather than merely waiting for enqueue calls to return. Deletion
rejects new mutations and keeps the gate closed until the signed-out graph has
restarted; a deletion that fails before Authentication is removed reopens it
for retry.

Widgets never wait indefinitely for a network response. They render an
account-scoped, compact JSON snapshot stored in Preferences DataStore. Snapshot
state transitions use atomic `DataStore.edit` operations. A check tap stores an
optimistic row and random action correlation together, schedules a
network-constrained recovery worker, refreshes both widget types, and submits
that same action ID in the atomic Firestore state/event batch. Accepted actions
are marked submitted. If the process dies, the worker waits for authoritative
non-cached state before resolving submitted actions; an interrupted
pre-submission action receives a grace period and is then reconciled safely.
Widget headers show `Syncing` while correlated actions are pending, or
`Cached` when data came from cache or a refresh error; pending status takes
precedence. Repository projections merge unresolved optimistic rows in the same
DataStore transaction, so they preserve pending display state without
resurrecting an action marker that a concurrent completion already resolved.
Repository completion callbacks resolve the persisted action even while
foreground Firestore listeners are paused: success removes its pending
correlation and clears `Syncing` when none remain, while failure rolls back that
row, exposes a cached error, and refreshes widgets. Asynchronous medicine or
in-app dose rejections likewise publish a friendly error through repository
state.

Before every widget render or action, the persisted snapshot UID is compared
with Firebase Authentication's live UID. Signed-out or switched-account
content is replaced before Glance reads it.

Read [Architecture](docs/ARCHITECTURE.md) and
[Data model](docs/DATA_MODEL.md) for the detailed flow and invariants.

## Project structure

```text
app/src/main/java/io/github/ffelixq/medswidget/
├── data/       Repository contracts and unavailable-build implementations
├── domain/     Android-independent models, validation, day and dose logic
├── firebase/   Authentication, Firestore repositories, paths and mappers
├── sync/       Reset boundary scheduling and system-time refresh handling
├── ui/         Compose screens, navigation, themes and ViewModels
├── util/       Locale-aware display formatting
└── widget/     Glance widgets, actions, configuration and local snapshots

firebase-tests/ JavaScript tests using the Firestore emulator
config/         Static-analysis configuration
docs/           Architecture, operations, security and validation guides
scripts/        Portable validation and security-support scripts
```

Settings currently span the domain settings model, Firebase/DataStore
repository, settings ViewModel/screen, and reset scheduler rather than a
dedicated source directory.

## Pinned build baseline

Versions are reproducible and centralized in
`gradle/libs.versions.toml`; dynamic `+` versions are not used. These are the
stable versions pinned by this repository, not a promise that every upstream
project will never publish a later stable patch. Gradle dependency locking is
enabled for every project and the root, settings, and app lockfiles are
committed; update those lockfiles deliberately when changing dependencies.

| Component | Pinned value |
| --- | --- |
| Gradle Wrapper | 9.6.1 |
| Android Gradle Plugin | 9.3.1 |
| Kotlin / Compose compiler plugin | 2.4.10 |
| Java toolchain | 17 |
| Compile SDK | 37 (`37.0` CI SDK package) |
| CI Build Tools | 36.0.0 |
| Target SDK | 37 |
| Minimum SDK | 26 |
| Compose BOM | 2026.06.01 |
| Firebase Android BOM | 34.16.0 |
| Glance | 1.1.1 |
| Activity Compose | 1.13.0 |
| Lifecycle | 2.11.0 |
| Navigation Compose | 2.9.8 |
| DataStore | 1.2.1 |
| WorkManager | 2.11.2 |
| Credential Manager | 1.6.0 |
| Coroutines | 1.11.0 |
| Detekt | 1.23.8 |

Useful upstream release references:

- <https://developer.android.com/build/releases/about-agp>
- <https://docs.gradle.org/9.6.1/release-notes.html>
- <https://kotlinlang.org/docs/releases.html>
- <https://developer.android.com/jetpack/androidx/versions>
- <https://firebase.google.com/support/release-notes/android>

No alpha, beta, or release-candidate dependency is declared.

## Required tools

- macOS or Linux for the documented scripts;
- JDK 17 for the Android/Gradle toolchain;
- a JDK 21 or newer for Firebase Emulator Suite commands;
- Android SDK platform package 37.0 and Build Tools 36.0.0, matching AGP's
  documented compatible default and CI,
  installed through Android Studio's SDK Manager;
- Node.js 20 or newer and npm;
- Firebase CLI;
- Git and GitHub CLI;
- an Android emulator for instrumentation tests; and
- a physical Samsung phone for the Samsung-specific checklist.

The Gradle installation itself is supplied by the committed wrapper.

Verify the essentials:

```bash
java -version
./gradlew --version
node --version
npm --version
firebase --version
gh --version
adb version
```

The validation script finds a local JDK 17 for Gradle, including a Homebrew
`openjdk@17` installation on macOS. Set `MEDS_GRADLE_JAVA_HOME` if JDK 17 is
installed somewhere else. If `java` is not already JDK 21+, point only the
Firebase portion of the practical suite at a newer JDK:

```bash
export FIREBASE_JAVA_HOME=/absolute/path/to/jdk-21-or-newer
```

## Local setup

Clone and enter the project:

```bash
git clone https://github.com/ffelixq/meds-widget.git
cd meds-widget
```

Create `local.properties` locally if Android Studio has not created it:

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

`local.properties` is ignored and must never be committed.

Install the Firestore rules-test dependencies:

```bash
npm ci --prefix firebase-tests
```

For an unconfigured UI/build check, `google-services.json` may remain absent.
The project still compiles and the app displays a configuration message instead
of attempting authentication. Real authentication and Firestore testing require
the correct Firebase Android configuration:

```text
app/google-services.json
```

That file is ignored. Obtain it only from the intended Firebase project as
described in [Firebase setup](docs/FIREBASE_SETUP.md). Do not copy a production
configuration from an unrelated app.

## Firebase and Google sign-in

The Firebase Android app must be registered with the exact, case-sensitive
package name `io.github.ffelixq.medswidget`. Create one Firestore Standard
database in `asia-southeast1` (Singapore), then enable Email/Password and Google
under Authentication. The database location should be treated as permanent.

Google sign-in additionally requires:

- the project's support email;
- Google provider enabled in Firebase Authentication;
- the debug signing SHA-1 and SHA-256 fingerprints;
- the release signing SHA-1 and SHA-256 fingerprints; and
- a freshly downloaded `google-services.json` containing
  `default_web_client_id`.

The implementation follows Firebase's current Android Credential Manager
guidance:
<https://firebase.google.com/docs/auth/android/google-signin>.

Do not commit `google-services.json`. Its API key is not treated as the
authorization boundary—Firebase Authentication and restrictive Firestore rules
are. CI reconstructs the file from a secret stored only in the main-restricted
GitHub environment `production`; it is not a repository-level secret. Release
signing and Google deployment credentials use the same environment boundary.
See [Firebase setup](docs/FIREBASE_SETUP.md) for exact commands and console
URLs.

## Development commands

Use the wrapper from the repository root:

```bash
./gradlew formatCheck
./gradlew formatApply
./gradlew detekt
./gradlew lint
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
npm test --prefix firebase-tests
./gradlew fullValidation
```

The practical cross-tool suite is:

```bash
FIREBASE_JAVA_HOME=/absolute/path/to/jdk-21-or-newer \
./scripts/validate.sh
```

This command defaults to one Gradle worker for reliable macOS resource
processing. Set `MEDS_GRADLE_MAX_WORKERS` to a positive integer only after the
machine's Android toolchain has been verified with parallel workers.

Every required command is expected to exit non-zero on failure. `formatApply`
changes source files; the other commands above are validation/build operations.
Read [Testing](docs/TESTING.md) for emulator preparation, reports, and the test
inventory.

## Build APKs

Debug:

```bash
./gradlew assembleDebug
```

Expected output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release builds require all four signing inputs. They can come from ignored
`keystore.properties` or the environment variables used by CI:

```text
MEDS_KEYSTORE_PATH
MEDS_KEYSTORE_PASSWORD
MEDS_KEY_ALIAS
MEDS_KEY_PASSWORD
```

An ignored local properties file has this shape:

```properties
storeFile=release-signing/meds-widget-release.p12
storePassword=<strong-random-password>
keyAlias=<release-alias>
keyPassword=<strong-random-password>
```

Then run:

```bash
./gradlew assembleRelease
./gradlew bundleRelease
```

Local builds default to version code `1`. The main deployment workflow passes
its monotonically increasing GitHub run number as `MEDS_VERSION_CODE`, allowing
newer distributed APKs to update older ones.

Never commit the keystore or properties. Keep a secure, backed-up local copy of
the release keystore. Losing it prevents future APKs from updating an installed
copy under the same signing identity.

## Widget installation on Samsung One UI

After installing and signing in:

- long-press an empty area of the Samsung home screen;
- choose **Widgets**;
- find **Meds Widget**;
- add **Meds Widget — Single medicine** for a responsive 2×2 widget, then
  select exactly one active medicine;
- add **Meds Widget — All medicines** for the responsive, scrollable 4×2
  widget; and
- touch and hold a single-medicine widget and use **Settings** or
  **Reconfigure** where the One UI version exposes it.

Launcher grid sizes vary, so both widgets use responsive minimum dimensions and
support resizing. On Android 9/API 28 and newer, the qualified widget metadata
marks each single-medicine instance `reconfigurable`; older launchers retain the
normal add-time configuration flow. The launcher still decides whether and
where to expose reconfiguration controls. A checked widget row opens the app
and never performs an undo. Only an unchecked row can submit a widget check.

The complete real-device procedure is in
[Samsung validation](docs/SAMSUNG_VALIDATION.md).

## Logical medication day

The default reset is `12:00 AM` in the device's current timezone. Let `R` be the
configured reset time:

- local time at or after `R` belongs to the current local date;
- local time before `R` belongs to the previous local date.

Changing the reset time can therefore change the visible logical date
immediately. No history is deleted at the boundary. The app recomputes the day
when it opens or resumes, when a widget renders or receives an action, when a
repository refresh runs, and after boot/date/time/timezone broadcasts.
WorkManager schedules a best-effort refresh near the next boundary; Android may
delay it, so correctness never depends only on that schedule.

The calculation uses `java.time` with the current `ZoneId`, covering calendar
boundaries, leap years, and daylight-saving transitions.

## Offline behaviour

Cloud Firestore's persistent Android cache is enabled by default. Existing
cached documents remain readable, and batched writes are queued until
connectivity returns:
<https://firebase.google.com/docs/firestore/manage-data/enable-offline>.

The app also stores a small, app-private widget snapshot so widget rendering
does not wait for the network. The app exposes cached/pending state, while both
widget types compact it to `Cached` or `Syncing`. Medicine data for a signed-out
account is removed from the widget snapshot, and configuration is owner-scoped
to prevent an account switch from displaying the previous account's widget
rows.

Important boundaries:

- an account's first uncached load cannot invent data while offline;
- Firestore resolves multiple offline edits to the same document with
  last-write-wins semantics;
- the in-process mutex prevents rapid duplicate taps in one process, while the
  deterministic state ID and rules protect the state/event shape; simultaneous
  actions from multiple offline devices can still conflict when they reconnect;
- client-side account deletion deliberately reads each deletion batch from
  `Source.SERVER`, so it requires connectivity and never treats a local cache as
  proof that all server data is gone; it may still be partial if the process or
  network fails between batches;
- sign-out clears settings and account content from the widget snapshot but
  leaves owner-scoped per-widget selections and Firestore's SDK-managed cache
  in place; neither can expose rows for a different signed-in UID;
- after cloud and Authentication deletion succeeds, the app attempts to clear
  every app-managed DataStore, update widgets to signed-out state, terminate
  Firestore, and call
  `clearPersistence()` on a best-effort basis, then builds a new `AppGraph` and
  restarts `MainActivity`; the blocking deletion screen remains in control
  instead of exposing sign-in during that transition. Authentication deletion
  is not reversed if one local cleanup action fails. Firebase's persistence
  clear is a logical cache deletion, not a guarantee that storage blocks were
  securely overwritten, so Android app data should still be cleared on a shared
  or high-risk device; and
- the history listener is intentionally capped at the newest 500 events.

These are V1 limitations, not medical guarantees.

## Privacy and permissions

Medicine names, slot labels, timestamps, timezone IDs, settings, and audit
events are associated with the signed-in Firebase UID and stored in Firestore.
The final merged package contains six permissions:

- `android.permission.INTERNET`;
- `android.permission.ACCESS_NETWORK_STATE`;
- `android.permission.RECEIVE_BOOT_COMPLETED`;
- `com.google.android.providers.gsf.permission.READ_GSERVICES`, contributed by
  Google Play services;
- `android.permission.WAKE_LOCK`, contributed by WorkManager for scheduled
  work; and
- the app-specific signature-protected
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` generated by AndroidX.

It does not request contacts, location, camera, microphone, photos, storage,
SMS, phone, notification, exact-alarm, biometric/fingerprint, or foreground-
service permission. The manifest also removes WorkManager's unused
`SystemForegroundService`. None of the six packaged permissions produces a
dangerous runtime permission prompt. Android backups are disabled.

Widget content is visible on the unlocked home screen and may be visible to
anyone who can see that screen. Local DataStore content is app-private but is
not a substitute for keeping the device locked. No ads or analytics are
enabled, and the application does not sell data.

Read [Security](docs/SECURITY.md) for the rules model, secret handling, CI
isolation, and known risks.

## CI/CD and App Distribution

Pull requests validate formatting, static analysis, Android Lint, JVM/Compose/
ViewModel/widget tests, Firestore rules, a debug APK, instrumentation tests,
wrapper integrity, dependency changes, secret scanning, and CodeQL where
supported. Pull requests never deploy and never receive production secrets.
The current source inventories are 150 JVM tests, 39 instrumentation tests, and
27 Firestore rules cases; executed counts and conclusions belong in
`VALIDATION_REPORT.md`.

After all validation succeeds on `main`, the deployment path builds with the
stable release key, deploys only Firestore rules/indexes, uploads the signed APK
to the Firebase App Distribution `owners` group, and retains CI artifacts for a
bounded period. Release notes include the commit SHA.

The `production` environment currently uses short-lived Workload Identity
Federation through pool `meds-widget-github` and provider
`meds-widget-main`. It contains both WIF secrets and no JSON fallback secret.
The deployment service account has exactly the four project roles documented in
[CI/CD](docs/CI_CD.md), with no Firebase Viewer or Firestore document-read role.
The successful production run linked above used this WIF path, skipped the JSON
fallback, and deployed through Firebase CLI's supported Application Default
Credentials flow. The temporary user-managed service-account key was deleted
after that proof, leaving zero user-managed keys.

Workflow and secret names, least-privilege permissions, branch-protection check
names, tester onboarding, and rotation steps are documented in
[CI/CD](docs/CI_CD.md). A workflow file existing is not evidence of success;
actual run URLs and conclusions must be recorded in `VALIDATION_REPORT.md`.

## Troubleshooting

### “This build does not contain Firebase configuration”

Confirm `app/google-services.json` exists locally and belongs to the intended
Firebase Android app. Sync/rebuild. Never remove the ignore rule to “fix” this.

### Google sign-in is not configured

Enable the Google provider, add both signing certificates' SHA fingerprints,
download a fresh `google-services.json`, and confirm it contains the generated
web OAuth client. The registered package name must be exact.

### Firestore returns permission denied

Confirm the user is authenticated, the path is under that user's UID, all
required schema-v1 fields are present, and check/event writes are submitted in
one batch. Do not weaken the rules. Reproduce against the emulator first.

### A widget says to choose or reconfigure a medicine

The widget has no configuration for its app-widget ID, belongs to another
signed-in account, or its medicine was archived/deleted. Reconfigure it from
the launcher's widget controls or remove and add it again.

### The logical day did not visibly change at the exact minute

Android may defer background work. Open the app or interact with the widget to
force recomputation. Confirm the device clock/timezone and the reset time.

### A release APK is unsigned

Provide all four signing inputs. The build intentionally does not invent a
release key. Verify the APK with `apksigner verify --print-certs`.

## Further documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Data model](docs/DATA_MODEL.md)
- [Firebase setup](docs/FIREBASE_SETUP.md)
- [CI/CD](docs/CI_CD.md)
- [Security](docs/SECURITY.md)
- [Testing](docs/TESTING.md)
- [Samsung validation](docs/SAMSUNG_VALIDATION.md)
- `VALIDATION_REPORT.md` — generated and updated only with observed results
