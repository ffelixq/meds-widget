# CI/CD

## Source of truth and evidence

The workflow source is `.github/workflows/ci.yml`. Its existence is not proof
that a check, deployment, rules release, or APK distribution succeeded. Record
actual run URLs, job conclusions, commit SHA, Firebase output, and artifact
identity in `VALIDATION_REPORT.md`.

The repository is public so standard GitHub-hosted runners are no-cost:
<https://docs.github.com/en/actions/concepts/billing-and-usage>. The workflow
uses only `ubuntu-latest`, not paid larger runners. Artifact retention is
bounded to reduce storage.

## Triggers and concurrency

Workflow name: `CI`

It runs for:

- every pull request targeting `main`;
- every push to `main`; and
- manual `workflow_dispatch`.

Concurrency key:

```text
meds-widget-${workflow}-${event_name}-${ref}
```

The literal workflow expression is
`meds-widget-${{ github.workflow }}-${{ github.event_name }}-${{ github.ref }}`.
Including the event name keeps a pull-request run distinct from a push run for
the same ref. `cancel-in-progress: true` prevents an older run in that exact
group from finishing after a newer commit. A manual run validates but does not
deploy: deployment additionally requires `event_name == push` and
`ref == refs/heads/main`.

The workflow pins:

```text
Java             17
Node             24
Firebase CLI     15.24.0
Android CLI      20.0 (build 14742923)
Android platform 37.0
Build Tools      36.0.0
```

Third-party actions are referenced by full commit SHA. The comment beside each
SHA records the reviewed release tag/version. Dependabot maintains these
references.

## Jobs and required check names

These exact PR check names are the branch-protection contract:

```text
Gradle wrapper validation
Gitleaks
Dependency review
Android validation
Firestore rules
Android instrumentation
CodeQL (Java/Kotlin)
```

Do not rename one without updating the branch ruleset and this document.

| Job | Main work | Timeout |
| --- | --- | ---: |
| `Gradle wrapper validation` | verifies the committed wrapper, rejects snapshots | 10 min |
| `Gitleaks` | scans complete Git history, no PR comment/artifact | 10 min |
| `Dependency review` | compares the PR or main-push dependency change range; fails for moderate-or-higher vulnerable additions | 10 min |
| `Android validation` | forbidden-file guard, format, Detekt, lint, JVM domain/ViewModel/widget logic and Glance render tests, debug APK | 45 min |
| `Firestore rules` | permissive-rule guard, locked npm install, emulator tests | 20 min |
| `Android instrumentation` | API 35 Google APIs x86_64 emulator, connected tests | 45 min |
| `CodeQL (Java/Kotlin)` | manual Kotlin/Java build and analysis | 45 min |
| `Deploy Firebase and APK` | main-only signed build/artifact, then rules/indexes and distribution | 45 min |

CodeQL uses the stable `codeql-bundle-v2.26.2` release asset explicitly because
that is the first stable bundle containing Kotlin 2.4.10 extractor support.
The CodeQL action itself remains pinned to its reviewed full commit SHA.

`Dependency review` runs for pull requests and main pushes. A push compares
`github.event.before` with `github.sha`, so the deployment gate requires an
observed successful dependency review rather than accepting a skipped job. It
is skipped only for non-deploying manual `workflow_dispatch` runs, which do not
have a canonical change range.

## Pull-request behaviour

A pull request:

- receives read-only repository contents by default;
- cannot request an OIDC token;
- never reads the `production` environment;
- never materializes `google-services.json` or a release key;
- never deploys Firebase resources;
- never uploads to App Distribution; and
- never executes a `pull_request_target` workflow.

This remains true for forks. Untrusted pull-request code is built only in
secretless validation jobs.

### PR artifacts

`Android validation` always attempts to upload:

```text
app/build/outputs/apk/debug/*.apk
app/build/reports/
build/reports/
```

Artifact name:

```text
android-validation-<run_id>-<run_attempt>
```

Retention: 14 days.

Firestore rule output is:

```text
firestore-rules-<run_id>-<run_attempt>
```

Its content is `firebase-tests/rules-test-output.txt`.
Retention: 14 days.

Instrumentation reports are:

```text
android-instrumentation-<run_id>-<run_attempt>
```

Its uploaded paths are:

```text
app/build/reports/androidTests/
app/build/outputs/androidTest-results/
app/build/outputs/connected_android_test_additional_output/
```

Retention: 14 days.

## Main deployment gate

`Deploy Firebase and APK` runs only when:

- the event is a push;
- the ref is `refs/heads/main`;
- wrapper validation passed;
- Gitleaks passed;
- dependency review passed for the pushed main change range;
- Android validation passed;
- Firestore rule tests passed;
- instrumentation passed; and
- CodeQL passed.

