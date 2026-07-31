# Architecture

## V1.1 responsive and countdown flow

Both Glance providers use `SizeMode.Exact` and `LocalSize.current`. Shared
`WidgetLayout` thresholds consider width and height and select compact,
standard, or spacious typography, padding, and row heights. Compose previews
consume the same tokens.

Countdown configuration lives on each medicine slot. `CountdownLogic` derives
display solely from `startedAt`, `targetAt`, and the current clock.
`FirestoreCountdownRepository` writes deterministic `countdownStates` plus
immutable random-ID `countdownEvents`. `WidgetSnapshotStore` applies an
account-bound optimistic start, while `CountdownRefreshScheduler` schedules
only the next adaptive 10/5/1-minute one-time WorkManager refresh, bounded by
the target.

`StartCountdownAction` and `CheckDoseAction` are distinct Glance callbacks and
click targets. They validate authoritative Firebase Auth, widget configuration
where applicable, and cached ownership/eligibility. Checking consumes an
active timer but never waits for it. Widget cancellation, restart, and undo are
prohibited. Both callbacks keep public zero-argument constructors and narrow
R8 preservation verified from minified APK DEX.

## Goals and boundaries

Meds Widget V1 is one native Android application. Its architecture is optimized
for four properties:

- domain rules remain testable without Compose, Glance, or Firebase;
- the app and every widget instance derive rows from the same medicine/day
  model;
- widgets render from local state and never depend on a fresh network request;
  and
- all private cloud data is rooted under the authenticated Firebase UID.

The design intentionally excludes a custom server, Cloud Functions, Cloud
Storage, analytics, reminders, notifications, exact alarms, and background
polling.

## Component map

```text
┌───────────────────────────────────────────────────────────────┐
│ UI                                                            │
│ MainActivity · Compose screens · ViewModels                   │
└───────────────────────┬───────────────────────────────────────┘
                        │ Flow/state + commands
┌───────────────────────▼───────────────────────────────────────┐
│ Domain                                                        │
│ logical day · validation · rows/progress · action policy      │
│ history assembly · display transforms                         │
└───────────────────────┬───────────────────────────────────────┘
                        │ repository interfaces
┌───────────────────────▼───────────────────────────────────────┐
│ Data / Firebase                                               │
│ FirebaseAuth · Firestore medicines/settings/states/events     │
│ Firestore Android offline persistence                         │
└───────────────────────┬───────────────────────────────────────┘
                        │ snapshot projection
┌───────────────────────▼───────────────────────────────────────┐
│ Widget                                                        │
│ account-scoped DataStore snapshot · per-ID configuration      │
│ Glance 2×2 and 4×2 · central update coordinator               │
└───────────────────────┬───────────────────────────────────────┘
                        │ temporal refresh
┌───────────────────────▼───────────────────────────────────────┐
│ Sync                                                          │
│ WorkManager reset boundary · boot/date/time/timezone receiver │
└───────────────────────────────────────────────────────────────┘
```

## Package responsibilities

### `domain`

The domain package has no Android UI or Firebase dependency.

- `Models.kt` defines medicines, settings, dose states/events, UI-neutral rows,
  progress, authentication sessions, and explicit content states.
- `LogicalDayCalculator` converts an `Instant`, `ZoneId`, and reset-minute value
  into a `LocalDate`; it also calculates the next reset boundary.
- `MedicineValidator` trims fields, bounds medicine names at 100 characters and
  labels at 60, and requires at least one enabled slot. A blank label is an
  error for an enabled slot; a blank label on a disabled slot is normalized to
  that slot's default so the persisted schema never receives an empty label.
- `DoseIds` creates
  `<yyyy-MM-dd>_<medicineId>_<afternoon|night>`.
- `DoseActionPolicy` makes a repeated check a no-op and rejects widget undo.
- `DoseRows` projects active medicines and the current day's state documents
  into rows and progress.
- `HistoryAssembler` pairs immutable check/undo events without rewriting the
  original medicine or label snapshots.
- `DisplayTransform` performs deterministic ellipsis truncation for constrained
  surfaces.

### `data`

