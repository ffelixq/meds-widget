# Testing

## Evidence policy

A test source file, workflow definition, or zero exit from a task with no
discovered tests is not evidence that the required behaviour passed. For every
delivery, `VALIDATION_REPORT.md` records:

- exact command;
- UTC/local date and time;
- terminal result;
- discovered/executed test count from the generated report;
- warnings/skips;
- commit SHA; and
- CI run URL for hosted-only checks.

Do not copy annotated source counts into the validation report without running
the suite. Local XML output from an earlier JVM run is evidence only for that
working-tree state, not for a later commit or CI run.

No test uses production user data. Firestore rules tests use an isolated
`demo-` emulator project and synthetic UIDs.

## Prerequisites

- JDK 17 for Gradle and Android compilation
- JDK 21 or newer for Firebase Emulator Suite commands
- Android SDK platform package 37.0
- Android Build Tools 36.0.0
- Node.js 20 or newer and npm
- Firebase CLI
- an API 35+ emulator for connected tests
- a physical Samsung phone for One UI validation

Install locked Node dependencies:

```bash
npm ci --prefix firebase-tests
```

`google-services.json` is not required for JVM/static/debug-build validation.
When absent, the application uses explicit unavailable repositories. Real
Firebase authentication/cloud smoke testing requires the ignored config.

## Command reference

| Purpose | Command |
| --- | --- |
| Formatting check | `./gradlew formatCheck` |
| Formatting correction | `./gradlew formatApply` |
| Detekt | `./gradlew detekt` |
| Android Lint | `./gradlew lint` |
| JVM/domain/ViewModel/widget/Glance | `./gradlew testDebugUnitTest` |
| Debug APK | `./gradlew assembleDebug` |
| Release APK/AAB | `./gradlew assembleRelease bundleRelease` |
| Connected instrumentation | `./gradlew connectedDebugAndroidTest` |
| Firestore rules | `npm test --prefix firebase-tests` |
| Gradle practical suite | `./gradlew fullValidation` |
| Cross-tool practical suite | `./scripts/validate.sh` |
| Cross-tool suite plus emulator | `RUN_INSTRUMENTATION=1 ./scripts/validate.sh` |
| Forbidden tracked files | `./scripts/check-forbidden-files.sh` |
| Permissive-rules guard | `./scripts/check-firestore-rules.sh` |

All validation scripts use strict shell error handling and exit non-zero on a
required failure.

## Practical local suite

```bash
FIREBASE_JAVA_HOME=/absolute/path/to/jdk-21-or-newer \
./scripts/validate.sh
```

`FIREBASE_JAVA_HOME` changes Java only for the rules-test subprocess; the
Android build continues to use its explicit JDK 17 toolchain. The script
auto-detects JDK 17 on standard installations and Homebrew macOS installations;
set `MEDS_GRADLE_JAVA_HOME` when it lives elsewhere. `FIREBASE_JAVA_HOME` may
be omitted when the `java` already on `PATH` is version 21 or newer.
The script defaults to one Gradle worker because parallel AAPT2 startup proved
unreliable on macOS. A known-safe machine may opt in to more workers with
`MEDS_GRADLE_MAX_WORKERS`, which must be a positive integer.

It checks required executables and then runs, in order:

```text
forbidden tracked-file guard
permissive Firestore-rule guard
formatCheck
detekt
lint
testDebugUnitTest
assembleDebug
npm ci --prefix firebase-tests
Firestore emulator rules tests
```

Instrumentation is skipped with an explicit message unless:

```bash
FIREBASE_JAVA_HOME=/absolute/path/to/jdk-21-or-newer \
RUN_INSTRUMENTATION=1 ./scripts/validate.sh
```

The script intentionally does not imitate GitHub's dependency-review service,
CodeQL upload, Gitleaks history action, or wrapper-validation action. Obtain
those conclusions from the hosted workflow.

## JVM test inventory

The JVM tests use JUnit 4, coroutine-test, AndroidX test core, Robolectric,
WorkManager test support, the stable Glance app-widget unit-test APIs, and
repository fakes. Count discovered `@Test` annotations with:

