# Data model

## Ownership root

All cloud data is private to one Firebase Authentication UID:

```text
users/{uid}
├── settings/preferences
├── medicines/{medicineId}
├── doseStates/{logicalDay}_{medicineId}_{slot}
└── doseEvents/{eventId}
```

There are no public profiles and no collection-group queries. Every stored
document includes `ownerUid`, and the rules require it to equal both `{uid}` in
the path and `request.auth.uid`.

All documents use `schemaVersion = 1`. Rules reject unknown fields rather than
silently accepting an expanded payload.

## Wire conventions

| Concept | Values / format |
| --- | --- |
| Logical day | ISO local date: `yyyy-MM-dd` |
| Slot | `afternoon`, `night` |
| Check source | `app`, `app_preview`, `widget_2x2`, `widget_4x2` |
| Dose action | `check`, `undo` |
| Theme | `system`, `light`, `dark` |
| Medicine ID | random UUID string, maximum 128 identifier characters |
| Dose state ID | `<logicalDay>_<medicineId>_<slot>` |
| Event ID | random UUID; immutable after creation |
| Device time/timezone | UTC Firestore timestamp plus IANA/Android timezone ID |
| Sync metadata | server timestamp |

Supported identifier fields use letters, digits, underscore, and hyphen.

## Root user document

Path: `users/{uid}`

The rules reserve a strict root document for ownership/lifecycle metadata:

| Field | Firestore type | Constraint |
| --- | --- | --- |
| `ownerUid` | string | Equals `{uid}` |
| `createdAt` | timestamp | Server timestamp on create; immutable |
| `updatedAt` | timestamp | Server timestamp |
| `schemaVersion` | integer | Exactly `1` |

The root document is not required to discover child data, and the current
client primarily uses it as the final account-deletion target. Listing the
top-level `users` collection is denied.

## Settings

Path: `users/{uid}/settings/preferences`

Only the `preferences` document ID is accepted.

| Field | Firestore type | Constraint / meaning |
| --- | --- | --- |
| `ownerUid` | string | Equals `{uid}` |
| `resetMinutesAfterMidnight` | integer | `0..1439`; default `0` |
| `timezoneId` | string | Non-empty, at most 100 characters |
| `displayName` | string | Trimmed, 1–80 characters |
| `themePreference` | string | `system`, `light`, or `dark` |
| `updatedAt` | timestamp | Server timestamp |
| `schemaVersion` | integer | Exactly `1` |

`resetMinutesAfterMidnight` is independent of a medicine. Examples:

| Value | Reset |
| ---: | --- |
| `0` | 12:00 AM |
| `360` | 6:00 AM |
| `780` | 1:00 PM |
| `1439` | 11:59 PM |

The device's current timezone drives the logical-day calculation. `timezoneId`
records the timezone seen when settings were last written; it is not a fixed
timezone override.

## Medicine

Path: `users/{uid}/medicines/{medicineId}`

| Field | Firestore type | Constraint / meaning |
| --- | --- | --- |
| `id` | string | Equals `{medicineId}`, 1–128 safe identifier characters |
| `ownerUid` | string | Equals `{uid}` |
| `name` | string | Trimmed, 1–100 characters |
| `afternoonEnabled` | boolean | Enables the afternoon row |
| `afternoonLabel` | string | 1–60 characters; default `Afternoon` |
| `nightEnabled` | boolean | Enables the night row |
| `nightLabel` | string | 1–60 characters; default `Night` |
| `archived` | boolean | Archived medicines are omitted from current rows/widgets |
| `createdAt` | timestamp | Server timestamp on create; immutable |
| `updatedAt` | timestamp | Server timestamp |
| `schemaVersion` | integer | Exactly `1` |

At least one of `afternoonEnabled` and `nightEnabled` must be true. Both label
fields remain bounded even when their slot is disabled so enabling a slot later
has a valid value. Validation trims every label. Whitespace-only input for an
enabled slot remains empty and is rejected; whitespace-only input for a
disabled afternoon/night slot is normalized to `Afternoon`/`Night`
respectively before persistence.

Archive changes visibility but keeps the medicine document. Delete removes the
medicine document. Neither action removes dose states or audit events; those
contain snapshots for history.

## Dose state

Path:
`users/{uid}/doseStates/{logicalDay}_{medicineId}_{afternoon|night}`

This mutable projection answers “is this dose currently taken for this logical
day?” without replaying all audit events.