`Repositories.kt` defines narrow interfaces for authentication, medicines,
doses, settings, and account deletion. UI and widget action code depend on
these contracts rather than document paths.

`UnavailableRepositories.kt` supplies safe behaviour when Firebase was not
configured into the build. This lets compile/test builds omit
`google-services.json` without masquerading as a working cloud build.

### `firebase`

- `FirebaseAuthRepository` maps Firebase users to `AuthSession`, supports
  email/password and Google credentials, resets passwords, updates display
  names, reauthenticates, signs out, and deletes the auth account.
- `FirestoreMedicineRepository` owns active/all medicine listeners and
  create/update/archive/delete writes. Asynchronous write rejection is merged
  back into the medicine `DataEnvelope` as a friendly error instead of being
  silently dropped.
- `FirestoreDoseRepository` listens for current-day state and recent events.
  It submits each check or undo as one batched state/event write. A later batch
  rejection is published through dose state, and the rejected optimistic
  in-memory transition is rolled back.
- `FirestoreSettingsRepository` mirrors the single cloud preferences document
  into a UID-keyed Preferences DataStore. Activating another UID clears the
  prior cache; late listener persistence checks the active UID before writing.
- `FirestoreAccountDataRepository` deletes known child collections in batches
  before deleting the user document. Every deletion query explicitly uses
  `Source.SERVER`, so completion requires a current server response.
- `FirestorePaths` is the single source of Firestore collection/document names.
- `FirestoreMappers` performs defensive snapshot-to-domain conversion.

Firestore listeners report `isFromCache` and `hasPendingWrites` through
`DataEnvelope`; callers can show a non-blocking cached/sync-pending status.

### `ui`

`MainActivity` owns navigation and Android Credential Manager integration. It
constructs ViewModels with a small factory; composables receive immutable state
and callbacks.

- `AuthScreen` supports sign-in, sign-up, password reset, and Google sign-in.
- `MainScreen` renders the logical day, progress, active medicines, app-only
  undo confirmation, and functional widget previews.
- `MedicineScreen` validates create/edit input and exposes archive/delete
  actions.
- `HistoryScreen` groups snapshot-preserving history by logical day.
- `SettingsScreen` manages reset time, timezone display, theme, display name,
  sign-out, account deletion, version, and the widget privacy warning.

Business rules are not implemented inside composables. ViewModels coordinate
repositories and domain functions. XML-declared application/widget picker
labels are string resources. Most current Compose screen copy is inline; its
accessibility semantics and wording must be preserved when those screens are
changed.

### `widget`

- `WidgetSnapshotStore` serializes a compact `WidgetSnapshot` into Preferences
  DataStore. It contains only what Glance needs: account ownership, current
  logical day, active medicine display data, dose rows, persisted pending-action
  correlations, and cache/pending/error metadata. Snapshot read/modify/write
  transitions run inside one atomic DataStore edit. Its compact status is
  `Syncing` when writes are pending, otherwise `Cached` when the source is
  cached or a safe refresh error exists.
- `WidgetConfigurationStore` stores `ownerUid` and `medicineId` for each Android
  app-widget ID. Widget 41 and widget 52 therefore remain independent.
- `SingleWidgetConfigurationActivity` returns `RESULT_CANCELED` until a valid
  medicine has been saved, then persists the exact widget ID configuration.
- `SingleMedicineWidget` is responsive around 2×2 and renders only its selected
  medicine. The `xml-v28` provider metadata adds
  `widgetFeatures="reconfigurable"` on Android 9/API 28 and newer while the base
  XML retains add-time configuration compatibility.
- `AllMedicinesWidget` is responsive around 4×2 and uses a Glance `LazyColumn`,
  so rows that do not fit remain reachable by vertical scrolling.
- `CheckDoseAction` validates source, signed-in UID, single-widget
  configuration, selected medicine, and enabled slot before changing state. Its
  optimistic action ID is reused for the Firestore state/event batch and later
  completion correlation.
- `WidgetUpdateCoordinator` updates every instance of both widget providers.

Checked widget rows open the app; there is no widget undo callback.

### `sync`

