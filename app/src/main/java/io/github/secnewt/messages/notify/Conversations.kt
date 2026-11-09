package io.github.secnewt.messages.notify

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.Settings
import androidx.core.app.Person as NotificationPerson
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.secnewt.messages.MainActivity
import io.github.secnewt.messages.R
import io.github.secnewt.messages.chats.Chat
import io.github.secnewt.messages.chats.Person

/**
 * Each chat as an Android "conversation". With one, a chat's notifications sit in the
 * Conversations part of the notification shade, and from there (or Chat details) it can be
 * made a priority conversation or given its own sound. It's also what lets a chat be
 * remembered by Android's share sheet. Nothing leaves the phone.
 */
object Conversations {

    fun id(threadId: Long) = "thread-$threadId"

    /** Tells Android about this chat (again, if its name or people changed). Returns its id. */
    fun publish(context: Context, chat: Chat): String {
        val id = id(chat.threadId)
        val open = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(MainActivity.EXTRA_THREAD, chat.threadId)
        val face = if (chat.isGroup) null else chat.people.firstOrNull()?.photo?.let { photo(context, it) }
        val shortcut = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(chat.title.take(SHORT_LABEL))
            .setLongLabel(chat.title)
            .setLongLived(true)
            .setLocusId(LocusIdCompat(id))
            .setIntent(open)
            .setPersons(chat.people.map { notificationPerson(context, it) }.toTypedArray())
            .setIcon(face ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIsConversation()
            .build()
        try {
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        } catch (e: Exception) {
            // Android limits how often shortcuts change; the notification still shows without one.
        }
        return id
    }

    /** A deleted chat stops being a conversation. */
    fun remove(context: Context, threadId: Long) {
        try {
            ShortcutManagerCompat.removeLongLivedShortcuts(context, listOf(id(threadId)))
        } catch (e: Exception) {
            // Nothing to remove.
        }
    }

    /** Android's own page for this chat's notifications: priority, its own sound, vibration. */
    fun settings(context: Context, chat: Chat): Intent {
        publish(context, chat)
        return Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.CHANNEL_MESSAGES)
            .putExtra(Settings.EXTRA_CONVERSATION_ID, id(chat.threadId))
    }

    fun notificationPerson(context: Context, person: Person): NotificationPerson =
        NotificationPerson.Builder()
            .setName(person.shownName)
            .setKey(person.address)
            // A contact's address lets Do Not Disturb's "starred contacts" let them through.
            .setUri(person.contact ?: "tel:${person.address}")
            .apply { person.photo?.let { photo(context, it) }?.let(::setIcon) }
            .build()

    /** A contact's photo, small, or null without one. */
    private fun photo(context: Context, uri: String): IconCompat? = try {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))) { decoder, info, _ ->
            val long = maxOf(info.size.width, info.size.height)
            if (long > PHOTO_PX) decoder.setTargetSize(info.size.width * PHOTO_PX / long, info.size.height * PHOTO_PX / long)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        IconCompat.createWithBitmap(bitmap.copy(Bitmap.Config.ARGB_8888, false))
    } catch (e: Exception) {
        null
    }

    private const val SHORT_LABEL = 25
    private const val PHOTO_PX = 192
}
