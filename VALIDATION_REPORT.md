# Meds Widget V1 and V1.1 Validation Report

Original V1 report date: 2026-07-30; V1.1 addendum: 2026-07-31
Local timezone: Asia/Singapore (UTC+08:00)
Repository: <https://github.com/ffelixq/meds-widget>

This report separates executed evidence from source inventories and physical
checks that still require the owner's Samsung device. `PENDING` is never used
as a passing result.

## V1.1 local validation addendum

Addendum date: 2026-07-31, Asia/Singapore. Milestone:
**Meds Widget V1.1 — Responsive widgets and meal countdowns**.

The feature branch `feature/responsive-widgets-countdowns` started from
physical-widget callback fix
`408df47cf5723fd6c27635c300120a6570ced1fe`. Local evidence before PR:

| Command | Result | Evidence / limitation | Run time |
| --- | --- | --- | --- |
| `MEDS_GRADLE_JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./scripts/validate.sh` | PASS | Formatting, Detekt, Lint, 186 JVM tests, debug APK, forbidden-file/rule guards, and 33 Firestore Emulator cases passed | 2026-07-31 11:02–11:04 SGT |
| `actionlint -shellcheck shellcheck .github/workflows/*.yml`; YAML parse; ShellCheck and shell syntax checks | PASS | Workflow and scripts accepted locally | 2026-07-31 10:47 and 11:04 SGT |
| `./gradlew :app:assembleDebugAndroidTest` | PASS | Instrumentation APK compiled; no local Android device/emulator was connected, so execution is delegated to required hosted CI | 2026-07-31 11:04 SGT |
| `MEDS_VERSION_CODE=11 ./gradlew --no-build-cache clean :app:assembleRelease :app:bundleRelease` | PASS | Fresh R8/resource-shrunk `1.1.0 (11)` local release outputs | 2026-07-31 11:04–11:08 SGT |
| `./scripts/verify-release-widget-callback.sh app/build/outputs/apk/release/app-release-unsigned.apk app/build/outputs/mapping/release/mapping.txt` | PASS | Both `CheckDoseAction` and `StartCountdownAction` retain runtime class names, public zero-argument constructors, and `onAction` in minified DEX | 2026-07-31 11:08 SGT |

Local unsigned APK SHA-256:
`bdc23e1d4e40aad8c6a9e6358e9576a3a3852755acb0a7847d7872ecf17d5b38`.
The production signed APK hash and version code are recorded from the main
workflow artifact, not inferred from this local unsigned build.

Responsive sizing uses Glance `SizeMode.Exact`/`LocalSize` with width-and-height
compact, standard, and spacious categories. Countdown refresh uses one-time
WorkManager work: 10-minute cadence above 60 minutes, 5 minutes from 15–60,
approximately one minute below 15, and one final target refresh. READY timers
schedule no further polling. No exact alarm, foreground service, notification,
or per-minute Firestore counter write was added.

Hosted PR checks, main deployment, signed artifact, Firestore deployment, and
App Distribution evidence are reported in the final delivery summary after the
protected workflow runs. Responsive sizing and countdown behavior on Samsung
remain **REQUIRES_PHYSICAL_SAMSUNG_VALIDATION**.

## Environment

| Item | Verified value |
| --- | --- |
| Operating system | macOS 26.5.1, build 25F80 |
| Architecture | arm64 / Apple Silicon |
| Kernel | Darwin 25.5.0 |
| Shell | zsh |
| Default Java | Oracle Java 26.0.1 |
| Android Gradle Java toolchain | Homebrew OpenJDK 17.0.20 |
| Firebase Emulator Java | Oracle Java 26.0.1; satisfies the Java 21+ emulator requirement |
| Android SDK | `/opt/homebrew/share/android-commandlinetools` |
| Android platform | Android 37.0, revision 2 |
| Android build tools | 36.0.0 |
| Android platform tools | SDK package 37.0.0; selected `adb` 37.0.1 |
| Android Emulator | 36.6.11 |
| Project compile/target/min SDK | 37 / 37 / 26 |
| Gradle Wrapper | 9.6.1 |
| Android Gradle Plugin | 9.3.1 |
| Project Kotlin plugin | 2.4.10 |
| Node.js | 26.0.0 |
| npm | 11.12.1 |
| Firebase CLI | 15.24.0 |
| GitHub CLI | 2.93.0 |
| Git | 2.54.0 |
| actionlint | 1.7.12 |
| ShellCheck | 0.11.0 |
| Gitleaks | 8.30.1 |

