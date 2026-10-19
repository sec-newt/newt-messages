package io.github.secnewt.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.secnewt.messages.notify.Notifier

/**
 * A picture or group message (MMS) is waiting. Android requires every default messaging app
 * to accept these; downloading them comes in the next step. Until then this says so, and the
 * app tells people not to make it their messaging app yet.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.w("NewtSms", "A picture or group message arrived; this version can't download it yet")
        Notifier(context).mmsNotSupported()
    }
}
