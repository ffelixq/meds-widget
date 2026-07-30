# Security and privacy

## Security boundary

Meds Widget is a mobile client that talks directly to Firebase Authentication
and Cloud Firestore. Its security boundary is:

- Firebase Authentication establishes `request.auth.uid`;
- every private document is under `users/{uid}`;
- Firestore Security Rules bind `{uid}` and `ownerUid` to the authenticated
  UID; and
- strict schemas and atomic state/event validation constrain client writes.

The Firebase API key in `google-services.json` identifies the Firebase project.
It is not a password and cannot replace Authentication or Security Rules.
Mobile binaries can be inspected, so no authorization decision relies on
keeping that key or a document path secret.

There is no privileged backend in V1. The Android app never uses Admin SDK
credentials, and CI credentials are for deployment only.

## Private data

Cloud data includes:

- Firebase UID, account display name, and authentication-provider metadata;
- medicine names and enabled/custom slot labels;
- reset time, timezone, and theme;
- checked/undo occurrence timestamps and timezone IDs;
- the source surface of each check; and
- medicine/label snapshots retained in history.

No dosage, prescription, diagnosis, interaction, location, contact, camera,
microphone, photo, SMS, phone, notification, advertising, analytics, or payment
data is collected.

Medicine names and status are intentionally displayed by widgets on the
unlocked home screen. Anyone who can see the screen may see them. The app's
Settings screen and README state this explicitly.

## Firestore rules

`firestore.rules` uses rules language version 2 and ends with a recursive
default deny. It does not contain a development grant such as:

```text
allow read, write: if true
```

### Path isolation

- Unauthenticated reads and writes are denied.
- A signed-in user can access only `users/{theirUid}` and its known
  subcollections.
- Listing `users` is denied.
- Collection-group access is denied.
- Unknown top-level and nested collections fall through to default deny.
- Every document's `ownerUid` must match its UID path.

### Schema validation

Rules require exact key sets and reject unknown fields. Bounds are:

| Field | Bound / accepted values |
| --- | --- |
| medicine name | 1–100 characters |
| slot label | 1–60 characters |
| display name | 1–80 characters |
| timezone ID | 1–100 characters |
| identifier | 1–128 letters/digits/underscore/hyphen |
| reset minutes | `0..1439` |
| schema version | `1` |
| slot | `afternoon`, `night` |
| source | `app`, `app_preview`, `widget_2x2`, `widget_4x2` |
| theme | `system`, `light`, `dark` |

Medicine rules require at least one enabled slot and preserve ID, owner, and
creation time on update.

### Dose state and audit invariants

- State IDs must exactly equal
  `<logicalDay>_<medicineId>_<slot>`.
- A state create starts taken with no undo time.
- A state update can only be an allowed check/undo transition.
- Each state write must be atomically paired with the event named by
  `lastActionId`.
- Each event must be atomically paired with its related state.
- Paired payloads must agree on owner, logical day, medicine, snapshots, slot,
  action time, source, and IDs.
- Undo is accepted only from `app`.
- Event updates are always denied.
- Event delete is owner-only and exists solely for account deletion.
- `updatedAt`/`syncedAt` use `request.time` server timestamps.

Rules use `getAfter()` to inspect the post-batch pair. A client cannot create a
checkbox state without its audit event or forge an event unrelated to the
current state.

## Rule validation

Run:

```bash
./scripts/check-firestore-rules.sh
npm test --prefix firebase-tests
```

The shell guard requires UID ownership, `ownerUid`, immutable events, and
default deny, and rejects unconditional/authentication-only broad grants. The
emulator suite covers anonymous denial, owner access, cross-user denial,
payload/schema bounds, deterministic IDs, supported sources, atomic pairing,
check/undo transitions, event immutability, collection query isolation, and
owner-only account deletion.

The guard is defense in depth, not a substitute for emulator tests or manual
review. Rules are OR-composed if multiple matches grant access, so adding a
broad matching rule can defeat a narrower one:
<https://firebase.google.com/docs/rules>.

