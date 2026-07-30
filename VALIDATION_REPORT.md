# Meds Widget V1 Validation Report

Report date: 2026-07-30
Local timezone: Asia/Singapore (UTC+08:00)
Repository: <https://github.com/ffelixq/meds-widget>

This report separates executed evidence from source inventories and pending
remote or physical checks. `PENDING` is not a passing result.

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

Current local V1 branch: `feature/meds-widget-v1`
Bootstrap base commit: `933f6f8a1de1aa5b9ef8ff2826be214d4e9669b5`
Final V1 commit SHA: **PENDING**

The public repository exists, but the V1 feature branch has not yet completed
the required pull-request and main deployment sequence.

| Required check or operation | URL | Commit | Conclusion |
| --- | --- | --- | --- |
| Initial V1 pull request | **PENDING** | **PENDING** | **PENDING** |
| Gradle wrapper validation | **PENDING** | **PENDING** | **PENDING** |
| Gitleaks | **PENDING** | **PENDING** | **PENDING** |
| Dependency review | **PENDING** | **PENDING** | **PENDING** |
| Android validation | **PENDING** | **PENDING** | **PENDING** |
| Firestore rules | **PENDING** | **PENDING** | **PENDING** |
| Android instrumentation | **PENDING** | **PENDING** | **PENDING** |
| CodeQL (Java/Kotlin) | **PENDING** | **PENDING** | **PENDING** |
| Feature-branch merge | **PENDING** | **PENDING** | **PENDING** |
| Main validation/deployment run | **PENDING** | **PENDING** | **PENDING** |
| Deploy Firebase and APK | **PENDING** | **PENDING** | **PENDING** |
| Signed GitHub Actions APK artifact | **PENDING** | **PENDING** | **PENDING** |
| Main branch protection verification | **PENDING** | **PENDING** | **PENDING** |

No CI or deployment success is claimed from workflow-file validation alone.
The terminal workflow conclusions and exact URLs must replace the pending
entries after the PR and main runs complete.

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

The billing state was checked before enabling deployment resources. No billing
role, Cloud Functions, Cloud Storage, Hosting, Realtime Database, SQL Connect,
Analytics, AdMob, or paid API is part of the application deployment.

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
values and no `FIREBASE_DEPLOY_SERVICE_ACCOUNT_JSON` fallback secret.
Provisioning is not evidence of a successful deployment.

### Deployment evidence

| Firebase operation | Result |
| --- | --- |
| Local Firestore rules tests | PASS — 27 cases across 4 suites |
| Production Firestore rules deployment from final main commit | **PENDING** |
| Production Firestore rules read-back/verification | **PENDING** |
| Production Firestore index deployment from final main commit | **PENDING** |
| Production Firestore index read-back/verification | **PENDING** |
| Firebase App Distribution upload | **PENDING** |
| App Distribution release identifier | **PENDING** |
| Tester invitation/install verification | **PENDING** |

## APK

### Final delivery artifact

| Item | Value |
| --- | --- |
| Application ID | `io.github.ffelixq.medswidget` |
| Version name | `1.0.0` |
| Local version code | `1` |
| Main-CI version code | **PENDING** — the workflow uses the successful GitHub run number |
| APK filename | `app-release.apk` |
| Expected local path | `/Users/felixdasumo/Desktop/meds/app/build/outputs/apk/release/app-release.apk` |
| Final APK SHA-256 | `c3bb2cbfe9ba5af1f0fb6278ac0fc20b2101ae63b36711e82ff48bec474c7e16` |
| Final AAB SHA-256 | `ce2e31162402c2964eb225d168a37d56caf3800e07406960702214a1235264a0` |
| Signing certificate SHA-256 | `03a8c044b5b59782ac812d173a041806c9fc0a0bcad02c0a22c94aee6be6eabc` |
| GitHub Actions artifact | **PENDING** |
| Firebase App Distribution release identifier | **PENDING** |

The same ignored release keystore must sign every distributed update. Losing
the release keystore prevents future builds from updating an installed APK
under the same application/signing identity.

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
- The provisional release APK signature and AAB structure were validated, but
  the final source state must be rebuilt and rechecked.

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

### Pending hosted and final-tree checks

| Check | Status |
| --- | --- |
| Gitleaks on complete final Git history | **PENDING — hosted CI** |
| GitHub dependency review | **PENDING — hosted CI** |
| CodeQL Java/Kotlin analysis | **PENDING — hosted CI** |
| Gradle Wrapper validation action | **PENDING — hosted CI** |
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
- Production rules/index deployment, App Distribution, and the signed GitHub
  artifact remain pending until the actual main workflow succeeds.
