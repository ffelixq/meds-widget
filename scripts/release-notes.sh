#!/usr/bin/env bash

set -Eeuo pipefail

if [[ "$#" -ne 1 ]]; then
  echo "usage: $0 OUTPUT_FILE" >&2
  exit 2
fi

script_directory="$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"
repository_root="$(CDPATH='' cd -- "${script_directory}/.." && pwd)"
output_file="$1"

cd "${repository_root}"

commit_sha="${GITHUB_SHA:-$(git rev-parse HEAD)}"
short_sha="$(printf '%s' "${commit_sha}" | cut -c1-12)"
commit_subject="$(git show -s --format=%s "${commit_sha}")"
build_date="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
test_summary="${TEST_SUMMARY:-Required CI validation completed successfully.}"

printf '%s\n' \
  "Meds Widget ${short_sha}" \
  "" \
  "Commit: ${short_sha} — ${commit_subject}" \
  "Build date (UTC): ${build_date}" \
  "Tests: ${test_summary}" \
  "" \
  "Install this APK after accepting the Firebase App Distribution invitation." \
  "Future builds signed with the same key can update this installation." \
  > "${output_file}"

echo "Release notes written to ${output_file}."