## Authentication

Supported providers are only:

- Email/Password; and
- Google through Android Credential Manager.

Password reset is delegated to Firebase Authentication. Passwords never pass to
Firestore, DataStore, logs, GitHub, or documentation. Account deletion requires
a recent login:

- password accounts reauthenticate with the entered current password; and
- Google accounts request a new Google ID credential through Credential
  Manager.

Friendly error messages avoid displaying stack traces or tokens. The
application clears Credential Manager state when signed out.
During account deletion, `MainActivity` renders a blocking progress surface
before its authentication branch. A transient null session therefore cannot
expose sign-in, sign-out, navigation, or account actions before the old graph is
replaced.
After reauthentication, deletion waits for every Firestore task already
dispatched for that UID. A timeout leaves cloud data and Authentication intact,
shows a reconnect/synchronisation error, and reopens the mutation gate.
Once Authentication deletion succeeds, local teardown and graph replacement
run in a non-cancellable context; the Activity is responsible only for
restarting its task.

Provider configuration and registered debug/release SHA fingerprints are
required for Google sign-in. The current integration follows
<https://firebase.google.com/docs/auth/android/google-signin>.

## Local storage and account isolation

### Widget snapshot

Preferences DataStore contains an account-scoped display snapshot. Every widget
render/action checks `ownerUid` against the current session. Sign-out replaces
it with signed-out content; post-Authentication deletion cleanup attempts the
same transition before graph restart.
The live Firebase UID is compared synchronously before each render/action, so a
process interrupted between authentication removal and asynchronous cleanup
cannot render the previous account's cached medicine names.
Optimistic row changes, pending action correlations, outcome resolution, day
rollover, and clearing are committed through atomic DataStore edits. Each
home-screen check persists a random action ID; only a completion carrying the
same UID/action ID may retain or roll back that row.

### Widget configuration

Each single-widget app-widget ID stores both owner UID and medicine ID. A
configuration for another account renders a choose/reconfigure state and cannot
submit an action for the new account. Account deletion attempts to clear all
configurations; owner matching keeps a retained mapping inert if best-effort
cleanup fails. Removing one widget deletes only that widget's mapping.

### Settings

Reset time, timezone, display name, and theme are locally cached with an
internal `owner_uid` partition key. Activating a different UID clears the old
preferences first. The prior account's cloud listener is cancelled through
`collectLatest`/`awaitClose`, and its persistence callback checks the active UID
again before writing. Process-lifecycle stop/start events also cancel and
recreate all settings/medicine/dose Firestore listeners so the backgrounded app
does not retain live subscriptions. Settings are cleared on sign-out and are a
best-effort cleanup step after Authentication deletion.

### Firestore persistence

Firestore's Android disk persistence remains enabled by default for useful
offline operation. Security Rules and UID-scoped queries prevent a subsequently
signed-in account from reading another UID's cached documents through the app.
Normal sign-out clears app-managed account caches but does not clear
Firestore's SDK persistence. Successful account deletion goes further: it
attempts to clear all app DataStores and widget content, terminate the current
Firestore instance, and call `clearPersistence()`, then rebuilds `AppGraph` and
restarts the activity task. Once Authentication deletion succeeds, each local
cleanup is best effort so one failure cannot return the user to an unusable old
graph. The SDK call logically removes its persisted cache; it is not a
secure-overwrite primitive and does not guarantee physical storage blocks are
unrecoverable on a rooted or compromised device. On a shared/high-risk device,
clear Android app storage after sign-out or account deletion.

No password, Firebase token, service-account key, signing key, or tester list is
stored in DataStore.

Android backups are disabled (`allowBackup=false`), which reduces unintended
transfer of cached account/widget data.

## Android permissions

The final merged package has exactly six permission declarations:

```text
android.permission.INTERNET
android.permission.ACCESS_NETWORK_STATE
android.permission.RECEIVE_BOOT_COMPLETED
com.google.android.providers.gsf.permission.READ_GSERVICES
android.permission.WAKE_LOCK
io.github.ffelixq.medswidget.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
```

The first three are app-declared. Google Play services contributes
`READ_GSERVICES`, WorkManager contributes `WAKE_LOCK`, and AndroidX generates
the app-specific signature permission to protect dynamically registered,
non-exported receivers. None is a dangerous runtime permission.

Manifest-merge removal directives strip inherited biometric, fingerprint, and
foreground-service permissions plus WorkManager's unused
`SystemForegroundService`. No notification or exact-alarm permission is
requested. The boot receiver is not direct-boot aware and receives only the
declared boot/date/time/timezone events. Widget receivers are not exported. The
single-widget configuration activity is exported only because the Android
widget host must launch it for `ACTION_APPWIDGET_CONFIGURE`; API-28-and-newer
provider metadata separately declares the single widget reconfigurable.

## Widget action validation

Widget taps are untrusted inputs. `CheckDoseAction` validates:

- the parameter parses to a supported medicine ID, slot, and widget source;
- source is `widget_2x2` or `widget_4x2`;
- the logical day is recomputed first;
- a Firebase session exists;
- snapshot owner equals session owner;
- a 2×2 action's app-widget ID maps to the same owner/medicine;
- the medicine is present in the active local snapshot; and
- the slot remains enabled.

An already checked row is wired to open the app and cannot call undo. The
repository policy and Firestore rules independently reject widget undo.
The graph-wide account operation gate rejects widget repository mutations after
deletion starts. A valid check stores the same random action ID in the atomic
local optimistic transition and Firestore batch. Repository callbacks resolve
only the matching persisted action: success removes its correlation and clears
`Syncing` when no pending work remains; failure rolls back the row, stores a
safe cached error, and refreshes widgets. This callback path remains active
while foreground Firestore listeners are paused.

## Logging and error handling

Production code must not log:

- medicine names or labels;
- email addresses or display names;
- dose state/history;
- Firebase ID/access/refresh tokens;
- passwords;
- service-account material; or
- signing secrets.

Release builds use R8 shrinking. There is no analytics or monitoring SDK. User
errors are intentionally generic where raw Firebase details could leak
implementation/account information. Asynchronous medicine and dose write
rejections are fed back into repository state so the UI can show those safe
errors; dose failures also undo the rejected optimistic in-memory transition.
UID- and operation-scoped failure tracking prevents a late result from a prior
account or unrelated write from clearing or surfacing another operation's
error.
CI sanitizes both successful and failed App Distribution command output: it
removes the signed binary-download line and replaces any remaining URLs before
writing them to the public Actions log.

## Repository secret controls

`.gitignore` excludes:

- `google-services.json`;
- service-account JSON and generated GitHub auth files;
- `*.jks`, `*.keystore`, `*.p12`, `*.pkcs12`, and `keystore.properties`;
- `.env*` except an explicit example;
- `local.properties`;
- private tester lists;
- Firebase/emulator state; and
- build outputs.

Run:

```bash
./scripts/check-forbidden-files.sh
git status --short
git diff --cached --check
git ls-files
```

The tracked-file guard fails if known credential, signing, tester, generated,
emulator, or build files are committed. Gitleaks scans complete Git history in
CI. A clean current tree does not repair a secret already present in history;
rotate it and remove it according to incident-response procedures.

## CI isolation

The workflow defaults to `contents: read`. Only:

- CodeQL receives `security-events: write`; and
- the `main` deployment job receives `id-token: write`.

Pull-request jobs do not reference deployment secrets and never deploy.
Deployment:

- runs only for a push to `main`;
- depends on every validation/security job succeeding;
- uses the protected `production` environment;
- validates required variables/secrets before materializing files;
- confirms `google-services.json` matches the Firebase project ID, Firebase
  Android app ID, Android application ID, and required web OAuth client;
