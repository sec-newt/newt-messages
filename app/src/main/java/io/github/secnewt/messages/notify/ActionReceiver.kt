package io.github.secnewt.messages.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.os.SystemClock
import androidx.core.app.RemoteInput
import io.github.secnewt.messages.sms.MessageStore
import io.github.secnewt.messages.sms.Outgoing
import kotlin.concurrent.thread

/** The notification buttons: Reply, Mark read, Copy a code, and clearing that copy later. */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(EXTRA_THREAD, -1)
        when (intent.action) {
            REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(EXTRA_REPLY)?.toString()
                if (text.isNullOrBlank() || threadId < 0) return
                background {
                    // Everyone in the chat: one person gets a text, a group gets a group message.
                    val store = MessageStore(context)
                    val to = store.people(threadId).map { it.address }
                    val problem = Outgoing.send(context, to, text)
                    store.markRead(threadId)
                    Notifier(context).cancel(threadId)
                    if (problem != null) Notifier(context).replyFailed(threadId, problem)
                }
            }
            MARK_READ -> background {
                MessageStore(context).markRead(threadId)
                Notifier(context).cancel(threadId)
            }
            COPY_CODE -> {
                val code = intent.getStringExtra(EXTRA_CODE) ?: return
                val clip = ClipData.newPlainText(CLIP_LABEL, code).apply {
                    // Android hides sensitive copies from the clipboard preview.
                    description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
                }
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                scheduleClear(context)
            }
            CLEAR_CODE -> try {
                // Android doesn't let an app in the background look at the clipboard (it reads as
                // empty), so there's no checking whether the code is still there: it's cleared
                // either way. Something copied in the two minutes since goes too.
                context.getSystemService(ClipboardManager::class.java).clearPrimaryClip()
            } catch (e: Exception) {
                // Clearing isn't allowed either; Android clears the clipboard itself after an hour.
            }
        }
    }

    private fun background(work: () -> Unit) {
        val pending = goAsync()
        thread {
            try {
                work()
            } finally {
                pending.finish()
            }
        }
    }

    private fun scheduleClear(context: Context) {
        val clear = PendingIntent.getBroadcast(
            context, 0, Intent(context, ActionReceiver::class.java).setAction(CLEAR_CODE), PendingIntent.FLAG_IMMUTABLE,
        )
        // Roughly two minutes; it doesn't need to be exact.
        context.getSystemService(AlarmManager::class.java)
            .set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + CLEAR_AFTER_MILLIS, clear)
    }

    companion object {
        const val REPLY = "io.github.secnewt.messages.REPLY"
        const val MARK_READ = "io.github.secnewt.messages.MARK_READ"
        const val COPY_CODE = "io.github.secnewt.messages.COPY_CODE"
        const val CLEAR_CODE = "io.github.secnewt.messages.CLEAR_CODE"
        const val EXTRA_THREAD = "thread"
        const val EXTRA_REPLY = "reply"
        const val EXTRA_CODE = "code"
        const val CLIP_LABEL = "Sign-in code"
        const val CLEAR_AFTER_MILLIS = 2 * 60_000L
    }
}