The machine-wide default Java is newer than the supported Android build
toolchain. `scripts/validate.sh` deliberately selects JDK 17 for Gradle and a
Java 21-or-newer runtime for Firebase Emulator Suite commands.

## Local checks

### Practical validation

The practical validation suite completed successfully on 2026-07-30 from
12:00 to 12:04 SGT:

```bash
RUN_INSTRUMENTATION=1 ./scripts/validate.sh
```

Result: **PASS — `Practical local validation passed.`**

The script executed the following required checks:

| Command | Result | Test count / evidence | Warnings | Run time |
| --- | --- | --- | --- | --- |
| `./scripts/check-forbidden-files.sh` | PASS | No forbidden tracked path reported by the guard | Repeated again after the suite at 12:04 SGT; it must also be run against the staged tree | 2026-07-30 12:00 and 12:04 SGT |
| `./scripts/check-firestore-rules.sh` | PASS | No accidentally permissive rule pattern found | This is a static guard, not the emulator rules test | 2026-07-30 12:00 and 12:04 SGT |
| `env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin:$PATH ./gradlew --no-daemon --no-parallel --max-workers=1 --stacktrace formatCheck detekt lint testDebugUnitTest assembleDebug` | PASS | 150 JVM tests across 20 suites; 0 failures, 0 errors, 0 skipped. Debug APK assembled. Android Lint reported `No issues found.` | None affecting the result | 2026-07-30 12:00–12:01 SGT |
| `npm ci --prefix firebase-tests` | PASS | Locked Firebase rules-test dependencies installed; npm reported 0 vulnerabilities | Registry results can change over time | 2026-07-30 12:01 SGT |
| `npm test --prefix firebase-tests` | PASS | 27 Firestore rules cases across 4 suites; 0 failures, 0 skipped | Expected permission-denied emulator diagnostics represent negative tests | 2026-07-30 12:01–12:02 SGT |
| `env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin:$PATH ./gradlew --no-daemon --no-parallel --max-workers=1 --stacktrace connectedDebugAndroidTest` | PASS | 39 instrumentation tests across 8 test classes; 0 failures, 0 errors, 0 skipped | Executed on an Android API 36 ARM64 AVD, not a Samsung device | 2026-07-30 12:02–12:04 SGT |

The executed JVM count includes domain, repository-isolation, scheduler,
ViewModel, utility, Glance/widget rendering, widget action, configuration, and
update-coordinator tests. The instrumentation count covers application launch,
fake-authenticated navigation, medicine creation/editing, check and undo,
settings restoration, widget configuration lifecycle, and process recreation.

### Additional local quality and dependency checks

| Command | Result | Evidence | Warnings | Run time |
| --- | --- | --- | --- | --- |
| `actionlint -shellcheck shellcheck .github/workflows/*.yml`; `shellcheck scripts/*.sh`; `bash -n scripts/*.sh`; `zsh -n scripts/*.sh` | PASS | Workflow expressions and embedded shell passed actionlint; repository scripts passed ShellCheck and Bash/zsh syntax parsing | This does not substitute for a hosted Actions run | 2026-07-30 12:04 SGT |
| `npm audit --prefix firebase-tests --audit-level=moderate` | PASS | `found 0 vulnerabilities` | Registry results can change over time | 2026-07-30 12:04 SGT |

### Post-deployment evidence-update validation

The application source was unchanged after the successful production run. The
final report/documentation update was nevertheless checked again on 2026-07-30
from 13:10 to 13:11 SGT:

