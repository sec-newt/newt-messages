# MMS encoding (vendored)

Android's own code for reading and writing MMS messages ("PDUs") and saving them to the
message store, from AOSP (`com.google.android.mms`, Apache License 2.0). Copied from
[FossifyOrg/mmslib](https://github.com/FossifyOrg/mmslib) on 2026-10-18 (its `pdu_alt`,
`util_alt` and base classes), which kept AOSP's copyright headers (some files are
Jacob Klinker's, also Apache 2.0).

Changes from that copy:
- Package renamed to `io.github.secnewt.messages.mms` so it can't clash with the hidden copy
  inside Android.
- Klinker's logger replaced with `android.util.Log`.
- `SendReq.prepareFromAddress` no longer reads the phone's own number; the carrier fills it in.
- `PduPersister` saves the subscription id whenever it's real (the library's checker is gone).
- DRM (forward-locked) conversion removed: it used a hidden Android class, and copy-protected
  MMS parts are a flip-phone-era feature. Such parts are saved as they arrive.

Not copied: the library's own HTTP sending (OkHttp 2.5, internet access) and its APN handling.
Sending and downloading go through Android's MMS service (`SmsManager.sendMultimediaMessage`
and `downloadMultimediaMessage`), so the app has no internet permission.
