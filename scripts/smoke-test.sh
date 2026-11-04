#!/usr/bin/env bash
# Installs the debug build, launches it, and fails if the process crashes or dies.
set -u
PKG=app.frame.studio.debug
LOG=smoke.log
adb install -r app/build/outputs/apk/debug/app-debug.apk > "$LOG" 2>&1 || { echo "install failed" >> "$LOG"; exit 1; }
adb logcat -c
adb shell am start -W -n "$PKG/app.frame.studio.MainActivity" >> "$LOG" 2>&1
sleep 20
# Dismiss a consent form if one is shown, then exercise the main screen.
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell am start -n "$PKG/app.frame.studio.MainActivity" >> "$LOG" 2>&1; sleep 5
adb exec-out screencap -p > smoke.png || true
adb logcat -d -b crash >> "$LOG" 2>&1
adb logcat -d | grep -E "AndroidRuntime|FATAL|$PKG|Frame|Ads" | tail -200 >> "$LOG"
if adb logcat -d -b crash | grep -q "$PKG"; then
  echo "SMOKE: CRASH DETECTED" >> "$LOG"; exit 1
fi
if [ -z "$(adb shell pidof "$PKG" | tr -d '\r')" ]; then
  echo "SMOKE: PROCESS NOT RUNNING" >> "$LOG"; exit 1
fi
echo "SMOKE: LAUNCH OK" >> "$LOG"

# End-to-end export through the real Media3 pipeline.
adb push /tmp/sample.mp4 /data/local/tmp/sample.mp4 >> "$LOG" 2>&1
adb push /tmp/sample.m4a /data/local/tmp/sample.m4a >> "$LOG" 2>&1
adb push /tmp/sample.png /data/local/tmp/sample.png >> "$LOG" 2>&1
if ./gradlew connectedDebugAndroidTest >> "$LOG" 2>&1; then
  echo "SMOKE: EXPORT OK" >> "$LOG"
else
  find app/build/outputs/androidTest-results -name "*.xml" -exec cat {} \; >> "$LOG" 2>/dev/null
  echo "SMOKE: EXPORT FAILED" >> "$LOG"; exit 1
fi
echo "SMOKE: OK" >> "$LOG"
