#!/usr/bin/env bash

set -Eeuo pipefail

script_directory="$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"
repository_root="$(CDPATH='' cd -- "${script_directory}/.." && pwd)"
cd "${repository_root}"

for required_command in git java node npm firebase; do
  if ! command -v "${required_command}" > /dev/null 2>&1; then
    echo "error: required command is unavailable: ${required_command}" >&2
    exit 1
  fi
done

java_major_for() {
  "$1" -XshowSettings:properties -version 2>&1 |
    awk -F'= ' '
      /^[[:space:]]*java.specification.version = / {
        split($2, components, ".")
        if (components[1] == "1") {
          print components[2]
        } else {
          print components[1]
        }
        exit
      }
    '
}

java_home_for() {
  "$1" -XshowSettings:properties -version 2>&1 |
    awk -F'= ' '
      /^[[:space:]]*java.home = / {
        print $2
        exit
      }
    '
}

gradle_java_home="${MEDS_GRADLE_JAVA_HOME:-}"
if [[ -z "${gradle_java_home}" ]] &&
  [[ "$(java_major_for "$(command -v java)")" == "17" ]]
then
  gradle_java_home="$(java_home_for "$(command -v java)")"
fi
if [[ -z "${gradle_java_home}" ]] &&
  [[ "$(uname -s)" == "Darwin" ]] &&
  [[ -x "/usr/libexec/java_home" ]]
then
  detected_java_home="$(/usr/libexec/java_home -v 17 2> /dev/null || true)"
  if [[ -x "${detected_java_home}/bin/java" ]] &&
    [[ "$(java_major_for "${detected_java_home}/bin/java")" == "17" ]]
  then
    gradle_java_home="${detected_java_home}"
  fi
fi
if [[ -z "${gradle_java_home}" ]] && command -v brew > /dev/null 2>&1; then
  detected_java_home="$(brew --prefix openjdk@17 2> /dev/null || true)"
  detected_java_home="${detected_java_home}/libexec/openjdk.jdk/Contents/Home"
  if [[ -x "${detected_java_home}/bin/java" ]] &&
    [[ "$(java_major_for "${detected_java_home}/bin/java")" == "17" ]]
  then
    gradle_java_home="${detected_java_home}"
  fi
fi
if [[ -z "${gradle_java_home}" ]] ||
  [[ ! -x "${gradle_java_home}/bin/java" ]] ||
  [[ "$(java_major_for "${gradle_java_home}/bin/java")" != "17" ]]
then
  echo "error: Android Gradle validation requires JDK 17." >&2
  echo "Set MEDS_GRADLE_JAVA_HOME to a JDK 17 installation." >&2
  exit 1
fi
gradle_java_environment=(
  env
  "JAVA_HOME=${gradle_java_home}"
  "PATH=${gradle_java_home}/bin:${PATH}"
)

firebase_java_command="$(command -v java)"
firebase_java_environment=(env)
if [[ -n "${FIREBASE_JAVA_HOME:-}" ]]; then
  firebase_java_command="${FIREBASE_JAVA_HOME}/bin/java"
  if [[ ! -x "${firebase_java_command}" ]]; then
    echo "error: FIREBASE_JAVA_HOME does not contain an executable bin/java." >&2
    exit 1
  fi
  firebase_java_environment=(
    env
    "JAVA_HOME=${FIREBASE_JAVA_HOME}"
    "PATH=${FIREBASE_JAVA_HOME}/bin:${PATH}"
  )
fi

firebase_java_major="$(java_major_for "${firebase_java_command}")"
if [[ ! "${firebase_java_major}" =~ ^[0-9]+$ ]] ||
  ((firebase_java_major < 21))
then
  echo "error: Firebase Emulator Suite requires Java 21 or newer." >&2
  echo "Set FIREBASE_JAVA_HOME to a JDK 21+ installation while keeping Android Gradle on Java 17." >&2
  exit 1
fi

./scripts/check-forbidden-files.sh
./scripts/check-firestore-rules.sh

gradle_max_workers="${MEDS_GRADLE_MAX_WORKERS:-1}"
if [[ ! "${gradle_max_workers}" =~ ^[1-9][0-9]*$ ]]; then
  echo "error: MEDS_GRADLE_MAX_WORKERS must be a positive integer." >&2
  exit 1
fi
gradle_arguments=(
  --no-daemon
  --no-parallel
  "--max-workers=${gradle_max_workers}"
  --stacktrace
)

"${gradle_java_environment[@]}" ./gradlew "${gradle_arguments[@]}" \
  formatCheck \
  detekt \
  lint \
  testDebugUnitTest \
  assembleDebug

npm ci --prefix firebase-tests
"${firebase_java_environment[@]}" npm test --prefix firebase-tests

if [[ "${RUN_INSTRUMENTATION:-0}" == "1" ]]; then
  "${gradle_java_environment[@]}" ./gradlew "${gradle_arguments[@]}" connectedDebugAndroidTest
else
  echo "Instrumentation tests skipped; run with RUN_INSTRUMENTATION=1 when an emulator is available."
fi

echo "Practical local validation passed."
