#!/usr/bin/env bash

set -Eeuo pipefail

manifest="app/src/main/AndroidManifest.xml"
network_config="app/src/main/res/xml/network_security_config.xml"
backup_rules="app/src/main/res/xml/data_extraction_rules.xml"
export_paths="app/src/main/res/xml/export_file_paths.xml"
reminder_source="app/src/main/java/io/github/ffelixq/medswidget/sync/MedicineReminderScheduler.kt"
main_activity="app/src/main/java/io/github/ffelixq/medswidget/ui/MainActivity.kt"
widget_config_activity="app/src/main/java/io/github/ffelixq/medswidget/widget/SingleWidgetConfigurationActivity.kt"
automatic_widget_config_activity="app/src/main/java/io/github/ffelixq/medswidget/widget/AutomaticWidgetConfigurationActivity.kt"
auth_screen="app/src/main/java/io/github/ffelixq/medswidget/ui/AuthScreen.kt"
settings_screen="app/src/main/java/io/github/ffelixq/medswidget/ui/SettingsScreen.kt"
sensitive_window_source="app/src/main/java/io/github/ffelixq/medswidget/security/SensitiveWindowProtection.kt"
sensitive_export_source="app/src/main/java/io/github/ffelixq/medswidget/util/SensitiveExportCleanup.kt"
widget_snapshot_source="app/src/main/java/io/github/ffelixq/medswidget/widget/WidgetSnapshot.kt"
sensitive_cipher_source="app/src/main/java/io/github/ffelixq/medswidget/security/SensitiveDataCipher.kt"

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
require_literal "$manifest" 'android.permission.HIDE_OVERLAY_WINDOWS' 'overlay protection permission must stay declared'
reject_literal "$manifest" 'android:debuggable="true"' 'production manifest must not enable debuggable'
reject_literal "$manifest" 'android:testOnly="true"' 'production manifest must not enable testOnly'
reject_literal "$manifest" 'android.permission.SYSTEM_ALERT_WINDOW' 'the app must not request overlay creation privileges'
reject_literal "$manifest" 'android.permission.REQUEST_INSTALL_PACKAGES' 'the app must not request package-install privileges'
reject_literal "$manifest" 'android.permission.MANAGE_EXTERNAL_STORAGE' 'the app must not request all-files access'

exported_true_count="$(grep -Fc 'android:exported="true"' "$manifest")"
[[ "$exported_true_count" == "3" ]] || fail "only launcher MainActivity and the two widget configuration activities may be exported"

main_activity_block="$(sed -n '/android:name=".ui.MainActivity"/,/<\/activity>/p' "$manifest")"
[[ "$main_activity_block" == *'android:exported="true"'* ]] || fail "MainActivity must remain the explicit exported launcher"
config_activity_block="$(sed -n '/android:name=".widget.SingleWidgetConfigurationActivity"/,/<\/activity>/p' "$manifest")"
[[ "$config_activity_block" == *'android:exported="true"'* ]] || fail "single widget configuration must remain explicitly exported for the host"
automatic_config_activity_block="$(sed -n '/android:name=".widget.AutomaticWidgetConfigurationActivity"/,/<\/activity>/p' "$manifest")"
[[ "$automatic_config_activity_block" == *'android:exported="true"'* ]] || fail "automatic widget repair must remain explicitly exported for the host"
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
reject_literal "$reminder_source" 'KEY_MEDICINE_NAME' 'WorkManager input must not persist medicine names'
reject_literal "$reminder_source" 'KEY_LABEL' 'WorkManager input must not persist custom medicine labels'

for activity_source in "$main_activity" "$widget_config_activity" "$automatic_widget_config_activity"; do
  require_literal "$activity_source" 'SensitiveWindowProtection.apply(this)' 'every health activity must apply shared sensitive-window protection'
done
require_literal "$sensitive_window_source" 'WindowManager.LayoutParams.FLAG_SECURE' 'health activities must block screenshots, recording, and insecure displays'
require_literal "$sensitive_window_source" 'setRecentsScreenshotEnabled(false)' 'health activities must suppress recents thumbnails on supported Android versions'
require_literal "$sensitive_window_source" 'window.setHideOverlayWindows(true)' 'health activities must block third-party overlays on supported Android versions'
require_literal "$sensitive_window_source" 'filterTouchesWhenObscured = true' 'health activities must reject obscured touches'

require_literal "$main_activity" 'SensitiveExportCleanup.scheduleDeletion' 'shared health-data exports must be scheduled for deletion'
require_literal "$sensitive_export_source" 'UUID.randomUUID()' 'health-data exports must use unpredictable filenames'
require_literal "$sensitive_export_source" 'canonicalFile.parentFile == directory.canonicalFile' 'export cleanup must reject paths outside its private directory'
require_literal "$sensitive_export_source" 'setInitialDelay(CLEANUP_DELAY_MINUTES, TimeUnit.MINUTES)' 'shared health-data exports must expire after a short delay'

require_literal "$widget_snapshot_source" 'SensitiveDataCipher("widget_snapshot")' 'app-managed widget health cache must use the sensitive-data cipher'
require_literal "$widget_snapshot_source" 'WIDGET_SNAPSHOT_CIPHER.encrypt(WidgetSnapshotCodec.encode(snapshot))' 'widget health cache writes must be encrypted at rest'
require_literal "$sensitive_cipher_source" 'AndroidKeyStore' 'local health-cache keys must stay in Android Keystore'
require_literal "$sensitive_cipher_source" 'AES/GCM/NoPadding' 'local health-cache encryption must remain authenticated AES-GCM'
require_literal "$sensitive_cipher_source" 'KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT' 'health-cache key must be scoped to encryption/decryption'
require_literal "$sensitive_cipher_source" 'setRandomizedEncryptionRequired(true)' 'health-cache encryption must require randomized encryption'

for credential_screen in "$auth_screen" "$settings_screen"; do
  reject_literal "$credential_screen" 'var password by rememberSaveable' 'passwords must never be serialized into Compose saved-instance state'
  require_literal "$credential_screen" 'var password by remember { mutableStateOf("") }' 'passwords must remain memory-only Compose state'
done

echo "Android security invariants passed."