- verifies the release keystore alias before building;
- verifies APK signatures and records SHA-256 hashes;
- obtains WIF/ADC only after the release build and Firebase CLI installation;
- deploys only Firestore rules/indexes;
- refreshes ADC immediately before App Distribution because the GitHub OIDC
  subject credential is short-lived;
- distributes only the signed APK to `owners`; and
- deletes materialized config/keystore files in an `always()` cleanup step.

The Google configuration, release-signing values, and two WIF deploy values
exist only as secrets in the main-restricted GitHub environment `production`;
no repository-level secret duplicates are used. The environment has no
service-account JSON fallback secret. Pull-request jobs cannot read that
environment. Only the non-sensitive Firebase project ID, Firebase Android app
ID, and Android application ID are repository variables.

All third-party actions are pinned to full commit SHAs with their release
version in a comment. Dependabot tracks Gradle, npm, and GitHub Actions.

## Google deployment identity

Workload Identity Federation is active and exchanges a GitHub OIDC identity for
short-lived credentials:
<https://cloud.google.com/iam/docs/workload-identity-federation-with-deployment-pipelines>.

The live provider configuration is:

```text
project number       648847295725
pool                 meds-widget-github
provider             meds-widget-main
provider state       ACTIVE
repository ID        1315914252
repository owner ID  167162073
allowed ref          refs/heads/main
```

The provider condition binds the immutable GitHub repository and owner IDs,
plus the exact `main` ref; repository names alone are not the trust boundary.
The protected `production` environment contains both
`GCP_WORKLOAD_IDENTITY_PROVIDER` and `GCP_DEPLOY_SERVICE_ACCOUNT`, with no
`FIREBASE_DEPLOY_SERVICE_ACCOUNT_JSON`.

The deployment service account has exactly these four project roles:

```text
roles/firebaserules.admin
roles/datastore.indexAdmin
roles/firebaseappdistro.admin
roles/serviceusage.serviceUsageConsumer
```

This live role set is used to:

- create/release Firebase Security Rules rulesets;
- manage the one tracked composite Firestore index and any reviewed future
  index changes;
- upload App Distribution releases to the existing `owners` group;
- call already-enabled project APIs as the service consumer.

The external WIF principal separately receives
`roles/iam.workloadIdentityUser` on that service account so it may impersonate
the account; that is a service-account policy binding, not a fifth project
role. The deployment account has no `roles/firebase.viewer` and no Firestore
document read/write role. Do not grant Owner, Editor, Cloud Functions, Storage,
or billing permissions. Add permissions only in response to an observed denied
operation. Firebase documents rules permissions at
<https://firebase.google.com/docs/projects/iam/permissions>, Firestore index
administration at
<https://cloud.google.com/firestore/docs/security/iam>, and App Distribution
roles at
<https://firebase.google.com/docs/projects/iam/roles-predefined-product>.

The workflow still supports one dedicated service-account JSON as an emergency
fallback if federation later becomes unavailable. It is not provisioned in the
current environment. If activated, it must have the same least privileges, be
stored only as
`FIREBASE_DEPLOY_SERVICE_ACCOUNT_JSON`, and be rotated after suspected exposure
or at the project's maintenance interval. A personal Firebase CLI token is
forbidden.
Identity provisioning is not deployment evidence; the terminal `main` run must
still be recorded separately.

## Release signing

The release keystore is the Android update identity. It is:

- generated locally with strong random store/key passwords;
- ignored by Git;
- backed up in a secure, access-controlled location;
- base64-encoded into a secret on the main-restricted GitHub environment
  `production` without printing it;
- materialized in `$RUNNER_TEMP` with restrictive process defaults; and
- removed at job completion.

CI secrets:

```text
ANDROID_RELEASE_KEYSTORE_BASE64
ANDROID_RELEASE_KEYSTORE_PASSWORD
ANDROID_RELEASE_KEY_ALIAS
ANDROID_RELEASE_KEY_PASSWORD
```

