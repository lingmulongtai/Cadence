#!/usr/bin/env bash
set -euo pipefail
analyzer="$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer"
debug_apk=app/build/outputs/apk/debug/app-debug.apk
release_apk=app/build/outputs/apk/release/app-release-unsigned.apk
for apk in "$debug_apk" "$release_apk"; do
  permissions=$("$analyzer" manifest permissions "$apk")
  if grep -Eq 'android\.permission\.(INTERNET|ACCESS_NETWORK_STATE|HIGH_SAMPLING_RATE_SENSORS|READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE)' <<< "$permissions"; then
    echo "Unexpected permission in $apk"
    exit 1
  fi
done
debug_manifest=$("$analyzer" manifest print "$debug_apk")
grep -Fq 'SensorRecordingService' <<< "$debug_manifest"
grep -Fq 'dev.lingmulongtai.cadence.recording.exports' <<< "$debug_manifest"
release_manifest=$("$analyzer" manifest print "$release_apk")
if grep -Eq 'SensorRecordingService|recording.exports' <<< "$release_manifest"; then
  echo 'Debug recorder or export provider leaked into the release manifest'
  exit 1
fi
# Inspect pre-R8 classes too, so shrinking or renaming cannot hide source-set leaks.
test -f app/build/intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes/dev/lingmulongtai/cadence/MainActivity.class
for module in app overlay sensor data; do
  release_classes=$(find "$module/build/intermediates" -path '*/release/*' -type f -name '*.class')
  if grep -Eq '(SensorRecordingService|RecordingController|AndroidSensorSource|RecordingEntryPoint|RecordingRepository|SavedRecording|RecordingExports)' <<< "$release_classes"; then
    echo "Debug recording code leaked into release classes in $module"
    exit 1
  fi
done
echo 'Debug recorder is present; production release remains free of recording/export code.'
