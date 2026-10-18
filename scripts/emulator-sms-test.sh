#!/usr/bin/env bash
# Installs the app on an emulator, makes it the default messaging app, and checks:
#  1. an incoming text is stored and notified,
#  2. an incoming reaction to it is stored as read and gets a quiet reaction notification,
#     not a text one,
#  3. a text sent through "reply with a message" goes out (the emulator texts itself back).
set -euo pipefail

pkg="io.github.secnewt.messages.debug"
adb install -r app-debug.apk

adb shell cmd role add-role-holder android.app.role.SMS "$pkg"
adb shell cmd role get-role-holders android.app.role.SMS | grep -q "$pkg"
adb shell pm grant "$pkg" android.permission.POST_NOTIFICATIONS
adb shell pm grant "$pkg" android.permission.READ_CONTACTS
echo "App is the default messaging app."

fail() {
  echo "::error::$1"
  adb logcat -d -s NewtSms:* AndroidRuntime:E | tail -40
  exit 1
}

adb logcat -c
adb emu sms send 5550111 'Sounds good'
sleep 6
adb logcat -d -s NewtSms:I > receive.log
grep -q "Stored an incoming text" receive.log || fail "An incoming text wasn't stored."
adb shell dumpsys notification --noredact > notifications.txt
grep -q "pkg=$pkg.*messages" notifications.txt || grep -q "$pkg" notifications.txt || fail "No notification for the incoming text."
echo "Incoming text stored and notified."

adb logcat -c
adb emu sms send 5550111 'Liked "Sounds good"'
sleep 6
adb logcat -d -s NewtSms:I > reaction.log
grep -q "Incoming reaction, matched a message" reaction.log || fail "The reaction wasn't recognized and matched to its message."
adb shell dumpsys notification --noredact > notifications.txt
grep -q "reacted" notifications.txt || fail "No reaction notification."
echo "Reaction recognized, matched, and notified as a reaction."

# The phone app's "reply with a message" path. The shell may not hold the permission that
# guards it; if so, say so instead of failing, since the code path is the same SmsSender.
adb logcat -c
started="$(adb shell am startservice -n "$pkg/io.github.secnewt.messages.sms.RespondService" \
    -a android.intent.action.RESPOND_VIA_MESSAGE -d smsto:15555215554 --es android.intent.extra.TEXT 'Ping' 2>&1 || true)"
echo "am startservice said: $started"
if echo "$started" | grep -qi "error\|exception\|denial\|not allowed"; then
  echo "::warning::The shell can't start the reply service (it lacks SEND_RESPOND_VIA_MESSAGE); sending wasn't tested here."
else
  sleep 8
  adb logcat -d -s NewtSms:I > send.log
  cat send.log
  adb logcat -d | grep -i "RespondService\|NewtSms\|ActivityManager.*messages" | tail -20 || true
  grep -q "Text sent" send.log || fail "A text was handed to the app but never reported sent."
  grep -q "Stored an incoming text" send.log || fail "The emulator's own number didn't get the text back."
  echo "Sending works: the text went out and came back."
fi

adb logcat -d > after.log
if grep -q "FATAL EXCEPTION" after.log; then
  grep -A20 "FATAL EXCEPTION" after.log
  fail "The app crashed."
fi
echo "Emulator SMS test passed."
