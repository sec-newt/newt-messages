package io.github.secnewt.messages.sms

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import android.provider.Telephony
import io.github.secnewt.messages.chats.Attachment
import io.github.secnewt.messages.chats.Chat
import io.github.secnewt.messages.chats.GroupNames
import io.github.secnewt.messages.chats.Message
import io.github.secnewt.messages.chats.Person
import io.github.secnewt.messages.reactions.Folding
import io.github.secnewt.messages.reactions.Reactions

/**
 * Reads and writes Android's shared message store (the same one every messaging app uses, so
 * switching apps loses nothing). Only the default messaging app may write to it. Texts (SMS)
 * and picture or group messages (MMS) are read together; MMS messages get negative ids here so
 * the two never collide.
 */
class MessageStore(private val context: Context) {

    private val resolver get() = context.contentResolver
    private val names = mutableMapOf<String, String?>()
    private val groupNames = GroupNames(context)

    /** Every chat, newest first. */
    fun chats(): List<Chat> {
        val threads = Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build()
        val columns = arrayOf(Telephony.Threads._ID, Telephony.Threads.DATE, Telephony.Threads.RECIPIENT_IDS, Telephony.Threads.SNIPPET)
        val unread = unreadByThread()
        return resolver.query(threads, columns, null, null, "${Telephony.Threads.DATE} DESC")?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val people = c.getString(2).orEmpty().split(' ').mapNotNull { it.toLongOrNull()?.let(::address) }.map(::person)
                    if (people.isEmpty()) continue
                    val snippet = c.getString(3).orEmpty()
                    add(Chat(id, people, shownSnippet(snippet), c.getLong(1), unread = unread[id] ?: 0, name = groupNames.name(id)))
                }
            }
        }.orEmpty()
    }

    /** A chat's messages as shown (reactions folded onto their messages), oldest first. */
    fun messages(threadId: Long, limit: Int = 600): List<Message> = Folding.fold(raw(threadId, limit))

    /** Every stored message (SMS and MMS), reactions included, oldest first. */
    fun raw(threadId: Long, limit: Int = 600): List<Message> =
        (rawSms(threadId, limit) + rawMms(threadId, limit)).sortedBy { it.timeMillis }.takeLast(limit)

    private fun rawSms(threadId: Long, limit: Int): List<Message> {
        val columns = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE)
        return resolver.query(
            Telephony.Sms.CONTENT_URI, columns, "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC LIMIT $limit",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val incoming = c.getInt(4) == Telephony.Sms.MESSAGE_TYPE_INBOX
                    add(Message(c.getLong(0), c.getString(2).orEmpty(), c.getLong(3), if (incoming) person(c.getString(1).orEmpty()) else null))
                }
            }.asReversed()
        }.orEmpty()
    }

    /** Picture and group messages in a chat, oldest first. */
    private fun rawMms(threadId: Long, limit: Int, unreadOnly: Boolean = false): List<Message> {
        val columns = arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_TYPE)
        var where = "${Telephony.Mms.THREAD_ID} = ?"
        if (unreadOnly) where += " AND ${Telephony.Mms.READ} = 0 AND ${Telephony.Mms.MESSAGE_BOX} = ${Telephony.Mms.MESSAGE_BOX_INBOX}"
        return resolver.query(Telephony.Mms.CONTENT_URI, columns, where, arrayOf(threadId.toString()), "${Telephony.Mms.DATE} DESC LIMIT $limit")?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val incoming = c.getInt(2) == Telephony.Mms.MESSAGE_BOX_INBOX
                    val waiting = c.getInt(3) == PduHeadersNotification
                    val found = if (waiting) Parts() else parts(id)
                    val from = if (incoming) person(mmsSender(id).orEmpty()) else null
                    add(
                        Message(
                            -id,
                            if (waiting) "Picture or group message, not downloaded yet" else found.text,
                            c.getLong(1) * 1000, from, images = found.images, files = found.files, waiting = waiting,
                        ),
                    )
                }
            }.asReversed()
        }.orEmpty()
    }

    private data class Parts(val text: String = "", val images: List<String> = emptyList(), val files: List<Attachment> = emptyList())

    /** A message's words, pictures, and any other files. */
    private fun parts(mmsId: Long): Parts {
        val text = StringBuilder()
        val images = mutableListOf<String>()
        val files = mutableListOf<Attachment>()
        resolver.query(
            Uri.parse("content://mms/part"),
            arrayOf(
                Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.TEXT,
                Telephony.Mms.Part.FILENAME, Telephony.Mms.Part.NAME, Telephony.Mms.Part.CONTENT_LOCATION,
            ),
            "${Telephony.Mms.Part.MSG_ID} = ?", arrayOf(mmsId.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val type = c.getString(1).orEmpty().lowercase()
                val uri = "content://mms/part/${c.getLong(0)}"
                when {
                    type == "text/plain" -> c.getString(2)?.let { if (text.isNotEmpty()) text.append('\n'); text.append(it) }
                    // The layout file that says where things go on screen; nothing to show.
                    type == "application/smil" -> Unit
                    type.startsWith("image/") -> images += uri
                    else -> {
                        val name = listOf(c.getString(3), c.getString(4), c.getString(5)).firstOrNull { !it.isNullOrBlank() }
                            ?: "attachment"
                        files += Attachment(uri, name, type)
                    }
                }
            }
        }
        return Parts(text.toString(), images, files)
    }

    /** Who sent an incoming MMS (address type 137 is "from"). */
    private fun mmsSender(mmsId: Long): String? =
        resolver.query(
            Uri.parse("content://mms/$mmsId/addr"), arrayOf(Telephony.Mms.Addr.ADDRESS),
            "${Telephony.Mms.Addr.TYPE} = 137", null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

    /** Where a not-yet-downloaded message waits at the carrier, for Retry. */
    fun waitingNotice(messageId: Long): Pair<Uri, String>? {
        val uri = Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, (-messageId).toString())
        return resolver.query(uri, arrayOf(Telephony.Mms.CONTENT_LOCATION), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0)?.let { location -> uri to location } else null }
    }

    /** Unread messages in a chat that aren't reactions, oldest first, for its notification. */
    fun unreadTexts(threadId: Long): List<Message> = (unreadSms(threadId) + rawMms(threadId, 50, unreadOnly = true))
        .filter { Reactions.parse(it.body) == null }.sortedBy { it.timeMillis }

    private fun unreadSms(threadId: Long): List<Message> {
        val columns = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        return resolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, columns,
            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(threadId.toString()), "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val body = c.getString(2).orEmpty()
                    if (Reactions.parse(body) == null) add(Message(c.getLong(0), body, c.getLong(3), person(c.getString(1).orEmpty())))
                }
            }
        }.orEmpty()
    }

    /** Saves a text that just arrived. Returns its chat, or null if the store refused it. */
    fun saveIncoming(address: String, body: String, sentMillis: Long, subscription: Int, read: Boolean = false): Long? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, sentMillis)
            put(Telephony.Sms.READ, if (read) 1 else 0)
            put(Telephony.Sms.SEEN, if (read) 1 else 0)
            put(Telephony.Sms.SUBSCRIPTION_ID, subscription)
        }
        val uri = resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) ?: return null
        return threadOf(uri)
    }

    /** Saves a text I'm sending, as waiting in the outbox. */
    fun saveOutgoing(address: String, body: String): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        return resolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
    }

    /** Moves a sent text to Sent, or marks it failed. */
    fun markSent(uri: Uri, ok: Boolean) {
        val values = ContentValues().apply {
            put(Telephony.Sms.TYPE, if (ok) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_FAILED)
        }
        resolver.update(uri, values, null, null)
    }

    /** Deletes a whole chat, texts and picture messages alike. */
    fun deleteChat(threadId: Long): Int =
        resolver.delete(Uri.withAppendedPath(Telephony.Threads.CONTENT_URI, threadId.toString()), null, null)

    fun markRead(threadId: Long) {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        resolver.update(Telephony.Sms.Inbox.CONTENT_URI, values, "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(threadId.toString()))
        resolver.update(Telephony.Mms.Inbox.CONTENT_URI, values, "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0", arrayOf(threadId.toString()))
    }

    fun threadOf(uri: Uri): Long? =
        resolver.query(uri, arrayOf(Telephony.Sms.THREAD_ID), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else null }

    /** The people in a chat. */
    fun people(threadId: Long): List<Person> = chats().firstOrNull { it.threadId == threadId }?.people.orEmpty()

    fun person(address: String): Person = Person(address, name(address))

    private fun name(address: String): String? = names.getOrPut(address) {
        if (address.isBlank()) return@getOrPut null
        try {
            val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
            resolver.query(uri, arrayOf(PhoneLookup.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            // No contacts access: show the number.
            null
        }
    }

    private fun address(recipientId: Long): String? =
        resolver.query(Uri.parse("content://mms-sms/canonical-address/$recipientId"), null, null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow("address")) else null }

    private fun unreadByThread(): Map<Long, Int> {
        val counts = mutableMapOf<Long, Int>()
        resolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.BODY), "${Telephony.Sms.READ} = 0", null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (Reactions.parse(c.getString(1).orEmpty()) == null) counts.merge(c.getLong(0), 1, Int::plus)
            }
        }
        resolver.query(Telephony.Mms.Inbox.CONTENT_URI, arrayOf(Telephony.Mms.THREAD_ID), "${Telephony.Mms.READ} = 0", null, null)?.use { c ->
            while (c.moveToNext()) counts.merge(c.getLong(0), 1, Int::plus)
        }
        return counts
    }

    /** The chat list shows a reaction as "❤️ to “…”", not the raw text. */
    private fun shownSnippet(snippet: String): String =
        Reactions.parse(snippet)?.let(Folding::orphanText) ?: snippet.ifBlank { "📷 Picture" }

    private companion object {
        /** MMS message type 130: a notice that a message is waiting at the carrier. */
        const val PduHeadersNotification = 130
    }
}
