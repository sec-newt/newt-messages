package io.github.secnewt.messages.sms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.util.Log
import kotlin.concurrent.thread

/**
 * Sends a text: saved to the outbox first, then moved to Sent (or Failed) when the phone reports
 * back, and marked Delivered when the carrier says it arrived.
 */
object SmsSender {

    fun send(context: Context, address: String, body: String) {
        if (address.isBlank() || body.isBlank()) return
        val store = MessageStore(context)
        val uri = store.saveOutgoing(address, body)
        if (uri == null) {
            Log.w("NewtSms", "The message store refused an outgoing text")
            return
        }
        transmit(context, uri, address, body)
    }

    /** Tries a failed text again, in place: the same message, moved back to the outbox. */
    fun resend(context: Context, id: Long) {
        val (address, body) = MessageStore(context).requeueSms(id) ?: return
        transmit(context, Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString()), address, body)
    }

    private fun transmit(context: Context, uri: Uri, address: String, body: String) {
        val sms = context.getSystemService(SmsManager::class.java)
        try {
            val parts = sms.divideMessage(body)
            // One "sent" report per part; only the last one moves the message to Sent, and any
            // failed part marks it failed. One delivery report, for the last part.
            val sent = ArrayList(parts.indices.map { i -> report(context, SentReceiver::class.java, uri, i, i == parts.lastIndex) })
            val delivered = ArrayList(parts.indices.map { i -> if (i == parts.lastIndex) report(context, DeliveredReceiver::class.java, uri, i, true) else null })
            sms.sendMultipartTextMessage(address, null, parts, sent, delivered)
            Log.i("NewtSms", "Sending a text in ${parts.size} part(s)")
        } catch (e: Exception) {
            Log.e("NewtSms", "Couldn't send a text", e)
            MessageStore(context).markSent(uri, ok = false)
        }
    }

    private fun report(context: Context, receiver: Class<out BroadcastReceiver>, uri: Uri, part: Int, last: Boolean): PendingIntent {
        // The message's address is in the data, so every message's reports stay apart.
        val intent = Intent(context, receiver).setData(uri).putExtra(SentReceiver.EXTRA_LAST, last)
        // Mutable: Android adds the carrier's report (the "pdu") to the delivery one.
        val flags = if (receiver == DeliveredReceiver::class.java) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, part, intent, flags or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/** The phone's report on a text being sent. */
class SentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri: Uri = intent.data ?: return
        val ok = resultCode == android.app.Activity.RESULT_OK
        if (!ok || intent.getBooleanExtra(EXTRA_LAST, true)) {
            val pending = goAsync()
            thread {
                try {
                    MessageStore(context).markSent(uri, ok)
                    Log.i("NewtSms", if (ok) "Text sent" else "A text failed to send (code $resultCode)")
                } finally {
                    pending.finish()
                }
            }
        }
    }

    companion object {
        const val EXTRA_LAST = "last"
    }
}

/** The carrier's report that a text reached the other phone (or never will). */
class DeliveredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri: Uri = intent.data ?: return
        val pdu = intent.getByteArrayExtra("pdu") ?: return
        val format = intent.getStringExtra("format") ?: SmsMessage.FORMAT_3GPP
        val status = try {
            SmsMessage.createFromPdu(pdu, format)?.status ?: return
        } catch (e: Exception) {
            return
        }
        val stored = DeliveryReports.stored(status, format)
        val pending = goAsync()
        thread {
            try {
                MessageStore(context).markDelivered(uri, stored)
                Log.i("NewtSms", "Delivery report: $stored")
            } finally {
                pending.finish()
            }
        }
    }
}

/** Turns a carrier's delivery report into how Android stores it. Kept free of Android so it can be tested. */
object DeliveryReports {
    const val COMPLETE = 0
    const val PENDING = 32
    const val FAILED = 64

    fun stored(status: Int, format: String): Int =
        if (format == "3gpp2") {
            // CDMA: an error class in bits 24–25 (0 none, 2 temporary) and a status code below it.
            val errorClass = (status shr 24) and 0x03
            val code = (status shr 16) and 0x3f
            when {
                errorClass == 0 && code == 2 -> COMPLETE
                errorClass == 0 || errorClass == 2 -> PENDING
                else -> FAILED
            }
        } else {
            // GSM: 0–31 delivered, 32–63 still trying, 64 and up failed.
            when {
                status < 0x20 -> COMPLETE
                status < 0x40 -> PENDING
                else -> FAILED
            }
        }
}
