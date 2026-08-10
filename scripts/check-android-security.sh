#!/usr/bin/env bash
set -Eeuo pipefail

manifest="app/src/main/AndroidManifest.xml"
network_config="app/src/main/res/xml/network_security_config.xml"
build_file="app/build.gradle.kts"

fail() {
  printf 'security guard: %s\n' "$1" >&2
  exit 1
}

require_text() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  grep -Fq -- "$pattern" "$file" || fail "$message"
}

forbid_text() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if grep -Fq -- "$pattern" "$file"; then
    fail "$message"
  fi
}

require_text "$manifest" 'android:allowBackup="false"' 'Android backups must remain disabled.'
require_text "$manifest" 'android:fullBackupContent="false"' 'Legacy full backup must remain disabled.'
require_text "$manifest" 'android:usesCleartextTraffic="false"' 'Cleartext traffic must be explicitly disabled.'
require_text "$manifest" 'android:networkSecurityConfig="@xml/network_security_config"' 'Network Security Config must be attached.'
require_text "$manifest" 'android.permission.HIDE_OVERLAY_WINDOWS' 'Overlay protection permission must remain declared.'
require_text "$network_config" 'cleartextTrafficPermitted="false"' 'Network Security Config must deny cleartext traffic.'
require_text "$build_file" 'implementation(libs.firebase.appcheck.playintegrity)' 'Release builds must include Firebase App Check Play Integrity support.'

for permission in \
  'android.permission.MANAGE_EXTERNAL_STORAGE' \
  'android.permission.READ_EXTERNAL_STORAGE' \
  'android.permission.WRITE_EXTERNAL_STORAGE' \
  'android.permission.QUERY_ALL_PACKAGES' \
  'android.permission.SYSTEM_ALERT_WINDOW' \
  'android.permission.REQUEST_INSTALL_PACKAGES' \
  'android.permission.READ_SMS' \
  'android.permission.RECEIVE_SMS' \
  'android.permission.READ_CONTACTS' \
  'android.permission.RECORD_AUDIO' \
  'android.permission.CAMERA'; do
  forbid_text "$manifest" "$permission" "Dangerous/unnecessary permission detected: $permission"
done

forbid_text "$manifest" 'android:debuggable="true"' 'Main manifest must never force a debuggable application.'
forbid_text "$network_config" 'cleartextTrafficPermitted="true"' 'Cleartext exception detected in production network config.'

python3 - "$manifest" <<'PY'
import sys
import xml.etree.ElementTree as ET

manifest_path = sys.argv[1]
android = "{http://schemas.android.com/apk/res/android}"
root = ET.parse(manifest_path).getroot()
application = root.find("application")
if application is None:
    raise SystemExit("security guard: application element missing")

allowed_exported = {
    ".ui.MainActivity",
    ".widget.SingleWidgetConfigurationActivity",
}
for element in application:
    if element.tag not in {"activity", "activity-alias", "service", "receiver", "provider"}:
        continue
    exported = element.get(android + "exported")
    name = element.get(android + "name", "<unnamed>")
    if exported == "true" and name not in allowed_exported:
        raise SystemExit(f"security guard: unexpected exported component: {name}")

providers = application.findall("provider")
for provider in providers:
    name = provider.get(android + "name", "")
    if name == "androidx.core.content.FileProvider":
        if provider.get(android + "exported") != "false":
            raise SystemExit("security guard: FileProvider must remain non-exported")
        if provider.get(android + "grantUriPermissions") != "true":
            raise SystemExit("security guard: FileProvider must use temporary URI grants")
        break
else:
    raise SystemExit("security guard: expected FileProvider is missing")
PY

printf 'Android security guard passed.\n'