It uses GitHub environment `production` and only:

```yaml
permissions:
  contents: read
  id-token: write
```

The job performs this sequence:

- validates every required variable/secret and selects exactly one Google auth
  mode;
- decodes `app/google-services.json` and the release keystore with restrictive
  file defaults;
- checks that the configured Android application ID is exactly
  `io.github.ffelixq.medswidget`, and that the Firebase config has the matching
  project ID, Firebase Android App ID, package, and web OAuth client required
  by Credential Manager Google sign-in;
- validates the keystore password/alias;
- sets `MEDS_VERSION_CODE` to the monotonically increasing GitHub
  `run_number`, then builds the signed release APK and AAB;
- verifies the APK signature with the newest installed `apksigner` and records
  the AAB certificate with `keytool`;
- writes SHA-256 hashes for APK and AAB;
- generates release notes with short SHA, subject, UTC build time, test
  summary, and installation/update note;
- uploads the complete signed-release artifact;
- installs the pinned Firebase CLI;
- only then obtains short-lived Google credentials through WIF, or uses the
  explicit service-account JSON fallback, and verifies the resulting
  Application Default Credentials (including fallback project identity);
- deploys only Firestore rules and indexes;
- refreshes the selected ADC authentication immediately before App
  Distribution, including a second WIF exchange when WIF is active;
- distributes the signed APK to App Distribution group alias `owners` while
  removing the signed binary-download line and redacting remaining URLs from
  public command output;
- removes decoded config/keystore files even after failure.

Artifact upload deliberately occurs before Firestore deployment and App
Distribution. Thus, a later deployment failure may coexist with an uploaded
artifact; it does not make the deployment successful.

Authentication is intentionally delayed until after the release build and
Firebase CLI installation, then refreshed between Firestore deployment and App
Distribution. GitHub's OIDC subject credential is short-lived; this ordering
avoids spending its useful lifetime on Gradle/R8 and prevents the second
Firebase operation from inheriting a nearly expired credential.

The deploy command is scoped by tracked `firebase.json`:

```bash
firebase deploy \
  --project "${FIREBASE_PROJECT_ID}" \
  --only firestore \
  --non-interactive \
  --force \
  --message "CI ${GITHUB_SHA}"
```

`--only firestore` deploys this repository's rules and indexes. It does not
deploy Authentication, Functions, Storage, Hosting, or any other service.
Because `--force` can remove indexes absent from the tracked file, index changes
must be reviewed in the pull request.

### Release artifact

Name:

```text
meds-widget-release-<full-commit-sha>-<run-attempt>
```

Contents:

```text
app-release.apk
app-release.aab
release-sha256.txt
release-signing-certificates.txt
release-aab-signing-certificate.txt
release-notes.txt
```

Retention: 30 days.

The AAB is retained only as build preparation. It is not published or
distributed through Google Play. App Distribution uses the APK.

Local builds default to version code `1`; main deployments replace it with the
workflow run number so a later distributed APK can update an earlier one.

## Repository variables

Set non-secret identifiers as GitHub Actions variables:

```text
FIREBASE_PROJECT_ID       = meds-widget-ffelixq
FIREBASE_ANDROID_APP_ID   = 1:648847295725:android:15e7b95037f6ff897678e4
ANDROID_APPLICATION_ID    = io.github.ffelixq.medswidget
```

After authenticating `gh` for `ffelixq/meds-widget`:

```bash
gh auth status
gh variable set FIREBASE_PROJECT_ID \
  --repo ffelixq/meds-widget \
  --body "meds-widget-ffelixq"
gh variable set FIREBASE_ANDROID_APP_ID \
  --repo ffelixq/meds-widget \
  --body "1:648847295725:android:15e7b95037f6ff897678e4"
gh variable set ANDROID_APPLICATION_ID \
  --repo ffelixq/meds-widget \
  --body "io.github.ffelixq.medswidget"
gh variable list --repo ffelixq/meds-widget
```

Do not guess the App ID. Obtain it from:

```bash
firebase apps:list ANDROID --project "meds-widget-ffelixq"
```

## Production secrets

All sensitive values below exist only as secrets on the GitHub environment
`production`. That environment is restricted to deployments from `main`.
There are no repository-level copies of the Google configuration, release
signing material, or deploy credentials. The three non-sensitive project/app
identifiers remain repository variables as described above.

Required build/distribution secrets:

```text
GOOGLE_SERVICES_JSON_BASE64
ANDROID_RELEASE_KEYSTORE_BASE64
ANDROID_RELEASE_KEYSTORE_PASSWORD
ANDROID_RELEASE_KEY_ALIAS
ANDROID_RELEASE_KEY_PASSWORD
```

Active WIF secrets:

```text
GCP_WORKLOAD_IDENTITY_PROVIDER
GCP_DEPLOY_SERVICE_ACCOUNT
```

Fallback only:

```text
FIREBASE_DEPLOY_SERVICE_ACCOUNT_JSON
```

The current `production` environment has both WIF secrets and does not have the
fallback secret. The fallback name remains in this contract only for emergency
recovery if federation later becomes unavailable.

Create/confirm the environment:

```bash
gh api \
  --method PUT \
  repos/ffelixq/meds-widget/environments/production
```

In GitHub, open:

```text
https://github.com/ffelixq/meds-widget/settings/environments
```

Select **production**, set **Deployment branches and tags** to **Selected
branches and tags**, and add `main`. A second-person approval is not required
for this single-developer repository.

Store secrets in the `production` environment:

```bash
gh secret set <SECRET_NAME> \
  --repo ffelixq/meds-widget \
  --env production
```

The CLI reads the value from standard input. Enter/paste it directly into the
CLI prompt; do not put passwords or JSON on the command line.

### Encode config and keystore

macOS:

```bash
base64 -i app/google-services.json |
  gh secret set GOOGLE_SERVICES_JSON_BASE64 \
    --repo ffelixq/meds-widget \
    --env production

base64 -i release-signing/meds-widget-release.p12 |
  gh secret set ANDROID_RELEASE_KEYSTORE_BASE64 \
    --repo ffelixq/meds-widget \
    --env production
```

Linux:

```bash
base64 -w 0 app/google-services.json |
  gh secret set GOOGLE_SERVICES_JSON_BASE64 \
    --repo ffelixq/meds-widget \
    --env production

base64 -w 0 release-signing/meds-widget-release.p12 |
  gh secret set ANDROID_RELEASE_KEYSTORE_BASE64 \
    --repo ffelixq/meds-widget \
    --env production
```

These commands stream encoded bytes to GitHub without creating an intermediate
base64 file or printing them. Keep the ignored keystore in a secure backup.

Verify names, never values:

```bash
gh secret list \
  --repo ffelixq/meds-widget \
  --env production
```

## Google authentication for deployment

Firebase CLI detects Application Default Credentials, so the workflow does not
use a personal `FIREBASE_TOKEN`:
<https://firebase.google.com/docs/cli#use_the_cli_with_ci_systems>.

### Active: Workload Identity Federation

WIF exchanges GitHub's OIDC assertion for a short-lived service-account
credential. Follow Google's official deployment-pipeline guide:
<https://cloud.google.com/iam/docs/workload-identity-federation-with-deployment-pipelines>.

The live provider is:

```text
project number       648847295725
pool                 meds-widget-github
provider             meds-widget-main
state                ACTIVE
repository ID        1315914252
repository owner ID  167162073
allowed ref          refs/heads/main
```

It trusts issuer `https://token.actions.githubusercontent.com`, maps the
repository/ref claims, and conditions access on both immutable GitHub IDs and
the exact main-branch ref. Renaming or transferring a repository therefore does
not silently extend trust.

The two secret values are:

```text
GCP_WORKLOAD_IDENTITY_PROVIDER =
  projects/648847295725/locations/global/workloadIdentityPools/meds-widget-github/providers/meds-widget-main

GCP_DEPLOY_SERVICE_ACCOUNT =
  <DEPLOY_ACCOUNT>@meds-widget-ffelixq.iam.gserviceaccount.com
```

The external principal requires
`roles/iam.workloadIdentityUser` on that service account. The service account
must remain limited to the four project roles below. They cover Firestore
rules/index deployment, App Distribution upload, and calling already-enabled
APIs. Do not give Owner, Editor, billing, Cloud Functions, Storage, or Firestore
document-data read/write access.

The verified live deployment service account has this role set:

```text
roles/firebaserules.admin
roles/datastore.indexAdmin
roles/firebaseappdistro.admin
roles/serviceusage.serviceUsageConsumer
```

The first three roles release rules, manage indexes, and upload App
Distribution releases. `roles/serviceusage.serviceUsageConsumer` lets that
identity call already-enabled project APIs. The WIF impersonation binding is
attached to the service account and is not a fifth project role. The account
has no `roles/firebase.viewer` and no Firestore document read/write role. Do not
replace this set with broad Firebase Admin, Editor, or Owner.

Add permissions only in response to an observed denied operation. Do not add
roles for products not present in `firebase.json`.

Official permission references:

- <https://firebase.google.com/docs/projects/iam/permissions>
- <https://cloud.google.com/firestore/docs/security/iam>
- <https://firebase.google.com/docs/projects/iam/roles-predefined-product>

### Fallback: dedicated service-account JSON

Use only as an emergency path if the active WIF configuration becomes
unavailable. The current `production` environment has both WIF secrets and no
dedicated JSON secret. A terminal `main` workflow run—not identity
provisioning—remains the authority for deployment success.

- Create a dedicated deployment service account with the same least
  privileges.
- Create one JSON key locally.
- Stream the JSON into
  `FIREBASE_DEPLOY_SERVICE_ACCOUNT_JSON` in the `production` environment.
- Remove the local plaintext key securely after confirming the secret exists.
- Never use a personal login credential.
- Record the key ID/creation date in a secure owner inventory, not in Git.

WIF secrets must both be absent when using the fallback. The workflow rejects:

- only one of the two WIF values;
- WIF plus fallback in a mixed state; and
- no complete auth method.

Rotate the fallback by creating a new key, updating the GitHub secret, observing
one successful deployment, then disabling/deleting the old key. Never print
either JSON.

## Release-key setup

Generate the key locally; never in CI. Use a strong randomly generated store
password and key password, and a non-sensitive alias. Keep one ignored copy at:

```text
release-signing/meds-widget-release.p12
```