| Field | Firestore type | Constraint / meaning |
| --- | --- | --- |
| `ownerUid` | string | Equals `{uid}` |
| `logicalDay` | string | `yyyy-MM-dd`; also part of document ID |
| `medicineId` | string | Safe identifier, also part of document ID |
| `slot` | string | `afternoon` or `night`, also part of document ID |
| `labelSnapshot` | string | Label at the check, 1–60 characters |
| `medicineNameSnapshot` | string | Medicine name at the check, 1–100 characters |
| `isTaken` | boolean | Current active state |
| `checkedAt` | timestamp | Exact device occurrence time of the retained check |
| `checkedTimezone` | string | Device timezone used when checked |
| `checkedSource` | string | Source that performed the retained check |
| `undoneAt` | timestamp or null | Device occurrence time of undo |
| `lastActionId` | string | Event document paired with the latest transition |
| `updatedAt` | timestamp | Server timestamp for sync metadata |
| `schemaVersion` | integer | Exactly `1` |

Example deterministic ID:

```text
2026-07-29_550e8400-e29b-41d4-a716-446655440000_afternoon
```

A new state must be taken, have `undoneAt = null`, and be atomically paired with
a matching check event. An update may only be:

- taken → not taken, changing `isTaken`, `undoneAt`, `lastActionId`, and
  `updatedAt`; or
- not taken → taken, refreshing the display/time/source snapshot fields,
  clearing `undoneAt`, and changing `lastActionId`/`updatedAt`.

Identity, owner, logical day, medicine ID, and slot cannot change.

Undo deliberately retains `checkedAt`, `checkedTimezone`, and `checkedSource`.
The prior check event also remains in `doseEvents`.

## Dose event

Path: `users/{uid}/doseEvents/{eventId}`

Events are immutable after creation. Owner-only delete exists solely for
client-side account deletion.

| Field | Firestore type | Constraint / meaning |
| --- | --- | --- |
| `eventId` | string | Equals `{eventId}` |
| `ownerUid` | string | Equals `{uid}` |
| `action` | string | `check` or `undo` |
| `logicalDay` | string | `yyyy-MM-dd` |
| `medicineId` | string | Medicine at occurrence |
| `medicineNameSnapshot` | string | Name at occurrence, 1–100 characters |
| `slot` | string | `afternoon` or `night` |
| `labelSnapshot` | string | Label at occurrence, 1–60 characters |
| `occurredAt` | timestamp | Exact device occurrence time |
| `timezoneId` | string | Device timezone at occurrence |
| `source` | string | Supported check source |
| `relatedStateId` | string | Deterministic state ID |
| `previousActionId` | string or null | `null` for checks; an undo points to its exact check |
| `syncedAt` | timestamp | Firestore server timestamp |
| `schemaVersion` | integer | Exactly `1` |

Undo events must use source `app`. The rules therefore reject an undo claiming
to come from either widget or the live preview.

## Atomic check and undo writes

State and event are one Firestore batched write. Security Rules use
`getAfter()` in both directions:

- a state create/update must find the matching post-batch event at
  `lastActionId`; and
- an event create must find the matching post-batch state at
  `relatedStateId`.

The pair must agree on owner, logical day, medicine, snapshots, slot, action
time, source, relationship ID, and the prior state's `lastActionId`. A client
cannot create only the state or only the audit event.

Firestore batched writes are persisted offline on Android and sent when the
device reconnects:
<https://firebase.google.com/docs/firestore/manage-data/transactions>.

The device timestamp is retained for user-visible history. `updatedAt` and
`syncedAt` use server timestamps only as authoritative sync metadata; they do
not replace the occurrence time.

## Idempotency

Idempotency uses several layers:

- an account-wide mutation gate that excludes account deletion;
- one deterministic state ID per logical-day/medicine/slot;
- an in-process mutex around dose actions;
- an active-state map that turns a repeated taken check into a no-op;
- an optimistic widget snapshot that refuses to mark an already-taken row
  again; and
- rule-enforced, atomically matching state and event documents.

Every genuine transition uses a new event UUID. A check after a confirmed undo
is a new event and refreshes the state snapshot.

