#!/usr/bin/env bash

set -Eeuo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "usage: $0 <release-apk> [mapping-file]" >&2
  exit 2
fi

release_apk="$1"
mapping_file="${2:-}"
callback_classes=(
  "io.github.ffelixq.medswidget.widget.CheckDoseAction"
  "io.github.ffelixq.medswidget.widget.StartCountdownAction"
)

if [[ ! -s "${release_apk}" ]]; then
  echo "error: release APK is missing or empty: ${release_apk}" >&2
  exit 1
fi

apkanalyzer_command="$(command -v apkanalyzer || true)"
if [[ -z "${apkanalyzer_command}" ]]; then
  echo "error: apkanalyzer is unavailable" >&2
  exit 1
fi

if [[ -n "${mapping_file}" && ! -s "${mapping_file}" ]]; then
  echo "error: R8 mapping is missing or empty: ${mapping_file}" >&2
  exit 1
fi

for callback_class in "${callback_classes[@]}"; do
  callback_descriptor="L$(tr '.' '/' <<< "${callback_class}");"
  callback_code="$("${apkanalyzer_command}" dex code --class "${callback_class}" "${release_apk}")"
  grep -Fq ".class public final ${callback_descriptor}" <<< "${callback_code}"
  grep -Fq ".method public constructor <init>()V" <<< "${callback_code}"
  grep -Fq ".method public onAction(" <<< "${callback_code}"

  if grep -Fq "MedsWidgetAction" <<< "${callback_code}"; then
    echo "error: debug widget-action logging is present in ${callback_class}" >&2
    exit 1
  fi

  if [[ -n "${mapping_file}" ]]; then
    mapping_block="$(
      awk -v header="${callback_class} -> ${callback_class}:" '
      $0 == header {
        found = 1
        print
        next
      }
      found && $0 !~ /^[[:space:]#]/ {
        exit
      }
      found {
        print
      }
      END {
        if (!found) {
          exit 3
        }
      }
      ' "${mapping_file}"
    )"
    grep -Fq "void <init>()" <<< "${mapping_block}"
    grep -Fq "onAction(" <<< "${mapping_block}"
  fi
done

echo "Minified widget ActionCallback runtime-instantiation checks passed."
