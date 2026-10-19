package io.github.secnewt.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SubscriptionManager
import android.util.Log
import io.github.secnewt.messages.mms.Mms
import kotlin.concurrent.thread

/** A picture or group message (MMS) is waiting at the carrier. Only Android can send this. */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val data = intent.getByteArrayExtra("data") ?: return
        val subscription = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1)
        val pending = goAsync()
        thread {
            try {
                Mms.onPush(context, data, subscription)
            } catch (e: Exception) {
                Log.e("NewtMms", "Couldn't handle an MMS notice", e)
            } finally {
                pending.finish()
            }
        }
    }
}
