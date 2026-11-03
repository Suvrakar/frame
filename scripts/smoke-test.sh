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
echo "SMOKE: OK" >> "$LOG"
