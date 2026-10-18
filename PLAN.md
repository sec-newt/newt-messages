# Newt Messages Plan

A private SMS and MMS app for GrapheneOS, replacing QUIK. Part of the Newt family: it
follows the shared [Newt house style](NEWT_STYLE.md), and reuses Newt Phone's spam
rules, caller announcement and per-person colors.

## Goals

- **SMS and MMS**, as the phone's default messaging app, including group chats.
- **Reactions that look like reactions,** including in notifications, which QUIK doesn't do.
- **Chats that are easy to find:** tabs for People, Groups, and Codes & alerts.
- **No internet.** Messages never leave the phone except as SMS or MMS. Picture messages go
  through Android's own MMS service (`SmsManager.sendMultimediaMessage` /
  `downloadMultimediaMessage`), so the app shouldn't need the internet permission.
  *To verify in Phase 2.*

History lives in Android's shared message store, not in QUIK, so switching apps loses nothing.

## The screens

- **Tabs: People / Groups / Codes & alerts.**
  - People: one-to-one chats.
  - Groups: group chats, with names I choose ("Family", "Church small group").
  - Codes & alerts: two-factor codes, delivery notices and 5–6 digit short-code senders,
    the noise that buries real chats.
- **Pin** top chats; **search by member** to find every group with a person.
- **Colors per person:** the same color as in Newt Phone and Newt Home, on the name and the
  bubble outline (not filled bubbles, so text stays at 7:1 contrast).

## Reactions

Over SMS and MMS, a reaction arrives as an ordinary text. No RCS is needed to read one, and
none is available: RCS is reserved for Google Messages.

- **iPhone wording:** `Loved “…”`, `Liked “…”`, `Disliked “…”`, `Laughed at “…”`,
  `Emphasized “…”`, `Questioned “…”`, `Reacted 😀 to “…”`, and removals such as
  `Removed a heart from “…”`. Long messages arrive cut short with `…`.
- **Google Messages:** the emoji and quoted text wrapped in invisible characters
  (U+200A, U+200B; U+200C for removals).
- QUIK (GPL-3.0, same licence as this app) has the patterns for about 15 languages. Reuse
  them with credit.

How it works:

1. **On receive, check first.** If a text is a reaction and the quoted text matches a recent
   message in the same chat (cut-off quotes allowed), the emoji goes on that message. The raw
   text is marked read, never deleted, so other apps and backups still see it.
2. **Notification setting:**
   - *As a reaction* (default): a quiet "Becca ❤️ your message “Let's do lunch…”"
   - *Don't notify*
   - *Like a text*, which is what QUIK does
3. **No matching message** (deleted, or too far back): it still shows as a reaction to "a
   message", never as a raw bubble.
4. **Sending:** reply in the wording that chat last used. When unknown, use the iPhone
   wording, which reads cleanly everywhere.
5. **Groups:** a reaction arrives from the member who reacted, so it's matched against
   everyone's messages in the chat.

Why QUIK shows reactions as texts: it only recognizes them when drawing a chat, while its
notification code lists unread messages as they are.

## Other features

- **Spam blocking:** Android's block list, shared with Newt Phone, so a block in either app
  applies to both. Newt Phone's rules carry over: contacts always get through, and "numbers
  starting with" rules.
- **Speak the sender** ("Message from Becca"), reusing Newt Phone's announcement settings.
- **Copy two-factor codes** from the notification. The copy is marked sensitive and cleared
  after a minute or two.
- **Newt Home:** "3 unread" under Messages, like missed calls under Phone.

## Roadmap

Newt Messages can't be the phone's messaging app until it receives both SMS **and** MMS: only
the default app can read or store messages, and switching early would lose group texts. So MMS
receiving moves up into Phase 1, and switching waits for it.

1. **Phase 1: SMS that works every day.** Default messaging app role, the three tabs, chats,
   sending and receiving SMS, notifications with reply, the shared block list.
2. **Phase 2: MMS.** Pictures and group chats. Build on a maintained library instead of
   writing MMS encoding from scratch: Fossify's `org.fossify:mmslib`, used by Fossify Messages
   (GPL-3.0, active). Group names, per-person colors. Test early with a throwaway group:
   carrier quirks are the biggest risk.
3. **Phase 3: Reactions and extras.** Reactions as above, 2FA copy, speaking the sender, the
   unread count in Newt Home.

- [x] Create the GitHub repo
- [ ] Phase 1
  - [x] Foundations: tab sorting, 2FA code spotting, reaction parsing and matching, the chat
    list and chat screens (sample data only; not usable as the messaging app yet)
  - [ ] Default messaging app: send and receive SMS, notifications with reply and Copy code,
    reactions folded and notified quietly (block list: Android drops blocked senders' texts
    before they reach any app; to confirm on the phone)
  - [ ] MMS receive and send (needed before switching: group texts are MMS)
- [ ] Phase 2
- [ ] Phase 3

## Open questions

- Does Ting pass group MMS cleanly?
- Speak just the sender, or read the message too?
- How do iPhones show a reaction sent in Google's invisible-character form? (Expected:
  garbled, which is why iPhone wording is the default.)
- Confirm the app needs no internet permission for MMS on GrapheneOS.

## Prior art

- [QUIK](https://github.com/quik-sms/quik) (GPL-3.0): reaction patterns; sending reactions in PR #846.
- [Fossify Messages](https://github.com/FossifyOrg/Messages) (GPL-3.0): MMS, group chats.