```bash
rg -n '@Test' app/src/test | wc -l
```

This count is a source inventory only. Use Gradle's XML result counts after
execution for the validation report.

The final local source inventory contains 150 tests across 20 concrete test
classes/files:

| Class | Tests |
| --- | ---: |
| `LogicalDayCalculatorTest` | 11 |
| `MedicineValidationTest` | 7 |
| `DoseLogicTest` | 11 |
| `HistoryLogicTest` | 8 |
| `ModelMappingTest` | 6 |
| `UidScopedWriteFailuresTest` | 5 |
| `AccountOperationGateTest` | 2 |
| `OutstandingWriteTrackerTest` | 5 |
| `ResetBoundarySchedulerTest` | 4 |
| `WidgetPendingSyncSchedulerTest` | 1 |
| `AuthViewModelTest` | 7 |
| `MainViewModelTest` | 11 |
| `SettingsViewModelTest` | 14 |
| `TimeFormattingTest` | 1 |
| `SingleMedicineWidgetGlanceTest` | 12 |
| `AllMedicinesWidgetGlanceTest` | 9 |
| `WidgetActionsTest` | 7 |
| `WidgetConfigurationStoreTest` | 7 |
| `WidgetSnapshotTest` | 21 |
| `WidgetUpdateCoordinatorTest` | 1 |

This inventory is not a passing conclusion. Rerun the task after any source
change; the validation report must use the generated XML result from the
delivered commit rather than treating the source count as execution evidence.

### Logical day

`LogicalDayCalculatorTest` covers:

- midnight reset;
- both sides of a custom reset boundary;
- end of month;
- end of year;
- leap day;
- DST spring-forward gaps;
- DST fall-back repeated hours;
- Singapore's no-DST behaviour;
- timezone changes for the same instant;
- manual clock changes; and
- invalid reset-minute bounds.

Tests use fixed instants and explicit `ZoneId` values. They do not depend on the
machine wall clock.

### Medicine and display domain

`MedicineValidationTest` covers trimming, empty names, 100-character name
boundary, at least one slot, enabled-label requirement, 60-character label
boundary, and normalization of blank disabled labels to their safe defaults.

`DoseLogicTest`, `HistoryLogicTest`, and `ModelMappingTest` cover:

- deterministic state IDs;
- check idempotency;
- app undo versus widget-undo rejection;
- enabled/archived row construction;
- progress and empty progress;
- safe ellipsis truncation and rejection of unusably small limits;
- historical medicine/label snapshots;
- check/undo/recheck event assembly;
- orphan/duplicate event handling;
- causal undo pairing despite manual clock rollback or equal device
  timestamps;
- supported wire mappings; and
- explicit signed-out/cached/pending content states.

`ResetBoundarySchedulerTest` uses Robolectric and WorkManager test support to
cover boundary-delay calculation, tagged unique work, supported system
time/date/timezone broadcasts, and ignoring unrelated or missing broadcast
actions. `TimeFormattingTest` verifies that completed times use the timezone
recorded with the dose rather than silently reinterpreting them in the device's
current timezone.

`AccountOperationGateTest` deterministically holds an admitted mutation while
deletion starts, verifies deletion waits and later mutations are rejected, then
verifies a failed deletion reopens the gate.

`UidScopedWriteFailuresTest` covers failure isolation by UID and operation,
including late prior-account failures, unrelated success, correlated success,
and healthy-snapshot clearing for only the intended UID.

### ViewModels

`AuthViewModelTest` covers signed-out/configuration state, auth transitions,
email sign-in, registration, Google-token routing, password reset, loading,
friendly errors, and message clearing.

`MainViewModelTest` uses UID-partitioned medicine/dose fakes to cover:

- signed-out isolation;
- create/edit/archive/delete;
- validation preventing repository writes;
- check and confirmed app undo;
- history changes;
- cached/offline/pending metadata;
- account switching without previous-account rows;
- shared `AppGraph` snapshot consumption without duplicate repository
  listeners;
- check and undo behaviour at a temporal boundary;
- reset-time recomputation; and
- widget-refresh seams.