`ResetBoundaryScheduler` uses replaceable, one-time WorkManager work for the
next logical-day boundary. `SystemTimeChangeReceiver` requests the same temporal
refresh after:

- boot completed;
- date changed;
- manual clock/time changed; and
- timezone changed.

There is no exact-alarm permission. WorkManager is best effort, so the logical
day is also recomputed synchronously at app resume, widget render, widget
action, and repository refresh entry points.

`AccountOperationGate` is also in `sync`. One graph-owned mutex serializes
medicine, dose, settings, sign-out, and widget check mutations. Deletion marks
the gate before waiting for an already-running mutation, rejects later
mutations, and then runs exclusively. Failure before Authentication deletion
reopens the gate; success leaves it closed until the graph is replaced.

### Settings and `util`

There is no dedicated `settings` source directory in the current V1 tree.
Settings span the domain model, Firebase/DataStore repository, settings
ViewModel/screen, and reset scheduler. `util` contains locale-aware time
formatting shared by app and widgets.

## Application graph and lifecycle

`MedsApplication.onCreate()` initializes Firebase if a valid resource config is
available, creates `AppGraph`, starts its application-scoped collectors, and
observes `ProcessLifecycleOwner`.

`AppGraph` owns:

- a `SupervisorJob` application scope;
- repository instances;
- settings, snapshot, and per-widget DataStores;
- a shared `AccountDaySnapshot` state flow and integer temporal tick;
- the account-wide operation gate;
- the central widget update coordinator; and
- the reset scheduler.

When the auth session becomes null, the graph clears account content from the
widget snapshot and updates both widget types. When signed in, it activates the
settings cache for that UID. Cloud listeners start only after the process
lifecycle reaches foreground/start: one cloud-settings listener runs alongside
the shared medicine/day pipeline. The pipeline combines active medicines,
local settings, and the temporal tick; calculates the logical day; then
collects that day's dose states into `AccountDaySnapshot`. `MainViewModel`
consumes this shared state instead of opening duplicate Firestore listeners.
The graph also projects each snapshot into widget DataStore and updates all
widget instances.

Collection uses `collectLatest` and callback-flow cleanup removes Firestore
listeners when their collectors are cancelled. `MedsApplication.onStop()` marks
the process backgrounded, which cancels the settings/medicine/day Firestore
collectors; `onStart()` recreates them from the current UID and cached state. A
UID switch likewise cancels the old account pipeline and its settings listener.
Settings persistence also checks its stored `owner_uid` before accepting an
asynchronous cloud result, so a cancelled old listener cannot repopulate the
new account's cache.

`refreshTemporalState()` recomputes the current logical day and rolls the local
widget snapshot when needed. If the day changed, it clears the now-stale shared
account snapshot and increments the temporal tick to restart the day-specific
dose listener; it then refreshes widgets and reschedules the next boundary.
Widget rendering uses the lighter
`prepareTemporalStateForWidgetRender()` path: it performs the same day
recomputation/stale-state invalidation but does not request an extra widget
update from inside the widget's own render.

`refreshFromRepositories()` also runs that recomputation and reprojects its
current `AccountDaySnapshot` only when both UID and logical day still match, so
a generic refresh cannot write yesterday's rows back over a rolled widget
snapshot.

## Check flow

### Full app or live preview

```text
tap unchecked row
  → MainViewModel identifies current session/medicine/day
  → AccountOperationGate admits the mutation or rejects it during deletion
  → FirestoreDoseRepository.check
  → in-process policy/mutex rejects a repeated active check
  → batch writes doseStates/{deterministicId} + doseEvents/{randomId}
  → Firestore local cache emits pending state immediately
  → AppGraph projects and writes the widget snapshot
  → WidgetUpdateCoordinator refreshes both widget providers
  → Firestore syncs when connectivity is available
```

The app uses source `app`; the live preview uses `app_preview`.

### Home-screen widget