Register the key's SHA-1 and SHA-256 with the Firebase Android app before
downloading the final `google-services.json`. See
[Firebase setup](FIREBASE_SETUP.md#add-debug-and-release-sha-fingerprints).

The app build reads CI values as:

| GitHub secret | Gradle environment variable |
| --- | --- |
| `ANDROID_RELEASE_KEYSTORE_BASE64` | decoded to `MEDS_KEYSTORE_PATH` |
| `ANDROID_RELEASE_KEYSTORE_PASSWORD` | `MEDS_KEYSTORE_PASSWORD` |
| `ANDROID_RELEASE_KEY_ALIAS` | `MEDS_KEY_ALIAS` |
| `ANDROID_RELEASE_KEY_PASSWORD` | `MEDS_KEY_PASSWORD` |

Losing the release keystore prevents later APKs from updating installed copies
under the same signing identity.

## Local parity

Practical suite:

```bash
FIREBASE_JAVA_HOME=/absolute/path/to/jdk-21-or-newer \
./scripts/validate.sh
```

The Android jobs and Gradle build use JDK 17. The local script auto-detects
standard and Homebrew JDK 17 installations; `MEDS_GRADLE_JAVA_HOME` overrides
that selection. Firebase CLI 15.24 requires Java 21 or newer for emulator
execution, so the rules job and the isolated rules-test subprocess use JDK 21.

It runs:

```text
check-forbidden-files.sh
check-firestore-rules.sh
formatCheck
detekt
lint
testDebugUnitTest
assembleDebug
npm ci --prefix firebase-tests
npm test --prefix firebase-tests
```

Add a running emulator/instrumentation:

```bash
FIREBASE_JAVA_HOME=/absolute/path/to/jdk-21-or-newer \
RUN_INSTRUMENTATION=1 ./scripts/validate.sh
```

Focused commands:

```bash
./gradlew formatApply
./gradlew formatCheck
./gradlew detekt
./gradlew lint
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
npm test --prefix firebase-tests
```

The local suite does not run GitHub's hosted dependency review, Gitleaks action,
wrapper-validation action, or CodeQL service. Those conclusions must come from
the PR run.

## Dependabot

`.github/dependabot.yml` checks weekly in Asia/Singapore:

| Ecosystem | Directory | Monday time | Group |
| --- | --- | --- | --- |
| Gradle | `/` | 05:00 | all minor/patch |
| npm | `/firebase-tests` | 05:15 | all minor/patch |
| GitHub Actions | `/` | 05:30 | all minor/patch |

Each ecosystem is limited to five open update PRs. Major updates are not grouped
with routine minor/patch maintenance. Every Dependabot PR still passes the same
protected checks; updates are never auto-deployed before merge.
The public repository's dependency graph, vulnerability alerts, and Dependabot
security updates are enabled. This keeps the hosted dependency-review action
available without a paid GitHub plan.

## App Distribution tester onboarding

The deployed APK goes to group alias `owners`.

For the initial tester:

- open the Firebase invitation email on the Samsung phone;
- select **Get started** or **Accept invitation**;
- sign in with the exact invited Google email;
- install/open Firebase App Tester if Firebase requests it;
- select the Meds Widget release and **Download**;
- when Android prompts, open **Settings**, permit installation from that
  tester/browser source, return, and select **Install**; and
- optionally revoke the source's install permission afterward.

Later releases signed with the same key appear as updates and can install over
the existing app. If Android reports an incompatible signature, stop: do not
uninstall until comparing the APK certificate in the CI artifact and
`VALIDATION_REPORT.md`.

Official App Distribution instructions:
<https://firebase.google.com/docs/app-distribution/android/distribute-cli>.

### GitHub artifact fallback

Open:

```text
https://github.com/ffelixq/meds-widget/actions/workflows/ci.yml
```

Choose the successful `main` run for the desired SHA, scroll to **Artifacts**,
download `meds-widget-release-<full-sha>-<run-attempt>`, verify the APK SHA-256
against `release-sha256.txt`, and verify signing before installing. Artifacts
expire after 30 days; App Distribution releases have their own retention
policy.

## Branch ruleset

Create protection only after the initial V1 PR has passed, been squash-merged,
and the resulting `main` deployment has succeeded. This avoids a bootstrap
deadlock before the required check contexts exist.

The `main` ruleset must:

- require a pull request;
- require zero additional human approvals for the single developer;
- require all seven exact check names listed above;
- require the branch to be up to date before merge;
- require review conversations to be resolved;
- block force pushes;
- block deletion;
- require linear history; and
- have no routine bypass actor.

In the repository:

```text
https://github.com/ffelixq/meds-widget/settings/rules
```

Select **New ruleset → New branch ruleset**:

- name: `Protect main`
- enforcement status: **Active**
- target branch: default branch / `main`
- enable **Restrict deletions**
- enable **Block force pushes**
- enable **Require linear history**
- enable **Require a pull request before merging**
- required approvals: `0`
- enable **Require conversation resolution before merging**
- enable **Require status checks to pass**
- enable **Require branches to be up to date before merging**
- add the seven exact checks.

Squash merge remains compatible with linear history.

Verify through CLI:

```bash
gh api repos/ffelixq/meds-widget/rulesets \
  --jq '.[] | {id, name, target, enforcement}'

gh api repos/ffelixq/meds-widget/rules/branches/main
```

Then attempt no destructive bypass. Query the rule details and confirm the
required checks/PR/deletion/non-fast-forward/linear-history rules are present.
GitHub's rules reference is
<https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets>.

## Initial V1 delivery sequence

- Complete and locally validate `feature/meds-widget-v1`.
- Run forbidden-file and secret review before staging.
- Push the branch and open a PR to `main`.
- Watch the `CI` run; inspect logs and fix the root cause on the feature branch.
- Repeat until every required PR check succeeds.
- Squash-merge the PR and delete the remote feature branch.
- Watch the new `main` run to terminal completion.
- Confirm the signed artifact, Firestore rules/index deployment, and App
  Distribution upload from actual output.
- Create/verify the `main` ruleset with the exact checks.
- Pull final `main` locally.
- Record all URLs, SHAs, conclusions, IDs, hashes, and unresolved manual work in
  `VALIDATION_REPORT.md`.

Never describe a cancelled, skipped-required, neutral, timed-out, or failed job
as passing.

## Troubleshooting

### Deployment configuration fails

Compare `gh variable list` and `gh secret list --env production` with the exact
names above. The current expected state is both WIF secrets set together and the
fallback absent. Use the JSON mode only as the documented emergency path; the
workflow rejects one WIF value, mixed WIF/fallback credentials, or no complete
authentication method.

### Config identity validation fails

The encoded `google-services.json` belongs to another project or does not
contain `io.github.ffelixq.medswidget`. Regenerate it from the intended Firebase
Android app after Google/SHA setup and replace the secret.

### Keystore validation/build signing fails

Confirm the encoded file, store password, alias, and key password all come from
the same ignored keystore. Do not generate a replacement key merely to turn CI
green; doing so breaks update identity.

### Firebase deploy is denied

Read the complete denied permission. Add only the exact missing rules/index
permission to the deployment principal. Do not grant Editor/Owner or a Firebase
Admin role reflexively.

### App Distribution is denied or group missing

Confirm `FIREBASE_ANDROID_APP_ID`, package, App Distribution initialization, and
the existing `owners` group. Grant only release/group-list permissions required
by the CLI. Do not commit a tester list.

### Instrumentation emulator fails to boot

Inspect the emulator-runner log before rerunning. Distinguish host-image
provisioning from a deterministic test failure. Reproduce with a local API 35
Google APIs x86_64 emulator when possible; never mark a flaky failure passed.

### Required check cannot be selected

The check context must have run recently in this repository. Complete the first
PR run before creating protection, then use the exact display name—not the YAML
job ID.
