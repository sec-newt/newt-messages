package io.github.secnewt.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import io.github.secnewt.messages.notify.Notifier
import io.github.secnewt.messages.reactions.Reactions
import kotlin.concurrent.thread

/**
 * A text arrived. As the default messaging app, saving it is this app's job: Android only
 * delivers it here. A reaction is saved already read and gets a quiet reaction notification
 * instead of a text one. The number and words never go to the system log.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val first = parts.firstOrNull() ?: return
        val address = first.displayOriginatingAddress ?: first.originatingAddress ?: return
        val body = parts.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }
        val subscription = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1)
        val pending = goAsync()
        thread {
            try {
                val store = MessageStore(context)
                val reaction = Reactions.parse(body)
                val threadId = store.saveIncoming(address, body, first.timestampMillis, subscription, read = reaction != null)
                if (threadId == null) {
                    Log.w(TAG, "The message store refused an incoming text")
                    return@thread
                }
                if (reaction != null) {
                    val target = Reactions.target(reaction, store.raw(threadId).asReversed().drop(1))
                    Log.i(TAG, "Incoming reaction, ${if (target != null) "matched a message" else "no matching message"}")
                    Notifier(context).reaction(threadId, store.person(address), reaction, target)
                } else {
                    Log.i(TAG, "Stored an incoming text")
                    Notifier(context).update(threadId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't handle an incoming text", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "NewtSms"
    }
}
