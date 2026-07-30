#!/usr/bin/env bash

set -Eeuo pipefail

script_directory="$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"
repository_root="$(CDPATH='' cd -- "${script_directory}/.." && pwd)"
rules_file="${repository_root}/firestore.rules"
firebase_file="${repository_root}/firebase.json"
indexes_file="${repository_root}/firestore.indexes.json"

if [[ ! -s "${rules_file}" ]]; then
  echo "error: firestore.rules is missing or empty" >&2
  exit 1
fi
if [[ ! -s "${firebase_file}" || ! -s "${indexes_file}" ]]; then
  echo "error: firebase.json and firestore.indexes.json must both exist" >&2
  exit 1
fi
if ! command -v node > /dev/null 2>&1; then
  echo "error: Node.js is required to validate Firebase JSON files" >&2
  exit 1
fi

node - "${firebase_file}" "${indexes_file}" <<'NODE'
const fs = require("node:fs");

const [firebasePath, indexesPath] = process.argv.slice(2);
const readJson = (path) => JSON.parse(fs.readFileSync(path, "utf8"));
const firebaseConfig = readJson(firebasePath);
const indexesConfig = readJson(indexesPath);
const forbiddenServices = [
  "apphosting",
  "database",
  "dataconnect",
  "functions",
  "hosting",
  "storage",
];

if (!firebaseConfig.firestore) {
  throw new Error("firebase.json must configure Firestore");
}
for (const service of forbiddenServices) {
  if (Object.hasOwn(firebaseConfig, service)) {
    throw new Error(`firebase.json must not configure forbidden service: ${service}`);
  }
}
if (!Array.isArray(indexesConfig.indexes) || !Array.isArray(indexesConfig.fieldOverrides)) {
  throw new Error("firestore.indexes.json must contain indexes and fieldOverrides arrays");
}
NODE

require_rule_text() {
  local expected_text="$1"
  local explanation="$2"
  if ! grep -Fq "${expected_text}" "${rules_file}"; then
    echo "error: Firestore rules ${explanation}" >&2
    exit 1
  fi
}

require_rule_text "rules_version = '2';" "must use rules_version 2"
require_rule_text "match /users/{userId}" "must scope private data under users/{userId}"
require_rule_text "request.auth.uid == userId" "must bind authenticated users to their own UID path"
require_rule_text "data.ownerUid == userId" "must validate ownerUid"
require_rule_text "match /doseEvents/{eventId}" "must define the immutable dose-event collection"
require_rule_text "allow update: if false;" "must prevent immutable audit-event updates"
require_rule_text "match /{document=**}" "must include a default-deny catch-all"
require_rule_text "allow read, write: if false;" "must explicitly deny unmatched access"

compact_rules="$(tr '\r\n\t' '   ' < "${rules_file}")"
if printf '%s' "${compact_rules}" |
  grep -Eq \
    'allow[[:space:]]+[^:;]+:[[:space:]]*if[[:space:]]*(true|isSignedIn\(\)|request\.auth[[:space:]]*!=[[:space:]]*null)[[:space:]]*;'
then
  echo "error: Firestore rules contain an unconditional or authentication-only allow grant" >&2
  exit 1
fi

echo "Firestore permissive-rule guard passed."
