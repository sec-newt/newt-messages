package io.github.secnewt.messages.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person as NotificationPerson
import androidx.core.app.RemoteInput
import io.github.secnewt.messages.MainActivity
import io.github.secnewt.messages.R
import io.github.secnewt.messages.chats.Codes
import io.github.secnewt.messages.chats.Message
import io.github.secnewt.messages.chats.Person
import io.github.secnewt.messages.reactions.ParsedReaction
import io.github.secnewt.messages.settings.ReactionNotify
import io.github.secnewt.messages.settings.SettingsStore
import io.github.secnewt.messages.settings.Speaker
import io.github.secnewt.messages.settings.Speaking
import io.github.secnewt.messages.sms.MessageStore

/**
 * The app's notifications. One per chat, listing its unread texts, with Reply, Mark read and
 * (for a sign-in code) Copy. Reactions get their own quiet one, never a text-style one.
 */
class Notifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)
    private val settings = SettingsStore(context)

    init {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH),
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_REACTIONS, "Reactions", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Someone reacted to a message. Quiet by default."
            },
        )
    }

    private fun allowed() =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Shows (or clears) a chat's notification from its unread texts. [announce] when a new
     * message just arrived, to say who it's from (if that's turned on).
     */
    fun update(threadId: Long, announce: Boolean = false) {
        if (!allowed()) return
        val store = MessageStore(context)
        val unread = store.unreadTexts(threadId)
        if (settings.muted(threadId)) {
            manager.cancel(threadId.toInt())
            return
        }
        if (unread.isEmpty()) {
            manager.cancel(threadId.toInt())
            return
        }
        val people = store.people(threadId)
        val me = NotificationPerson.Builder().setName("You").build()
        val style = NotificationCompat.MessagingStyle(me)
        if (people.size > 1) style.setGroupConversation(true).setConversationTitle(people.joinToString(", ") { it.shownName })
        unread.forEach { style.addMessage(it.body, it.timeMillis, it.from?.toNotificationPerson()) }

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(open(threadId))
            .setAutoCancel(true)
            .addAction(markRead(threadId))
        // Replying from the notification only for one-to-one chats until group texts (MMS) work.
        if (people.size == 1) builder.addAction(reply(threadId, people.first().address))
        Codes.find(unread.last().body)?.let { builder.addAction(copy(it)) }
        post(threadId.toInt(), builder.build())
        if (announce) {
            val last = unread.last()
            last.from?.let { from ->
                val group = if (people.size > 1) store.chats().firstOrNull { it.threadId == threadId }?.name ?: "a group" else null
                val body = if (settings.settings().readMessage) last.body.ifBlank { if (last.images.isNotEmpty()) "a picture" else "" } else null
                Speaker.announce(context, Speaking.text(from.name, from.address, group, body))
            }
        }
    }

    /** "Becca ❤️ your message “Let's do lunch…”", quietly. */
    fun reaction(threadId: Long, from: Person, reaction: ParsedReaction, target: Message?) {
        if (!allowed() || reaction.removed || settings.muted(threadId)) return
        val how = settings.settings().reactions
        if (how == ReactionNotify.OFF) return
        val quote = (target?.body ?: reaction.quoted.removeSurrounding("\"")).let { if (it.length > 60) it.take(57) + "…" else it }
        val whose = if (target == null || target.mine) "your message" else "${target.from?.shownName}'s message"
        val text = "${from.shownName} ${reaction.emoji} $whose “$quote”"
        val channel = if (how == ReactionNotify.LIKE_A_TEXT) CHANNEL_MESSAGES else CHANNEL_REACTIONS
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("${from.shownName} reacted ${reaction.emoji}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(threadId))
            .setAutoCancel(true)
            .setSilent(how != ReactionNotify.LIKE_A_TEXT)
            .build()
        post(REACTION_ID_OFFSET + threadId.toInt(), notification)
    }

    /** A picture message couldn't download; the chat has a Download button to try again. */
    fun mmsFailed(threadId: Long) {
        val text = "A picture or group message couldn't download. Open the chat and tap Download to try again."
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("A message couldn't download")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(threadId))
            .setAutoCancel(true)
            .build()
        post(MMS_ID + threadId.toInt(), notification)
    }

    fun cancel(threadId: Long) = manager.cancel(threadId.toInt())

    private fun post(id: Int, notification: android.app.Notification) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            // Notifications were turned off between the check and now.
        }
    }

    private fun Person.toNotificationPerson() = NotificationPerson.Builder().setName(shownName).setKey(address).build()

    private fun open(threadId: Long): PendingIntent = PendingIntent.getActivity(
        context, threadId.toInt(),
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_THREAD, threadId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun markRead(threadId: Long) = NotificationCompat.Action.Builder(
        0, "Mark read",
        PendingIntent.getBroadcast(
            context, threadId.toInt(),
            Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.MARK_READ).putExtra(ActionReceiver.EXTRA_THREAD, threadId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ),
    ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).build()

    private fun reply(threadId: Long, address: String): NotificationCompat.Action {
        val input = RemoteInput.Builder(ActionReceiver.EXTRA_REPLY).setLabel("Reply").build()
        // Mutable: Android puts the typed reply into it.
        val intent = PendingIntent.getBroadcast(
            context, threadId.toInt(),
            Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.REPLY)
                .putExtra(ActionReceiver.EXTRA_THREAD, threadId).putExtra(ActionReceiver.EXTRA_ADDRESS, address),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(0, "Reply", intent)
            .addRemoteInput(input)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setAllowGeneratedReplies(false)
            .build()
    }

    private fun copy(code: String) = NotificationCompat.Action.Builder(
        0, "Copy $code",
        PendingIntent.getBroadcast(
            context, code.hashCode(),
            Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.COPY_CODE).putExtra(ActionReceiver.EXTRA_CODE, code),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ),
    ).build()

    companion object {
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_REACTIONS = "reactions"
        private const val REACTION_ID_OFFSET = 1_000_000
        private const val MMS_ID = 2_000_000
    }
}
