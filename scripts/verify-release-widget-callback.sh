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

application_id="$("${apkanalyzer_command}" manifest application-id "${release_apk}")"
if [[ "${application_id}" != "io.github.ffelixq.medswidget" ]]; then
  echo "error: unexpected release application ID: ${application_id}" >&2
  exit 1
fi

release_debuggable="$("${apkanalyzer_command}" manifest debuggable "${release_apk}")"
if [[ "${release_debuggable}" != "false" ]]; then
  echo "error: release APK must not be debuggable" >&2
  exit 1
fi

release_permissions="$("${apkanalyzer_command}" manifest permissions "${release_apk}")"
for forbidden_permission in \
  android.permission.SYSTEM_ALERT_WINDOW \
  android.permission.REQUEST_INSTALL_PACKAGES \
  android.permission.MANAGE_EXTERNAL_STORAGE \
  android.permission.READ_EXTERNAL_STORAGE \
  android.permission.WRITE_EXTERNAL_STORAGE \
  android.permission.READ_MEDIA_IMAGES \
  android.permission.READ_MEDIA_VIDEO \
  android.permission.READ_MEDIA_AUDIO \
  android.permission.CAMERA \
  android.permission.RECORD_AUDIO \
  android.permission.ACCESS_FINE_LOCATION \
  android.permission.ACCESS_COARSE_LOCATION \
  android.permission.ACCESS_BACKGROUND_LOCATION \
  android.permission.READ_CONTACTS \
  android.permission.WRITE_CONTACTS \
  android.permission.GET_ACCOUNTS \
  android.permission.READ_SMS \
  android.permission.RECEIVE_SMS \
  android.permission.SEND_SMS \
  android.permission.READ_PHONE_STATE \
  android.permission.READ_CALL_LOG \
  android.permission.WRITE_CALL_LOG \
  android.permission.BODY_SENSORS \
  android.permission.BODY_SENSORS_BACKGROUND \
  android.permission.ACTIVITY_RECOGNITION \
  android.permission.BLUETOOTH_SCAN \
  android.permission.BLUETOOTH_CONNECT \
  android.permission.NEARBY_WIFI_DEVICES \
  android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS \
  android.permission.SCHEDULE_EXACT_ALARM \
  android.permission.USE_EXACT_ALARM \
  android.permission.USE_BIOMETRIC \
  android.permission.USE_FINGERPRINT \
  android.permission.FOREGROUND_SERVICE
do
  if grep -Fq -- "${forbidden_permission}" <<< "${release_permissions}"; then
    echo "error: unexpected high-risk permission in release APK: ${forbidden_permission}" >&2
    exit 1
  fi
done

echo "Release APK manifest attack-surface checks passed."

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