`SettingsViewModelTest` covers:

- valid/invalid reset changes;
- system/light/dark persistence;
- display-name validation and trimming;
- sign-out local clearing and widget updates;
- password and Google reauthentication paths;
- account data/auth deletion ordering, local cache/widget clearing, and the
  Firestore shutdown/persistence-clear seam;
- duplicate deletion and sign-out rejection while the account gate is closed;
- post-Authentication cleanup failure still completing the deletion/restart
  handoff;
- cached, pending, and failure metadata from settings synchronisation; and
- repository failures leaving busy state safely.

`TestFakes.kt` is test support, not a production alternate backend. Fakes keep
data partitioned by UID and create deterministic event sequences for assertions.

### Widget state/configuration

`WidgetConfigurationStoreTest` uses Robolectric/DataStore to cover:

- two widget IDs selecting different medicines;
- two widget IDs independently selecting the same medicine;
- reconfiguring one instance without changing another;
- owner UID retention across account switching;
- one-widget removal;
- account-wide configuration clearing; and
- missing configuration.

`WidgetSnapshotTest` covers:

- complete JSON round trip;
- malformed payload fallback;
- unsupported-slot row dropping;
- deterministic row mapping;
- per-medicine lookup isolation;
- one-time optimistic checks, persisted action correlation, and repeated-tap
  no-op;
- successful correlated outcomes clearing pending/`Syncing` state;
- failed correlated outcomes rolling back the row and storing a safe error;
- atomic repository projection preserving unresolved optimistic rows without
  resurrecting resolved pending markers;
- created/submitted action handoff, authoritative process-recreation
  reconciliation, and safe expiry of an interrupted pre-submission action;
- preservation of unrelated repository-pending status while a widget action
  resolves;
- stale outcome rejection and concurrent atomic optimistic mutations;
- wrong-account/signed-out/missing medicine rejection and live-auth cache
  isolation before rendering;
- logical-day rollover rebuilding enabled rows unchecked;
- changed-day reporting and same-day no-op;
- compact widget status precedence (`Syncing`, then `Cached`); and
- account snapshot clearing.

`WidgetActionsTest` exercises malformed parameters, signed-out/account
mismatch handling, per-instance 2×2 configuration validation, repeated-tap
idempotency, durable schedule/render/check/submission ordering, and
non-cancellable rollback/recovery after scheduling, rendering, repository, or
coroutine-cancellation failures.
`WidgetUpdateCoordinatorTest` verifies that a central refresh requests both
widget provider types.

These are widget model/persistence/action-policy/coordinator tests. Direct
Glance composition coverage is separate:

`SingleMedicineWidgetGlanceTest` uses
`runGlanceAppWidgetUnitTest` to cover:

- signed-out, missing-configuration, wrong-account, and deleted/archived
  states;
- exactly one configured medicine;
- afternoon-only and night-only layouts;
- unchecked callback versus checked app-opening action, with no widget undo;
- production long-name truncation;
- long-label layout that preserves the completion time;
- an explicit loading state; and
- compact `Syncing`/`Cached` header state.

`AllMedicinesWidgetGlanceTest` uses the same API to cover:

- signed-out and signed-in empty states;
- multiple medicines, every enabled dose, and progress;
- a `LazyColumn` retaining the full row collection;
- unchecked callback versus checked app-opening action;
- production long-name truncation;
- long-label layout that preserves the completion time;
- an explicit loading state; and
- pending/cached header status precedence.

These tests inspect the Glance composition tree and action metadata. Actual
RemoteViews conversion, launcher interaction, Samsung sizing/scrolling, and
update timing still require connected/manual layers.

## Compose and connected Android tests

Connected tests belong under `app/src/androidTest` and run with:

```bash
./gradlew connectedDebugAndroidTest
```

CI provisions:

```text
API level       35
target          google_apis
architecture    x86_64
profile         pixel_2
GPU             swiftshader_indirect
animations      disabled
snapshots       disabled
```

The current source inventory contains 39 `@Test` methods across eight concrete
classes:

| Class | Source tests | Covered surfaces |
| --- | ---: | --- |
| `ApplicationLifecycleTest` | 3 | app start/recreation and missing or invalid widget-configuration ID cancellation |
| `AuthScreenTest` | 6 | signed-out UI, unavailable Firebase, registration validation, password reset, saved state, required display name |
| `FakeAuthenticatedFlowTest` | 1 | fake registration through medicine creation, check, undo, and history navigation |
| `HistoryScreenTest` | 4 | loading, empty, audited check/undo, source/timezone, error, and back states |
| `MainScreenTest` | 7 | loading/empty states, accessibility/check source, undo confirmation, live previews, preview no-undo, navigation |
| `MedicineScreenTest` | 7 | validation, slot requirement, create, edit/save, archive/delete, saved state |
| `SettingsScreenTest` | 9 | reset/theme radio semantics, display name/sign-out, password/Google deletion confirmation, privacy/sync text, blocking deletion UI, saved state |
| `WidgetMedicineSelectionRowTest` | 2 | one accessible radio action per configuration row and selected-state semantics |

This is a source count, not a passing result. It can change while tests are
being edited; only the delivered commit's generated instrumentation report and
terminal command conclusion belong in `VALIDATION_REPORT.md`.

An observed local `:app:compileDebugAndroidTestKotlin` run compiled these
sources successfully. No emulator/connected test conclusion is claimed here.

The connected suite exercises Compose semantics/navigation and Android
lifecycle. Direct Glance composable rendering is covered by the JVM Glance
tests above; connected configuration-activity coverage remains limited to safe
cancellation for missing or provider-unbound widget IDs. It does not exercise
a real launcher-bound configuration or configuration-activity recreation.
Those narrower boundaries must not be described as full launcher-widget
coverage.

The instrumentation report must show that concrete tests were discovered. If
`app/src/androidTest` is empty or Gradle reports no tests, this requirement is
not satisfied even if the Gradle task exits successfully.

## Widget coverage boundaries

Widget validation has four layers:

| Layer | What it proves |
| --- | --- |
| JVM domain/DataStore tests | deterministic rows, progress, configuration isolation, snapshot codec, rollover, check/undo policy |
| JVM Glance app-widget tests | rendered Glance node tree, collection retention, status text, and action wiring |
| Connected Android tests | receiver/configuration lifecycle and Android component integration |
| Physical Samsung checklist | One UI picker names, 2×2/4×2 sizing, scroll gestures, stacks, resize, launcher update timing |

The following matrix must be represented across automated and physical layers:

| Scenario | Expected |
| --- | --- |
| Single widget missing config | choose/reconfigure state |
| Deleted/archived selection | unavailable/reconfigure state |
| Signed out | open app to sign in |
| Afternoon-only | one afternoon row |
| Night-only | one night row |
| Two-slot medicine | both rows |
| Checked row | check/time shown; tap cannot undo |
| Two app-widget IDs | independent medicine mappings |
| All-widget empty | add-medicine state |
| Many medicines | every row reachable by `LazyColumn` scroll |
| Long names/custom labels | safe truncation, no crash |
| Progress | completed/total matches rows |
| Update coordinator | both widget provider types refreshed |

Jetpack Glance composables are distinct from normal Compose composables. A
Compose preview test is not a Glance widget render test; this repository uses
the dedicated Glance unit-test environment for that layer. Even those Glance
tests are not evidence of Samsung launcher behaviour.

## Firestore emulator tests

Run:

```bash
npm test --prefix firebase-tests
```

`firebase-tests/package.json` pins:

```text
@firebase/rules-unit-testing  5.0.1
firebase                     12.16.0
Node engine                  >=20
```

The script starts only Firestore with:

```text
project     demo-meds-widget-rules
host        127.0.0.1
port        8080
UI          disabled
```

Node runs the files in `firebase-tests/test` serially to avoid emulator state
races. The current source inventory contains 27 `it(...)` rule cases.
`beforeEach` clears emulator data; privileged setup uses
`withSecurityRulesDisabled` only to seed documents for denial tests. This
source count is not a passing result; use the Node test summary from an actual
emulator run in `VALIDATION_REPORT.md`.

The current rules suite includes cases for:

