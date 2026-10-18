package io.github.secnewt.messages.sms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log

/** Sends a text: saved to the outbox first, then moved to Sent (or Failed) when the phone reports back. */
object SmsSender {

    fun send(context: Context, address: String, body: String) {
        if (address.isBlank() || body.isBlank()) return
        val store = MessageStore(context)
        val uri = store.saveOutgoing(address, body)
        val sms = context.getSystemService(SmsManager::class.java)
        val parts = sms.divideMessage(body)
        // One "sent" report per part; only the last one moves the message to Sent.
        val sent = ArrayList(
            parts.indices.map { i ->
                val report = Intent(context, SentReceiver::class.java)
                    .setData(uri)
                    .putExtra(SentReceiver.EXTRA_LAST, i == parts.lastIndex)
                PendingIntent.getBroadcast(context, (uri?.hashCode() ?: 0) * 31 + i, report, PendingIntent.FLAG_IMMUTABLE)
            },
        )
        try {
            sms.sendMultipartTextMessage(address, null, parts, sent, null)
            Log.i("NewtSms", "Sending a text in ${parts.size} part(s)")
        } catch (e: Exception) {
            Log.e("NewtSms", "Couldn't send a text", e)
            uri?.let { store.markSent(it, ok = false) }
        }
    }
}

/** The phone's report on a text being sent. */
class SentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri: Uri = intent.data ?: return
        val ok = resultCode == android.app.Activity.RESULT_OK
        if (!ok || intent.getBooleanExtra(EXTRA_LAST, true)) {
            MessageStore(context).markSent(uri, ok)
            Log.i("NewtSms", if (ok) "Text sent" else "A text failed to send (code $resultCode)")
        }
    }

    companion object {
        const val EXTRA_LAST = "last"
    }
}
