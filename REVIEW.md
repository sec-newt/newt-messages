# What to look for when reviewing Newt Messages

Read [CLAUDE.md](CLAUDE.md) first; flag code that breaks a rule there, and lines in it
that have gone stale.

## What "broken" means here

In order of how bad it is:

1. **It leaks.** A new permission, anything that could send messages, contacts or codes
   off the phone other than as the SMS/MMS itself, message text in logs, or an exported
   component other apps can reach.
2. **It loses or misdirects a message.** A text sent to the wrong person or group, a
   group member dropped, a message lost, shown twice, or marked sent when it wasn't (or
   the other way round), a draft lost, a reaction attached to the wrong message.
3. **The owner can't use it.** Text under 16sp or under 7:1 contrast, buttons under 56dp
   or rows under 48dp, an icon with no words, something missing for TalkBack (no label, or
   two things reading out the same), a layout that breaks at 200% text, or a feature that
   needs a swipe or typing with no button.
4. **The app breaks.** A crash, a hang on the main thread, a missed incoming message, or a
   lost setting, pin, color or group name. As the default messaging app, a crash can mean
   texts the owner never sees.
5. **It lies.** README, a label, a hint or a status that says something different from
   what the code does.

## Always call out, even if it looks fine

Any change to `.github/workflows/`, `scripts/`, `config/allowed-permissions.txt`,
`AndroidManifest.xml`, signing, release, or `gradle/libs.versions.toml`. Say in
one line what changed so the owner reads it themselves.

## How to report

- Evidence for every finding: file and line, and what goes wrong for the person using it.
  Don't guess from a name.
- Tag each one **Important**, **Nit** or **Pre-existing** (a problem this PR didn't add).
- At most 3 nits. Skip style, naming and formatting unless it hurts readability.
- Start the summary with a count: "2 important, 1 nit." If nothing needs fixing, say so
  plainly: "Nothing to fix."
- Don't repeat a finding that's already posted on this PR.

## Skip

`app/build/`, `build/`, `docs/superpowers/` (old plans and specs), and screenshot images. Skip the Android MMS
code in `app/src/main/java/io/github/secnewt/messages/mms/` unless this PR changes it.