| Command | Result | Evidence | Warnings |
| --- | --- | --- | --- |
| `./scripts/validate.sh` | PASS | Forbidden-file and permissive-rules guards passed; formatting, Detekt, Lint, the 150-test JVM task, and debug assembly were successful/up to date; all 27 rules cases executed and passed | Instrumentation was intentionally not repeated for documentation-only changes; the unchanged app already passed 39/39 in the full suite and hosted PR/main CI |
| `actionlint -shellcheck shellcheck .github/workflows/*.yml` | PASS | No workflow or embedded-shell findings | Hosted Actions remains authoritative for runner behavior |
| `shellcheck scripts/*.sh`; `bash -n scripts/*.sh`; `zsh -n scripts/*.sh` | PASS | No script findings | None |
| `npm audit --prefix firebase-tests --audit-level=moderate` | PASS | `found 0 vulnerabilities` | Registry results can change over time |
| `./scripts/check-forbidden-files.sh`; `./scripts/check-firestore-rules.sh`; `git diff --check` | PASS | Both guards passed; no whitespace errors | Repeated again after staging |
| `gitleaks git --redact --no-banner --log-opts='--all' .` | PASS | Five commits scanned; no leaks | Hosted Gitleaks also passed |
| `git diff --no-ext-diff \| gitleaks stdin --redact --no-banner` | PASS | Uncommitted evidence update scanned; no leaks | Repeated against the staged tree before commit |

### Final signed-build checks

The signed APK and AAB were rebuilt after the final application-source change,
with every release task rerun. Signing values came from the ignored local
keystore and macOS Keychain and were not printed.

| Command | Result | Evidence | Warnings | Run time |
| --- | --- | --- | --- | --- |
| `./gradlew --no-daemon --no-parallel --max-workers=1 --rerun-tasks assembleRelease bundleRelease` with ignored `MEDS_KEYSTORE_*` inputs | PASS | 62/62 release tasks executed; signed, R8-minified APK and signed AAB produced | Two dependency native libraries could not be stripped and were safely packaged as-is | Final exact-input rebuild: 2026-07-30 12:11–12:14 SGT |
| `shasum -a 256 app/build/outputs/apk/release/app-release.apk app/build/outputs/bundle/release/app-release.aab` | PASS | Final hashes recorded below | Any later application or release-input rebuild changes these hashes | 2026-07-30 12:14 SGT |
| `/opt/homebrew/share/android-commandlinetools/build-tools/36.0.0/apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk` | PASS | One RSA-4096 signer; APK Signature Scheme v2 and v3 verified; certificate SHA-256 recorded below | Java 26 printed an informational native-access warning for Conscrypt; v1, v3.1, and v4 signatures are not used | 2026-07-30 12:14 SGT |
| `/opt/homebrew/share/android-commandlinetools/build-tools/36.0.0/aapt dump badging app/build/outputs/apk/release/app-release.apk` | PASS | Application ID, version 1/1.0.0, min 26, target 37, and six packaged permissions verified | Includes Android's app-specific dynamic-receiver signature permission | 2026-07-30 12:14 SGT |
| `jarsigner -verify -certs app/build/outputs/bundle/release/app-release.aab` | PASS — exit 0 / `jar verified` | The AAB JAR signature was accepted | Expected self-signed certificate, no timestamp, POSIX attribute, and `JarFile`/`JarInputStream` informational warnings; details below | 2026-07-30 12:14 SGT |
| `java -jar bundletool-all-1.18.3.jar validate --bundle=app/build/outputs/bundle/release/app-release.aab` | PASS | Current stable Bundletool 1.18.3 accepted the final AAB | Java 26 printed an informational deprecated-Unsafe warning from protobuf | 2026-07-30 12:14 SGT |

The AAB signer is an intentionally self-signed private release identity, so a
public PKIX chain and timestamp are not expected. `jarsigner` also reports that
entries visible through `JarFile` are not signed when interpreted through
`JarInputStream`; `jarsigner` nevertheless returned `jar verified`, and
Bundletool independently accepted the bundle structure. These warnings are
informational for this locally signed Android App Bundle.

## GitHub checks

Initial V1 branch: `feature/meds-widget-v1`
Bootstrap base commit: `933f6f8a1de1aa5b9ef8ff2826be214d4e9669b5`
Final feature head: `f44ba72f5c57311d4c4ba50549acdddfb2df75ef`
Squash-merged V1 commit: `956a1f26c58adfeb19c46e1306536ba9fa68f46b`