```text
tap unchecked row
  → CheckDoseAction activates the live UID and recomputes temporal state
  → replace stale signed-out/switched-account cache before reading it
  → validate auth/account/configuration/slot
  → atomically store the optimistic row + random CREATED action ID
  → schedule network-constrained WorkManager reconciliation
  → update all widgets
  → AccountOperationGate admits or rejects the repository mutation
  → submit the same action ID with widget_2x2 or widget_4x2
  → mark the accepted action SUBMITTED
  → reject + repository-recover non-cancellably on local submission failure
  → Firestore completion callback resolves that exact persisted action ID
      success: remove its pending entry; clear Syncing when none remain
      failure: clear pending, roll back the row, store a safe Cached error
  → after process death, reconcile submitted actions from authoritative state
      and expire/reconcile an abandoned CREATED action after a grace period
  → update widgets after either asynchronous outcome
```

An already checked widget row launches the app rather than calling the action.
The outcome callback uses the graph application scope rather than the
foreground snapshot-listener pipeline. It therefore resolves the widget state
while the process is backgrounded and those listeners are paused.
Foreground projections never clear action markers themselves. The recovery
worker is the only projection allowed to resolve a persisted action without its
original task callback, and only after obtaining non-cached state with no
Firestore pending writes.

## Undo and audit flow

Undo is reachable only through an app confirmation dialog. The repository
requires a currently taken state, updates it to `isTaken = false`, records
`undoneAt`, assigns a new `lastActionId`, and creates an immutable `undo` event
in the same batch. It does not clear `checkedAt`, the original timezone, or the
original source.

`HistoryAssembler` pairs each undo to its exact check through
`previousActionId`; device clock rollback cannot reverse that causal
relationship. Renaming or deleting a medicine later does not rewrite history
because each event carries medicine-name and slot-label snapshots.

## Logical-day model

For an occurrence instant `I`, device timezone `Z`, and reset minutes `R`:

```text
local = I in Z
logical day = local date       when local time >= R
logical day = local date - 1   when local time <  R
```

The next-boundary calculation inspects `ZoneRules`. A reset inside a skipped
hour occurs at the gap transition. During a repeated hour, it schedules each
instant where the literal local-time rule changes—including the clock rollback
and the second reset occurrence when applicable. No scheduled job deletes or
resets documents. A new deterministic ID namespace makes all rows appear
unchecked for the new day while old states/events remain queryable.

## Offline and consistency model

Local persistence is split by purpose:

| Store | Purpose | Account isolation |
| --- | --- | --- |
| Firestore Android persistence | Query/listener data and queued cloud writes | Firebase client/auth scope |
| Preferences DataStore widget snapshot | Fast, network-independent Glance rendering | Snapshot `ownerUid`; cleared on sign-out |
| Preferences DataStore widget configuration | App-widget ID → owner/medicine mapping | Stored owner UID checked at render/action |
| Preferences DataStore settings | Reset time, timezone, display name, theme before cloud load | Stored `owner_uid`; cleared before another UID activates and on sign-out/delete |

The widget projection preserves Firestore's cache and pending-write metadata,
including persisted action IDs for optimistic home-screen checks. Both widgets
show `Syncing` while Firestore metadata or any correlated widget action remains
pending. A matching success removes only its action and clears `Syncing` once
none remain. A matching failure removes its action, rolls back its row, stores a
safe error, and renders `Cached`; a stale/unrelated outcome cannot mutate the
snapshot. Repository projections atomically merge any still-pending optimistic
rows from the current snapshot; because current state is read inside that same
transaction, a projection cannot resurrect an action marker that a concurrent
completion already resolved. These state transitions and logical-day rollover
are DataStore-atomic.

Cloud Firestore documents Android offline persistence and last-write-wins
conflict resolution at
<https://firebase.google.com/docs/firestore/manage-data/enable-offline>.

The V1 duplicate-tap guard is strong within one application process:

- one mutex serializes check/undo decisions;
- an active in-memory state map makes a repeated taken check a no-op;
- state IDs are deterministic; and
- the rules require a matching atomic event/state pair.

It is not a distributed transaction across disconnected devices. Two devices
that act on stale offline state can queue competing writes; when synchronised,
Firestore's document conflict semantics apply. The immutable event IDs may
retain both attempts if both batches are accepted in sequence. This limitation
is documented rather than hidden.

