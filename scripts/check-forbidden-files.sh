#!/usr/bin/env bash

set -Eeuo pipefail

script_directory="$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"
repository_root="$(CDPATH='' cd -- "${script_directory}/.." && pwd)"
cd "${repository_root}"

if ! git rev-parse --is-inside-work-tree > /dev/null 2>&1; then
  echo "error: forbidden-file check must run inside a Git work tree" >&2
  exit 1
fi

findings_file="$(mktemp "${TMPDIR:-/tmp}/meds-widget-forbidden.XXXXXX")"
trap 'rm -f "${findings_file}"' EXIT

git ls-files -z |
  while IFS= read -r -d '' tracked_path; do
    lower_path="$(printf '%s' "${tracked_path}" | tr '[:upper:]' '[:lower:]')"

    case "${lower_path}" in
      .env.example | */.env.example)
        continue
        ;;
      google-services.json | */google-services.json | \
        *service-account*.json | *service_account*.json | \
        gha-creds-*.json | */gha-creds-*.json | \
        *.jks | *.keystore | *.p12 | *.pkcs12 | \
        keystore.properties | */keystore.properties | \
        local.properties | */local.properties | \
        .env | */.env | .env.* | */.env.* | \
        secrets/* | */secrets/* | \
        local-secrets/* | */local-secrets/* | \
        release-signing/* | */release-signing/* | \
        emulator-data/* | */emulator-data/* | \
        .firebase/* | */.firebase/* | \
        .gradle/* | */.gradle/* | \
        .kotlin/* | */.kotlin/* | \
        node_modules/* | */node_modules/* | \
        build/* | */build/* | \
        firebase-debug*.log | */firebase-debug*.log | \
        firestore-debug*.log | */firestore-debug*.log | \
        ui-debug*.log | */ui-debug*.log | \
        testers.txt | */testers.txt | testers.csv | */testers.csv | testers.json | */testers.json | \
        tester-emails.txt | */tester-emails.txt | tester-emails.csv | */tester-emails.csv | \
        tester-emails.json | */tester-emails.json | \
        firebase-testers.txt | */firebase-testers.txt | \
        firebase-testers.csv | */firebase-testers.csv | \
        firebase-testers.json | */firebase-testers.json | \
        private-testers.txt | */private-testers.txt | \
        private-testers.csv | */private-testers.csv | \
        private-testers.json | */private-testers.json)
        printf '%s\n' "${tracked_path}" >> "${findings_file}"
        ;;
    esac
  done

if [[ -s "${findings_file}" ]]; then
  echo "error: forbidden generated, credential, signing, tester, or build files are tracked:" >&2
  sed 's/^/  - /' "${findings_file}" >&2
  exit 1
fi

echo "Forbidden tracked-file check passed."