The public repository is <https://github.com/ffelixq/meds-widget>. Pull request
[#1](https://github.com/ffelixq/meds-widget/pull/1) was squash-merged at
2026-07-30 12:36 SGT, and the remote feature branch was then deleted.

| Required check or operation | URL | Commit | Conclusion |
| --- | --- | --- | --- |
| Initial V1 pull request | [PR #1](https://github.com/ffelixq/meds-widget/pull/1) | `f44ba72f5c57311d4c4ba50549acdddfb2df75ef` | MERGED |
| Gradle wrapper validation | [PR run 30513941287](https://github.com/ffelixq/meds-widget/actions/runs/30513941287) | `f44ba72f5c57311d4c4ba50549acdddfb2df75ef` | SUCCESS |
| Gitleaks | [PR run 30513941287](https://github.com/ffelixq/meds-widget/actions/runs/30513941287) | `f44ba72f5c57311d4c4ba50549acdddfb2df75ef` | SUCCESS |
| Dependency review | [PR run 30513941287](https://github.com/ffelixq/meds-widget/actions/runs/30513941287) | `f44ba72f5c57311d4c4ba50549acdddfb2df75ef` | SUCCESS |
| Android validation | [PR run 30513941287](https://github.com/ffelixq/meds-widget/actions/runs/30513941287) | `f44ba72f5c57311d4c4ba50549acdddfb2df75ef` | SUCCESS — 150 JVM tests |
| Firestore rules | [PR run 30513941287](https://github.com/ffelixq/meds-widget/actions/runs/30513941287) | `f44ba72f5c57311d4c4ba50549acdddfb2df75ef` | SUCCESS — 27 cases |
| Android instrumentation | [PR run 30513941287](https://github.com/ffelixq/meds-widget/actions/runs/30513941287) | `f44ba72f5c57311d4c4ba50549acdddfb2df75ef` | SUCCESS — 39 tests |
| CodeQL (Java/Kotlin) | [PR run 30513941287](https://github.com/ffelixq/meds-widget/actions/runs/30513941287) | `f44ba72f5c57311d4c4ba50549acdddfb2df75ef` | SUCCESS |
| Feature-branch merge | [PR #1](https://github.com/ffelixq/meds-widget/pull/1) | `956a1f26c58adfeb19c46e1306536ba9fa68f46b` | SQUASH-MERGED |
| Main validation/deployment run | [Run 30514348334, attempt 2](https://github.com/ffelixq/meds-widget/actions/runs/30514348334) | `956a1f26c58adfeb19c46e1306536ba9fa68f46b` | SUCCESS |
| Deploy Firebase and APK | [Job 90783610897](https://github.com/ffelixq/meds-widget/actions/runs/30514348334/job/90783610897) | `956a1f26c58adfeb19c46e1306536ba9fa68f46b` | SUCCESS |
| Signed GitHub Actions APK artifact | [Main run artifacts](https://github.com/ffelixq/meds-widget/actions/runs/30514348334#artifacts) | `956a1f26c58adfeb19c46e1306536ba9fa68f46b` | SUCCESS — artifact `8748680821` |
| Main branch protection verification | [Repository rules](https://github.com/ffelixq/meds-widget/settings/rules) | ruleset `20019671` | ACTIVE and verified |

Attempt 1 of main run `30514348334` failed only in the deployment job because
`iam.googleapis.com`, `iamcredentials.googleapis.com`, and
`sts.googleapis.com` were disabled. The failure was inspected rather than
blindly retried. Those three identity APIs required by the official WIF
deployment-pipeline guide were enabled while Cloud Billing remained disabled.
Attempt 2 then passed the previously failing WIF exchange, rules/index
deployment, App Distribution upload, and cleanup steps. The JSON credential
fallback was skipped in both authentication phases.

Ruleset `20019671` has no bypass actors and applies to `refs/heads/main`. It
requires a squash pull request, the seven exact checks above, an up-to-date
branch, resolved conversations, and linear history; deletion and non-fast-
forward updates are blocked. Zero additional human approvals are required.

## Firebase

### Resource identity and no-cost status

| Item | Verified value |
| --- | --- |
| Firebase project ID | `meds-widget-ffelixq` |
| Firebase project number | `648847295725` |
| Android Firebase app ID | `1:648847295725:android:15e7b95037f6ff897678e4` |
| Android application ID | `io.github.ffelixq.medswidget` |
| Plan | Spark |
| Billing | No billing account attached; billing disabled |
| Firestore database | Cloud Firestore Standard / Native mode |
| Firestore location | `asia-southeast1` |
| Firestore delete protection | Enabled |
| Firestore point-in-time recovery | Disabled |
| Authentication providers | Google; Email/password |
| App Distribution group | `owners` |
| WIF identity APIs | IAM, Service Account Credentials, and Security Token Service enabled |

The billing state was checked before and after enabling the three identity
APIs and returned `False` both times. Google documents all IAM API use as free
of charge. No billing role, Cloud Functions, Cloud Storage, Hosting, Realtime
Database, SQL Connect, Analytics, AdMob, or paid API is part of the application
deployment.

### CI identity

Workload Identity Federation is configured without a long-lived JSON secret in
the GitHub `production` environment:

```text
projects/648847295725/locations/global/workloadIdentityPools/meds-widget-github/providers/meds-widget-main
```

Verified provider constraints:

- provider state is active;
- GitHub repository ID is `1315914252`;
- GitHub repository-owner ID is `167162073`;
- allowed ref is exactly `refs/heads/main`;
- the service-account policy grants the external principal
  `roles/iam.workloadIdentityUser`; and
- the deployment service account has only
  `roles/firebaserules.admin`, `roles/datastore.indexAdmin`,
  `roles/firebaseappdistro.admin`, and
  `roles/serviceusage.serviceUsageConsumer` at project scope.

The deployment account has no Firebase Viewer or Firestore document-data
read/write role. The production environment contains both WIF configuration
values and no `FIREBASE_DEPLOY_SERVICE_ACCOUNT_JSON` fallback secret. WIF
authentication was exercised successfully twice in the production deployment.
The temporary bootstrap service-account key was then deleted; a live follow-up
query reported zero user-managed keys for the deployment account.

### Deployment evidence

| Firebase operation | Result |
| --- | --- |
| Local Firestore rules tests | PASS — 27 cases across 4 suites |
| Production Firestore rules deployment from final V1 main commit | PASS — run `30514348334`, attempt 2 |
| Production Firestore rules read-back/verification | PASS — ruleset `0a9020e8-3b25-4501-9944-da62fe2979ed`; local and remote SHA-256 both `1ca3ac9dd5b324c12accb519d2e6002ca9d4b360775fa9c8aa5fee6bddce798a` |
| Production Firestore index deployment from final V1 main commit | PASS — one obsolete index removed |
| Production Firestore index read-back/verification | PASS — only tracked `medicines` (`archived ASC`, `createdAt ASC`) index remains; state `READY` |
| Firebase App Distribution upload | PASS — version `1.0.0 (4)` distributed to `owners` |
| App Distribution release identifier | `projects/648847295725/apps/1:648847295725:android:15e7b95037f6ff897678e4/releases/29poloscta3co` |
| App Distribution group verification | PASS — `owners`, tester count `1`; no private tester address recorded here |
| Tester invitation/install verification | **PENDING — requires the tester's physical Samsung device** |

## APK

### Final delivery artifact

| Item | Value |
| --- | --- |
| Application ID | `io.github.ffelixq.medswidget` |
| Version name | `1.0.0` |
| Local version code | `1` |
| Main-CI version code | `4` |
| APK filename | `app-release.apk` |
| Downloaded final CI APK path | `/Users/felixdasumo/Desktop/meds/build/ci-release-956a1f2/apk/release/app-release.apk` |
| Final CI APK SHA-256 | `7040ac2438bffe4ee1640afc3e39a268f71115c42fb7d9ef45e07102b6fe6496` |
| Final CI AAB SHA-256 | `37554ab6a5f38ed6603984669613dfe3f4ed76c112cb2a8277354202f63d0f28` |
| Signing certificate SHA-256 | `03a8c044b5b59782ac812d173a041806c9fc0a0bcad02c0a22c94aee6be6eabc` |
| GitHub Actions artifact | ID `8748680821`; `meds-widget-release-956a1f26c58adfeb19c46e1306536ba9fa68f46b-2`; 11,363,183 bytes; expires 2026-08-29 |
| Artifact archive digest | `sha256:d1bafd2d31d4a73d16cd3aa892750855ea128d1fd31c77cded26076a542017a0` |
| Firebase App Distribution release identifier | `29poloscta3co` |

The artifact was downloaded again from GitHub and its embedded checksum
manifest matched both files. `apksigner` verified the APK and reported the
expected RSA-4096 certificate. The same ignored release keystore must sign
every distributed update. Losing it prevents future builds from updating an
installed APK under the same application/signing identity.

## Manual validation

### Completed emulator and manual checks

- Android API 36 AVD instrumentation run: 39 tests across 8 classes passed.
- Application start and fake-authenticated navigation were exercised.
- Medicine creation/editing, checking, confirmation-based undo, history,
  settings restoration, widget configuration lifecycle, and process
  recreation were exercised by deterministic instrumentation tests.
- The two Glance widgets and the Compose previews have JVM rendering/action
  coverage, including multiple widget IDs, scrolling data, signed-out,
  missing/deleted medicine, long-name, custom-label, checked-state, and
  widget-cannot-undo behavior.
- Firebase Security Rules passed 27 emulator cases without production user
  data.
- A live synthetic Email/Password Authentication smoke test passed; no test
  credential is retained in the repository.
- Google authentication provider, Android OAuth configuration, and debug and
  release certificate fingerprints are configured. A real-device Google
  sign-in interaction remains pending.
- The final CI release APK was downloaded, checksum-compared to its artifact
  manifest, signature-verified, and inspected as application
  `io.github.ffelixq.medswidget`, version `1.0.0 (4)`.

### REQUIRES_PHYSICAL_SAMSUNG_VALIDATION

No physical Samsung phone was available in this session. Therefore none of the
steps in `docs/SAMSUNG_VALIDATION.md` are claimed as passed, including:

- One UI widget picker discovery and labels;
- two independently configured 2×2 widget instances;
- 4×2 vertical scrolling with all active doses reachable;
- widget check propagation and widget non-undo behavior on One UI;
- widget resizing and Samsung widget-stack behavior;
- reset-boundary and reboot recovery on the phone;
- offline check and reconnection synchronization;
- real Google sign-in;
- Firebase App Distribution invitation, download, install, and update;
- light/dark home-screen themes, long labels, and large fonts.

Status: **REQUIRES_PHYSICAL_SAMSUNG_VALIDATION**

## Security

### Completed checks

- Firestore rules tests: 27 cases across 4 suites passed.
- Anonymous access, cross-user access, wrong `ownerUid`, invalid schemas, event
  mutation, and owner-only account-data deletion paths are covered by the
  local rules suite.
- The permissive-rules static guard passed.
- npm audit reported zero vulnerabilities at the moderate threshold.
- Workflow and shell static checks passed.
- Workflow actions are pinned to full commit SHAs.
- Pull-request jobs do not receive production secrets or `id-token: write`.
- Deployment is gated on all required main validation jobs and uses
  main-restricted, short-lived WIF credentials.
- Real Google configuration, signing material, local SDK paths, tester data,
  and generated credentials are ignored and forbidden from tracking.
- The final staged tree passed the forbidden-file guard, `git diff --check`,
  a credential-pattern review, and Gitleaks with no findings.

### Hosted and final-tree checks

| Check | Status |
| --- | --- |
| Gitleaks on complete V1 Git history | PASS — PR and main CI |
| GitHub dependency review | PASS — PR and main CI |
| CodeQL Java/Kotlin analysis | PASS — PR and main CI |
| Gradle Wrapper validation action | PASS — PR and main CI |
| Final staged/tracked-file review | PASS — repeated immediately before commit |
| Final post-source-change signed rebuild and hashes | PASS — hashes recorded in the APK section |

### Known risks and limitations

- Client-side account deletion is not one atomic operation across Firestore and
  Firebase Authentication; interruption or quota exhaustion can leave a
  partial deletion that the user must retry.
- Firestore SDK persistence clearing is best-effort at the storage layer; flash
  media does not provide a secure-overwrite guarantee.
- Simultaneous offline actions on multiple devices are not a distributed lock;
  state resolves through Firestore synchronization while immutable events
  retain audit attempts.
- Firebase App Check is not configured in V1.
- Widget medicine content is intentionally visible on the unlocked home screen.
- V1 history renders the most recent 500 audit events.
- Spark quotas can be exhausted. The app does not attach billing or
  automatically upgrade.
- Physical Samsung/One UI behavior and real Google sign-in remain unverified.
- App Distribution invitation acceptance, installation, and Samsung One UI
  behavior remain unverified until the physical-device checklist is performed.
