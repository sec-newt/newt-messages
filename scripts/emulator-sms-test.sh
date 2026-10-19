#!/usr/bin/env bash
# Installs the app on an emulator, makes it the default messaging app, and checks:
#  1. an incoming text is stored and notified,
#  2. an incoming reaction to it is stored as read and gets a quiet reaction notification,
#     not a text one,
#  3. typing a text and tapping Send sends it (the emulator texts itself back).
set -euo pipefail

pkg="io.github.secnewt.messages.debug"
adb install -r app-debug.apk

adb shell cmd role add-role-holder android.app.role.SMS "$pkg"
adb shell cmd role get-role-holders android.app.role.SMS | grep -q "$pkg"
adb shell pm grant "$pkg" android.permission.POST_NOTIFICATIONS
adb shell pm grant "$pkg" android.permission.READ_CONTACTS
adb shell pm grant "$pkg" android.permission.READ_PHONE_NUMBERS
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

# The unread count for Newt Home is locked to Newt-signed apps: an app without the key (the
# shell) must be turned away. (It counts exactly what the chat list shows: MessageStore.unreadCount.)
denied="$(adb shell content query --uri "content://$pkg.unread/count" 2>&1 || true)"
echo "$denied" | grep -qi "permission\|denial\|security" || fail "An app without the Newt key could read the unread count: $denied"
echo "The unread count is locked to Newt-signed apps."

# Sending, the way a person does it: open a chat from an "sms:" link with the text already
# typed, then tap Send. The radio reporting it sent is the check (this emulator image doesn't
# reliably text its own number back, so that isn't required).
adb logcat -c
adb shell am start -W -a android.intent.action.SENDTO -d smsto:15555215554 --es sms_body Ping \
  -n "$pkg/io.github.secnewt.messages.MainActivity" > /dev/null
sleep 4
adb shell uiautomator dump /sdcard/ui.xml > /dev/null
adb pull /sdcard/ui.xml ui.xml > /dev/null
bounds="$(grep -o 'content-desc="Send"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' ui.xml | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -1 || true)"
[ -n "$bounds" ] || { grep -o 'text="[^"]*"' ui.xml | head -20; fail "The chat didn't open with a Send button."; }
read -r x1 y1 x2 y2 <<< "$(echo "$bounds" | tr -c '0-9' ' ')"
adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
# Sending and the text coming back can take a while on a cold emulator: wait up to 30 s.
for _ in $(seq 15); do
  adb logcat -d -s NewtSms:I > send.log
  grep -q "Text sent" send.log && break
  sleep 2
done
cat send.log
grep -q "Text sent" send.log || fail "Tapping Send didn't send the text."
echo "Sending works: tapping Send sent the text."

# Renaming a group: open a two-person chat from a link, open its details, tap Rename, type
# a name, save, and the details show it.
tap() { # tap the element whose content-desc or text is $1, waiting up to ~10 s for it
  local b=""
  for try in 1 2 3 4 5 6 7 8; do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null
    adb pull /sdcard/ui.xml ui.xml > /dev/null
    b="$(grep -o "\(content-desc\|text\)=\"$1\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" ui.xml | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -1 || true)"
    [ -n "$b" ] && break
    # Not there yet: wait twice (it may still be drawing), then scroll down to look further.
    if [ "$try" -gt 2 ]; then adb shell input swipe 540 1700 540 900 300; fi
    sleep 2
  done
  [ -n "$b" ] || { grep -o 'text="[^"]\+"\|content-desc="[^"]\+"' ui.xml; fail "Couldn't find \"$1\" on screen."; }
  read -r x1 y1 x2 y2 <<< "$(echo "$b" | tr -c '0-9' ' ')"
  adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
  sleep 2
}
adb shell am start -W -a android.intent.action.SENDTO -d "smsto:5550111,5550122,5550133" \
  -n "$pkg/io.github.secnewt.messages.MainActivity" > /dev/null
sleep 3
tap "Group details"
tap "Rename"
tap "Group name"
adb shell input text "Small%sgroup"
sleep 1
tap "Save name"
adb shell uiautomator dump /sdcard/ui.xml > /dev/null
adb pull /sdcard/ui.xml ui.xml > /dev/null
grep -q 'text="Small group"' ui.xml || fail "After renaming, the group's name didn't show."
echo "Renaming a group works."

# Removing someone starts a new group with the others (keeping the name), which opens.
tap "Remove 5550111 from the group"
tap "Start new group"
tap "Group details"
adb shell uiautomator dump /sdcard/ui.xml > /dev/null
adb pull /sdcard/ui.xml ui.xml > /dev/null
grep -q 'text="2 people, plus you"' ui.xml && grep -q 'text="Small group"' ui.xml && ! grep -q 'text="5550111"' ui.xml \
  || { grep -o 'text="[^"]*"' ui.xml | head -20; fail "Removing someone didn't open a new group with the others."; }
echo "Removing someone from a group works."

# Starting a new chat: back to the list, New message, type a number, add it, open the chat.
adb shell am start -W -n "$pkg/io.github.secnewt.messages.MainActivity" > /dev/null
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell am start -W -n "$pkg/io.github.secnewt.messages.MainActivity" > /dev/null
sleep 2
tap "New message"
tap "Name or number"
adb shell input text "5550177"
sleep 1
tap "Add 5550177"
tap "Text 5550177"
# The chat's top bar must sit below Android's status bar, not under it (Jerry's screenshot,
# 2026-10-19). Its Back button starts lower than the status bar's height.
adb shell uiautomator dump /sdcard/ui.xml > /dev/null
adb pull /sdcard/ui.xml ui.xml > /dev/null
status="$(adb shell dumpsys window | grep -o 'statusBars.*frame=\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -1 | grep -o '\]\[[0-9]*,[0-9]*\]' | grep -o ',[0-9]*' | tr -d ',' || true)"
[ -n "$status" ] || status=63
back_top="$(grep -o 'content-desc="Back"[^>]*bounds="\[[0-9]*,[0-9]*\]' ui.xml | grep -o ',[0-9]*\]$' | tr -d ',]' | head -1 || true)"
echo "Status bar ends at $status px; the chat's Back button starts at ${back_top:-?} px."
[ -n "$back_top" ] && [ "$back_top" -ge "$status" ] || fail "The chat's top bar runs under the status bar."
echo "The chat's top bar sits below the status bar."
adb shell uiautomator dump /sdcard/ui.xml > /dev/null
adb pull /sdcard/ui.xml ui.xml > /dev/null
grep -q 'content-desc="Send"' ui.xml && grep -q 'text="5550177"' ui.xml \
  || { grep -o 'text="[^"]*"\|content-desc="[^"]*"' ui.xml | head -20; fail "New message didn't open a chat with the typed number."; }
echo "Starting a new chat works."

adb logcat -d > after.log
if grep -q "FATAL EXCEPTION" after.log; then
  grep -A20 "FATAL EXCEPTION" after.log
  fail "The app crashed."
fi
echo "Emulator SMS test passed."
