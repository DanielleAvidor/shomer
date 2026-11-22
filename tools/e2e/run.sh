#!/usr/bin/env bash
# Runs inside the Android emulator (started by .github/workflows/android-e2e.yml).
# Installs the debug APK, grants the two permissions, sends fake SMS messages through the emulator's modem,
# and saves screenshots, a screen recording and the text of the notifications Shomer posted.
set -u
OUT=${OUT:-e2e-out}; mkdir -p "$OUT"
APK=$(ls app/app/build/outputs/apk/debug/*.apk | head -1)
adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard || true
adb install -r "$APK" | tee "$OUT/install.txt"
adb shell pm grant il.shomer android.permission.RECEIVE_SMS
adb shell pm grant il.shomer android.permission.POST_NOTIFICATIONS
adb shell settings put global heads_up_notifications_enabled 1

adb shell "screenrecord --time-limit 170 /data/local/tmp/e2e.mp4" &
REC=$!
adb shell am start -n il.shomer/.MainActivity; sleep 4
adb exec-out screencap -p > "$OUT/01-app-home.png"

send() { # $1 = sender, $2 = text, $3 = label
  adb emu sms send "$1" "$2"
  sleep 4
  adb exec-out screencap -p > "$OUT/$3-heads-up.png"
  adb shell cmd statusbar expand-notifications; sleep 2
  adb exec-out screencap -p > "$OUT/$3-shade.png"
  adb shell cmd statusbar collapse; sleep 1
}
adb shell input keyevent KEYCODE_HOME; sleep 1
# 1. A scam: the Israel Post name, with a link that is not the official site. Should raise an alert.
send 0501234567 'דואר ישראל: החבילה שלך נעצרה. אשר כתובת https://israelpost.paliet.top/il' 02-scam
# 2. An ordinary message. Should stay quiet.
send 0527654321 'אני מגיעה בשש, תשמרו לי מקום' 03-ordinary

adb shell dumpsys notification --noredact > "$OUT/dumpsys-notification.txt"
grep -E "pkg=il.shomer|android.title|android.text|android.bigText" "$OUT/dumpsys-notification.txt" > "$OUT/shomer-notifications.txt" || true
adb shell am start -n il.shomer/.MainActivity; sleep 3
adb exec-out screencap -p > "$OUT/04-app-after.png"
adb logcat -d -s AndroidRuntime:E > "$OUT/crashes.txt" || true
adb logcat -d > "$OUT/logcat.txt" || true
echo "--- logcat (Shomer) ---"; grep -E "ShomerE2E|il.shomer|AndroidRuntime" "$OUT/logcat.txt" | tail -40
echo "--- package receivers ---"; adb shell dumpsys package il.shomer | grep -iE "SmsReceiver|RECEIVE_SMS|granted=" | head -12
sleep 2; kill $REC 2>/dev/null || true; sleep 3
adb pull /data/local/tmp/e2e.mp4 "$OUT/e2e.mp4" || true
echo "--- Shomer notifications ---"; cat "$OUT/shomer-notifications.txt"
# The run fails unless the scam raised exactly one alert and the ordinary message raised none.
FAIL=0
ALERTS=$(grep -c "android.title=String (⛔" "$OUT/shomer-notifications.txt" || true)
OTHER=$(grep -c "android.title=String (❓" "$OUT/shomer-notifications.txt" || true)
[ "$ALERTS" = "1" ] || { echo "FAIL: expected exactly 1 alert for the scam SMS, saw $ALERTS"; FAIL=1; }
[ "$OTHER" = "0" ] || { echo "FAIL: the ordinary message must not raise an alert, saw $OTHER"; FAIL=1; }
[ -s "$OUT/crashes.txt" ] && { echo "FAIL: the app crashed"; cat "$OUT/crashes.txt" | head -20; FAIL=1; }
[ "$FAIL" = "0" ] && echo "PASS: scam -> one alert, ordinary message -> none"
exit $FAIL