Losing the keystore prevents future APKs from updating existing installations
under the same signing identity. Exposing it requires rotating distribution to
a new application identity/install base; merely changing the password after a
copy escaped does not revoke that copied key.

## Account deletion risk

V1 client deletion enumerates and deletes settings, medicines, dose states, and
dose events before deleting Authentication. `AccountOperationGate` marks
deletion first, waits for an admitted mutation, and then excludes new
medicine/dose/settings/widget mutations until failure or graph replacement. The
operation is owner-authorized but:

- requires a network and recent authentication; each collection query uses
  `Source.SERVER`, so cached emptiness is never treated as a completed cloud
  deletion;
- is not one transaction across all documents/Auth;
- can be interrupted between 400-document batches;
- may be stopped by quota exhaustion; and
- cannot discover a future unlisted subcollection.

Users can retry while their auth account remains. A partial deletion followed
by successful Auth deletion may require project-owner cleanup. This limitation
is accepted to avoid Cloud Functions and paid infrastructure. A failure before
Authentication deletion reopens the mutation gate for retry. After successful
cloud/Auth deletion, the client attempts each app-managed DataStore clear,
signed-out widget refresh, Firestore termination, and persistence clear
independently, then rebuilds `AppGraph` and restarts `MainActivity` even if a
local cleanup attempt failed. Authentication deletion cannot be rolled back.

## Offline and multi-device risk

An in-process mutex and deterministic state ID handle rapid repeat taps on one
device. Firestore batched writes remain atomic and queue offline. They are not a
distributed lock: simultaneous offline devices can submit competing actions
when reconnecting, and Firestore applies last-write-wins to the state document.
Immutable random-ID events may retain more than one attempt.

Meds Widget is a personal tracker, not a safety-critical medication
administration system. Users must not rely on it for medical decisions.

## Quota and service failure

Spark has finite Firestore quota. The application never upgrades or attaches
billing. On temporary/network/quota failure:

- cached rows/widgets remain usable where data already exists;
- pending writes are indicated;
- failed refreshes return friendly errors;
- widgets render cached/signed-out/reconfiguration states; and
- no infinite polling loop is started.

A local optimistic widget check carries a persisted action correlation. A
matching server success clears its pending entry and `Syncing` when none remain;
a matching failure rolls the row back and shows the widget's safe `Cached` state
even when foreground listeners are paused. Medicine and dose write rejections
surface a friendly in-app error. Cloud state is authoritative after
synchronization.

## Known security limitations

- No Firebase App Check attestation is configured in V1.
- Normal sign-out does not invoke Firestore `clearPersistence()`.
- Account deletion invokes `clearPersistence()`, but logical cache deletion is
  not a guarantee of secure physical overwrite.
- Client-side account deletion can be partial.
- Local post-Authentication cleanup is best effort; clearing Android app data is
  the additional owner-controlled purge if a cleanup action failed.
- Multi-device offline actions are not globally serialized.
- Home-screen widget contents are deliberately visible while the device is
  unlocked.
- A rooted/compromised device can bypass normal app sandbox assumptions.
- Email verification is not required by V1.
- The most recent 500 events are rendered; older Firestore audit documents are
  not visible in the V1 history screen.

These limitations must remain visible in release documentation and must not be
described as regulatory or medical-device guarantees.

## Response to suspected secret exposure

- Stop deployment and identify the exact material and exposure window.
- For a service-account key, disable/delete that key and create a replacement
  only if the fallback is still required.
- For WIF, tighten/disable the provider or IAM binding and inspect audit logs.
- For the release keystore, treat the Android update identity as compromised;
  removing a GitHub secret is not sufficient.
- For `google-services.json`, inspect Firebase usage and rules; rotate the API
  key only when justified, then distribute a new config.
- Rotate affected GitHub secrets and remove exposed history using an approved
  repository-history procedure.
- Rerun Gitleaks, forbidden-file checks, rule tests, and a deployment dry review.
- Record the incident without copying the secret into an issue or report.
