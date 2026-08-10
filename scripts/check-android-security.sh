#!/usr/bin/env bash

set -Eeuo pipefail

manifest="app/src/main/AndroidManifest.xml"
network_config="app/src/main/res/xml/network_security_config.xml"
backup_rules="app/src/main/res/xml/data_extraction_rules.xml"
export_paths="app/src/main/res/xml/export_file_paths.xml"
reminder_source="app/src/main/java/io/github/ffelixq/medswidget/sync/MedicineReminderScheduler.kt"

fail() {
  echo "error: Android security invariant failed: $1" >&2
  exit 1
}

require_literal() {
  local file="$1"
  local literal="$2"
  local description="$3"
  grep -Fq -- "$literal" "$file" || fail "$description"
}

reject_literal() {
  local file="$1"
  local literal="$2"
  local description="$3"
  if grep -Fq -- "$literal" "$file"; then
    fail "$description"
  fi
}

require_literal "$manifest" 'android:allowBackup="false"' 'application backups must stay disabled'
require_literal "$manifest" 'android:fullBackupContent="false"' 'legacy full backup must stay disabled'
require_literal "$manifest" 'android:dataExtractionRules="@xml/data_extraction_rules"' 'data extraction rules must stay attached'
require_literal "$manifest" 'android:usesCleartextTraffic="false"' 'cleartext traffic must stay disabled'
require_literal "$manifest" 'android:networkSecurityConfig="@xml/network_security_config"' 'explicit network security config must stay attached'
reject_literal "$manifest" 'android:debuggable="true"' 'production manifest must not enable debuggable'
reject_literal "$manifest" 'android:testOnly="true"' 'production manifest must not enable testOnly'

exported_true_count="$(grep -Fc 'android:exported="true"' "$manifest")"
[[ "$exported_true_count" == "2" ]] || fail "only launcher MainActivity and widget configuration may be exported"

main_activity_block="$(sed -n '/android:name=".ui.MainActivity"/,/<\/activity>/p' "$manifest")"
[[ "$main_activity_block" == *'android:exported="true"'* ]] || fail "MainActivity must remain the explicit exported launcher"
config_activity_block="$(sed -n '/android:name=".widget.SingleWidgetConfigurationActivity"/,/<\/activity>/p' "$manifest")"
[[ "$config_activity_block" == *'android:exported="true"'* ]] || fail "widget configuration must remain explicitly exported for the host"
receiver_blocks="$(sed -n '/<receiver/,/<\/receiver>/p' "$manifest")"
[[ "$receiver_blocks" != *'android:exported="true"'* ]] || fail "app receivers must not be exported"
provider_block="$(sed -n '/android:name="androidx.core.content.FileProvider"/,/<\/provider>/p' "$manifest")"
[[ "$provider_block" == *'android:exported="false"'* ]] || fail "FileProvider must remain non-exported"

require_literal "$network_config" 'cleartextTrafficPermitted="false"' 'network security config must deny cleartext'
reject_literal "$network_config" 'cleartextTrafficPermitted="true"' 'network security config must never allow cleartext'

for domain in root file database sharedpref; do
  require_literal "$backup_rules" "<exclude domain=\"$domain\" path=\".\" />" "backup rules must exclude the $domain domain"
done

require_literal "$export_paths" '<cache-path' 'exports must stay inside app cache'
require_literal "$export_paths" 'path="exports/"' 'FileProvider must remain scoped to exports/'
for forbidden_path in '<root-path' '<external-path' '<external-files-path' '<external-cache-path' '<files-path'; do
  reject_literal "$export_paths" "$forbidden_path" "FileProvider must not expose broader storage paths"
done

require_literal "$reminder_source" '.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)' 'detailed medicine reminders must stay private on the lock screen'
require_literal "$reminder_source" '.setPublicVersion(publicNotification)' 'medicine reminders must retain a generic public version'
require_literal "$reminder_source" '.setLocalOnly(true)' 'medicine reminders must not auto-bridge to companion devices'

echo "Android security invariants passed."