- anonymous read/write denial;
- owner medicine create/get/list and indexed query;
- cross-user read/list/write denial;
- forged/wrong `ownerUid`;
- users and collection-group discovery denial;
- strict root/settings/medicine fields and ranges;
- immutable ID/owner/creation time;
- valid atomic check from every supported source;
- rejection of unpaired state/event writes;
- deterministic state ID, logical day, slot, source, and snapshot bounds;
- app-only undo retaining audit;
- widget/app-preview undo denial;
- repeated active-check denial;
- recheck after undo;
- immutable event updates;
- owner state/history queries;
- owner-only deletion of all supported account paths; and
- unknown nested collection denial.

Do not point this suite at the provisioned Firebase project. The `demo-` project
ID and emulator host are deliberate safety controls.

## Static analysis and security checks

### Formatting

`formatCheck` delegates to Android-aware ktlint. Apply changes with
`formatApply`, review the diff, then rerun the check.

### Detekt

The root `detekt` JavaExec task runs the pinned stable CLI against production
Kotlin using `config/detekt/detekt.yml`, default rules, and parallel analysis.

### Android Lint

Lint aborts on errors, checks release builds where invoked, treats warnings as
errors, and emits HTML/XML/SARIF reports. Dependency-version suggestions are
disabled because Dependabot owns upgrades.

### Hosted security checks

CI additionally runs:

- Gradle wrapper validation;
- Gitleaks over full Git history;
- dependency review at moderate severity or higher;
- CodeQL Java/Kotlin; and
- forbidden-file/permissive-rules shell guards.

See [CI/CD](CI_CD.md) for exact job names and permissions.

## Reports and artifacts

Common local outputs after execution:

```text
app/build/test-results/testDebugUnitTest/
app/build/reports/tests/testDebugUnitTest/
app/build/reports/lint-results-*.html
app/build/reports/lint-results-*.xml
app/build/reports/lint-results-*.sarif
app/build/reports/androidTests/
app/build/outputs/androidTest-results/
app/build/outputs/apk/debug/app-debug.apk
firebase-tests/rules-test-output.txt        # CI-captured output
```

Paths can change with Android Gradle Plugin versions; use the task output as
the authority. Do not commit reports or APKs.

## Manual emulator smoke testing

After automated tests, install the debug APK on a disposable emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start \
  -n io.github.ffelixq.medswidget/io.github.ffelixq.medswidget.ui.MainActivity
```

With no Firebase config, verify only the safe unconfigured state. With the
intended ignored config and test account, exercise sign-in, add medicine,
check/undo, history, reset setting, both preview types, sign-out, and account
switch isolation. Never use production medicine data for screenshots or logs.

An emulator does not close the Samsung requirement.

## Physical Samsung validation

Run all 34 steps in [Samsung validation](SAMSUNG_VALIDATION.md). Until then,
report exactly:

```text
REQUIRES_PHYSICAL_SAMSUNG_VALIDATION
```

Do not infer One UI widget scrollability, stacks, responsive sizing, or launcher
refresh behaviour from Pixel/emulator tests.

## Determinism rules for new tests

- Inject `Clock`; never wait for the wall clock.
- Specify `ZoneId`; restore any process-global default timezone in teardown.
- Use `runTest` and test dispatchers; avoid arbitrary sleeps.
- Partition fake data by UID.
- Reset DataStore/application state between tests.
- Use synthetic names, UIDs, and emails.
- Never access the production Firebase project.
- Assert state transitions and side effects, not implementation constants.
- For process recreation, use Android lifecycle recreation rather than manually
  calling private methods.
- A flaky failure remains a failure until its cause is fixed.

## Failure workflow

- Read the complete error and retain the first actionable stack trace.
- Reproduce the smallest failing task.
- Fix the root cause without weakening assertions, rules, lint, or analysis.
- Rerun the focused test.
- Rerun `./scripts/validate.sh`.
- Rerun connected tests if the changed area touches UI/widgets/lifecycle.
- Push and observe a new terminal CI conclusion.
- Record warnings/skips honestly in `VALIDATION_REPORT.md`.