This is retry-safe for rapid repeated taps in one process. It is not a global
serializable lock between two disconnected devices. Firestore resolves
competing writes to the same state document when devices reconnect; see
[Architecture](ARCHITECTURE.md#offline-and-consistency-model).

## Logical-day namespace

No daily reset job deletes data. Rows are built for exactly one calculated
logical day:

```text
state ID = logicalDay + "_" + medicineId + "_" + slot
```

At the next day, the medicine rows still exist but no state document with the
new prefix exists, so they appear unchecked. Old state and event documents
remain available for history/account deletion.

## History projection

The history listener queries up to the newest 500 events by:

```text
syncedAt DESC
```

`HistoryAssembler` pairs an undo directly to the check named by
`previousActionId`, then returns entries descending by the check's device
occurrence time. Pairing therefore remains correct if the device clock moves
backward or the two device timestamps are equal. A history entry uses only
event snapshots. Current medicine names and labels are never joined onto old
events.

Every check remains a separate history entry. The 500-event window means a very
old matching check can fall outside V1's displayed history even though the
document remains in Firestore.

## Queries and indexes

`firestore.indexes.json` defines only the composite indexes used by actual
queries:

| Collection | Query |
| --- | --- |
| `medicines` under one user | `archived ASC`, then `createdAt ASC` |

The current-day state query filters only `logicalDay`, which uses Firestore's
automatic single-field index. History orders only `syncedAt`, also covered by
an automatic single-field index. There are no collection-group indexes or
queries.

## Local data

Preferences DataStore contains no password, Firebase token, service-account
credential, or signing secret.

These app-managed DataStores are distinct from Firestore's SDK-managed
persistent cache. Sign-out clears settings and account content from the widget
snapshot, while retaining owner-scoped widget selections that cannot render for
another UID; SDK persistence also remains in place. Successful account deletion
attempts to clear all three DataStores, update widgets, terminate Firestore, and
call `clearPersistence()`, then rebuilds the application graph and restarts the
activity task even if a post-Authentication local cleanup action fails. That SDK
operation is a logical cache deletion, not a guarantee of secure physical
overwrite; Android app-data removal remains the additional precaution for a
shared/high-risk device.

### Settings DataStore: `meds_settings`

```text
owner_uid
reset_minutes
timezone
display_name
theme
```

`owner_uid` is an internal cache partition key, not a display setting.
`activateAccount(uid)` clears every old preference before assigning a different
UID. A cloud-listener result is persisted only while its UID still equals that
key, which prevents a cancelled listener from repopulating the cache after an
account switch. The cache provides reset/theme values before a cloud snapshot
arrives and is cleared on sign-out/account deletion.

### Widget snapshot DataStore: `widget_snapshot`

The `snapshot_json` value includes:

- `ownerUid` and `signedIn`;
- current `logicalDay`;
- active medicine IDs, names, enabled flags, and labels;
- current dose rows and compact checked timestamps;
- pending widget action IDs with medicine/slot correlation, creation instant,
  and created/submitted handoff state; and
- `fromCache`, aggregate `hasPendingWrites`, repository-only pending state, and
  safe error text.

All snapshot mutations use a single Preferences DataStore edit, so concurrent
optimistic checks and asynchronous outcomes cannot overwrite one another with a
stale read/modify/write result. Repository snapshot writes merge rows for
currently pending medicine/slot keys while reading the current marker set in
that same transaction. They therefore preserve unresolved optimistic display
state but cannot resurrect a marker already removed by a completion callback. A
widget check persists its action ID in the same edit that marks the row taken.
The Firestore batch reuses that ID:

- foreground repository projections preserve both created and submitted
  actions;
- a network-constrained authoritative projection may resolve submitted actions
  after process recreation;
- an abandoned created action is given a grace period, rolled back, and then
  checked against authoritative state so an already-queued Firestore batch is
  not lost visually;
- success removes its pending correlation and clears `Syncing` when no pending
  actions or Firestore writes remain;
- failure removes its correlation, rolls its row back to unchecked, and stores
  a safe error that renders `Cached`; and
- an outcome for an unknown action ID or another UID is ignored.

This completion path is independent of foreground snapshot listeners. It can
resolve the persisted optimistic state while those listeners are paused.

The snapshot is replaced with signed-out content on sign-out. Post-
Authentication deletion cleanup attempts the same replacement before graph
restart. Widgets derive one compact status: `Syncing` when `hasPendingWrites` is
true; otherwise `Cached` when `fromCache` is true or safe error text exists;
otherwise no status.

### Widget configuration DataStore: `widget_configuration`

For each Android app-widget ID `N`:

```text
widget_N_owner
widget_N_medicine
```

Deleting a 2×2 widget removes only its two keys. Account deletion attempts to
clear all widget configurations. A retained configuration whose owner no longer
matches the snapshot is rendered as unavailable rather than leaking another
account's medicine.

## Account deletion coverage

The V1 client explicitly enumerates:

- `settings`;
- `medicines`;
- `doseStates`; and
- `doseEvents`.

It deletes at most 400 documents per batch, repeats until each collection is
empty, and deletes `users/{uid}` last. Collection reads explicitly require
Firestore `Source.SERVER`; an offline/cached empty result cannot authorize
completion. This is not a recursive server-side operation and is not atomic
across collections. Any future subcollection must be added to this list and to
security-rule tests.

The graph-wide account operation gate closes before deletion waits for an
already-admitted medicine/dose/settings/widget mutation. New mutations are
rejected until failure reopens the gate or successful deletion replaces the
entire graph. After Authentication deletion, local cache cleanup is best effort
and graph/activity restart still completes.