## Account deletion

Deletion is client-side because V1 does not use Cloud Functions:

```text
confirm → recent-login reauthentication
        → close AccountOperationGate to new account mutations
        → wait for any admitted mutation to finish
        → delete settings/medicines/doseStates/doseEvents in ≤400-doc batches
        → delete users/{uid}
        → delete Firebase Authentication account
        → best-effort settings/snapshot/widget configuration clearing
        → best-effort widget signed-out refresh
        → best-effort AppGraph stop, Firestore termination and clearPersistence
        → rebuild AppGraph and restart MainActivity task
```

This is deliberately limited. The client only knows the four V1
subcollections; adding another collection requires updating the deletion
repository. Each 400-document query uses Firestore `Source.SERVER`: cached
emptiness is never accepted as proof that the cloud collection is empty.
Consequently, the operation needs connectivity and is not atomic across all
batches and Firebase Authentication. A crash, revoked session, quota
exhaustion, or network failure may leave partial data and requires retry or
administrator-assisted cleanup. A server-side recursive delete would improve
this but is excluded by the no-Cloud-Functions/no-paid-infrastructure boundary.
While deletion is active, `MainActivity` renders a blocking progress surface
before evaluating the signed-in/signed-out authentication branch. Sign-in,
navigation, settings actions, medicine/dose actions, and widget checks therefore
cannot race the deletion or appear during the session-null transition.

After cloud and Authentication deletion, each app-managed cleanup action is
attempted independently: DataStore clearing, widget refresh, graph shutdown,
Firestore termination, and `clearPersistence()`. Authentication deletion is
already irreversible, so a local cleanup failure does not strand the old UI;
the flow still requests a new graph and activity-task restart. The new
signed-out graph and UID checks maintain account isolation, while failed cleanup
remains a best-effort limitation. Firebase's clear removes the SDK's logical
persistent cache; it is not a secure-overwrite primitive and makes no guarantee
that underlying storage blocks cannot be recovered on a compromised device.
Clear Android app data as an additional precaution on a shared or high-risk
device.

## Dependency and build design

- Gradle Kotlin DSL and a version catalogue centralize pinned versions.
- Dependency locking covers the root, settings, and app projects; their
  lockfiles are committed.
- The committed wrapper uses Gradle 9.6.1.
- Java/Kotlin compile against the explicit Java 17 toolchain.
- Core-library desugaring supplies `java.time` support down to API 26.
- The Firebase BOM keeps Auth and Firestore mutually compatible.
- Compose and Glance remain separate UI implementations; they share domain
  models and wording, not composable code.
- Release R8 shrinking and resource shrinking are enabled.
- Release signing is conditional on explicit local/CI secrets; no key is
  generated silently.
- Android backups are disabled to reduce unintended transfer of cached private
  data.

## Design decisions

### Why no exact alarm?

The boundary refresh is not a medical reminder and does not need exact
wall-clock execution. Recomputing on every entry point provides correctness
without the privileged exact-alarm permission.

### Why a state collection and an event collection?

`doseStates` makes today's checkbox lookup compact. `doseEvents` retains the
append-only audit trail needed to represent check followed by undo. One does
not substitute for the other.

### Why DataStore for widgets?

Glance runs independently of the activity lifecycle and may render after
process death. A compact local snapshot avoids indefinite network access and
keeps multiple widget instances responsive.

### Why manual dependency injection?

The object graph is small and has clear repository interfaces. A DI framework
would add build/runtime complexity without improving V1's test seams.

## References

- Glance overview: <https://developer.android.com/develop/ui/compose/glance>
- Glance configuration:
  <https://developer.android.com/develop/ui/compose/glance/configuration>
- WorkManager: <https://developer.android.com/topic/libraries/architecture/workmanager>
- DataStore: <https://developer.android.com/topic/libraries/architecture/datastore>
- Firestore offline behaviour:
  <https://firebase.google.com/docs/firestore/manage-data/enable-offline>
- Firestore batched writes:
  <https://firebase.google.com/docs/firestore/manage-data/transactions>
