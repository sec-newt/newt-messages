package io.github.secnewt.messages.sms

import android.content.ContentValues
import android.content.Context
import android.net.Uri
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
    // Shared by the screens' background loads, so these caches must be safe across threads.
    private val known = java.util.concurrent.ConcurrentHashMap<String, Person>()
    private val groupNames = GroupNames(context)
    private val chatSettings = io.github.secnewt.messages.settings.SettingsStore(context)

    /** Every chat, newest first. */
    fun chats(): List<Chat> {
        val threads = Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build()
        val columns = arrayOf(Telephony.Threads._ID, Telephony.Threads.DATE, Telephony.Threads.RECIPIENT_IDS, Telephony.Threads.SNIPPET)
        val unread = unreadByThread()
        val addresses = canonicalAddresses()
        return resolver.query(threads, columns, null, null, "${Telephony.Threads.DATE} DESC")?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val people = c.getString(2).orEmpty().split(' ').mapNotNull { it.toLongOrNull()?.let(addresses::get) }.map(::person)
                    if (people.isEmpty()) continue
                    val snippet = c.getString(3).orEmpty()
                    add(Chat(id, people, shownSnippet(snippet), c.getLong(1), unread = unread[id] ?: 0, name = groupNames.name(id), pinned = chatSettings.pinned(id), muted = chatSettings.muted(id)))
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
        var where = "${Telephony.Mms.THREAD_ID} = ? AND $REAL_MMS"
        if (unreadOnly) where += " AND ${Telephony.Mms.READ} = 0 AND ${Telephony.Mms.MESSAGE_BOX} = ${Telephony.Mms.MESSAGE_BOX_INBOX}"
        data class Row(val id: Long, val date: Long, val incoming: Boolean, val waiting: Boolean)
        val rows = resolver.query(Telephony.Mms.CONTENT_URI, columns, where, arrayOf(threadId.toString()), "${Telephony.Mms.DATE} DESC LIMIT $limit")?.use { c ->
            buildList { while (c.moveToNext()) add(Row(c.getLong(0), c.getLong(1), c.getInt(2) == Telephony.Mms.MESSAGE_BOX_INBOX, c.getInt(3) == PduHeadersNotification)) }
        }.orEmpty()
        // Every message's words and files in one query, instead of one per message.
        val allParts = parts(rows.filterNot { it.waiting }.map { it.id })
        return rows.map { row ->
            val id = row.id
            val waiting = row.waiting
            val found = allParts[id] ?: Parts()
            val from = if (row.incoming) person(mmsSender(id).orEmpty()) else null
            Message(
                -id,
                if (waiting) "Picture or group message, not downloaded yet" else found.text,
                row.date * 1000, from, images = found.images, files = found.files, waiting = waiting,
            )
        }.asReversed()
    }

    private data class Parts(val text: String = "", val images: List<String> = emptyList(), val files: List<Attachment> = emptyList())

    /** Messages' words, pictures and other files, for many messages in one query. */
    private fun parts(mmsIds: List<Long>): Map<Long, Parts> {
        if (mmsIds.isEmpty()) return emptyMap()
        val text = mutableMapOf<Long, StringBuilder>()
        val images = mutableMapOf<Long, MutableList<String>>()
        val files = mutableMapOf<Long, MutableList<Attachment>>()
        // In chunks, so the query never gets too long for SQLite.
        mmsIds.chunked(400).forEach { chunk ->
            resolver.query(
                Uri.parse("content://mms/part"),
                arrayOf(
                    Telephony.Mms.Part.MSG_ID, Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.TEXT,
                    Telephony.Mms.Part.FILENAME, Telephony.Mms.Part.NAME, Telephony.Mms.Part.CONTENT_LOCATION,
                ),
                "${Telephony.Mms.Part.MSG_ID} IN (${chunk.joinToString(",")})", null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val mid = c.getLong(0)
                    val type = c.getString(2).orEmpty().lowercase()
                    val uri = "content://mms/part/${c.getLong(1)}"
                    when {
                        type == "text/plain" -> c.getString(3)?.let { words ->
                            val b = text.getOrPut(mid) { StringBuilder() }
                            if (b.isNotEmpty()) b.append('\n')
                            b.append(words)
                        }
                        // The layout file that says where things go on screen; nothing to show.
                        type == "application/smil" -> Unit
                        type.startsWith("image/") -> images.getOrPut(mid) { mutableListOf() } += uri
                        else -> {
                            val name = listOf(c.getString(4), c.getString(5), c.getString(6)).firstOrNull { !it.isNullOrBlank() } ?: "attachment"
                            files.getOrPut(mid) { mutableListOf() } += Attachment(uri, name, type)
                        }
                    }
                }
            }
        }
        return mmsIds.associateWith { Parts(text[it]?.toString().orEmpty(), images[it].orEmpty(), files[it].orEmpty()) }
    }

    /** Who sent an incoming MMS (address type 137 is "from"). */
    private val senders = java.util.concurrent.ConcurrentHashMap<Long, String>()

    private fun mmsSender(mmsId: Long): String? = senders[mmsId] ?: run {
        resolver.query(
            Uri.parse("content://mms/$mmsId/addr"), arrayOf(Telephony.Mms.Addr.ADDRESS),
            "${Telephony.Mms.Addr.TYPE} = 137", null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }?.also { senders[mmsId] = it }
    }

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

    /** Messages containing [query], newest first: texts, and the words in picture messages. */
    fun search(query: String, limit: Int = 50): List<io.github.secnewt.messages.chats.SearchHit> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        // SQL LIKE with the user's words escaped, so % and _ are just characters.
        val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        val hits = mutableListOf<io.github.secnewt.messages.chats.SearchHit>()
        resolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.BODY} LIKE ? ESCAPE '\\'", arrayOf(like), "${Telephony.Sms.DATE} DESC LIMIT $limit",
        )?.use { c ->
            while (c.moveToNext()) {
                val body = c.getString(1).orEmpty()
                if (Reactions.parse(body) == null) hits += io.github.secnewt.messages.chats.SearchHit(c.getLong(0), body, c.getLong(2))
            }
        }
        resolver.query(
            Uri.parse("content://mms/part"), arrayOf(Telephony.Mms.Part.MSG_ID, Telephony.Mms.Part.TEXT),
            "${Telephony.Mms.Part.CONTENT_TYPE} = 'text/plain' AND ${Telephony.Mms.Part.TEXT} LIKE ? ESCAPE '\\'", arrayOf(like), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val body = c.getString(1).orEmpty()
                if (Reactions.parse(body) != null) continue
                resolver.query(
                    Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, c.getLong(0).toString()),
                    arrayOf(Telephony.Mms.THREAD_ID, Telephony.Mms.DATE), null, null, null,
                )?.use { m -> if (m.moveToFirst()) hits += io.github.secnewt.messages.chats.SearchHit(m.getLong(0), body, m.getLong(1) * 1000) }
            }
        }
        return hits.sortedByDescending { it.timeMillis }.take(limit)
    }

    /** Deletes one message (MMS messages have negative ids here). */
    fun deleteMessage(id: Long): Int =
        if (id < 0) resolver.delete(Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, (-id).toString()), null, null)
        else resolver.delete(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString()), null, null)

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

    fun person(address: String): Person = known.getOrPut(address) {
        if (address.isBlank()) return@getOrPut Person(address)
        contacts()[numberKey(address)]?.let { c -> Person(address, c.name, c.photo, c.contact) } ?: Person(address)
    }

    private class Saved(val name: String, val photo: String?, val contact: String?)

    // Every saved number, read once in a single query (not one lookup per chat).
    @Volatile private var savedNumbers: Map<String, Saved>? = null

    private fun contacts(): Map<String, Saved> = savedNumbers ?: try {
        resolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                android.provider.ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                android.provider.ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ),
            null, null, null,
        )?.use { c ->
            buildMap {
                while (c.moveToNext()) {
                    val number = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val contact = android.provider.ContactsContract.Contacts.getLookupUri(c.getLong(3), c.getString(4))?.toString()
                    putIfAbsent(numberKey(number), Saved(name, c.getString(2), contact))
                }
            }
        }.orEmpty()
    } catch (e: SecurityException) {
        // No contacts access: numbers are shown as they are.
        emptyMap()
    }.also { savedNumbers = it }

    /** Contacts may have changed (an edit, a new contact): read them again next time. */
    fun refreshContacts() {
        savedNumbers = null
        known.clear()
    }

    /** One chat's phone numbers, by their ids in the message store, in a single query. */
    private fun canonicalAddresses(): Map<Long, String> =
        resolver.query(Uri.parse("content://mms-sms/canonical-addresses"), null, null, null, null)?.use { c ->
            val id = c.getColumnIndexOrThrow("_id")
            val address = c.getColumnIndexOrThrow("address")
            buildMap { while (c.moveToNext()) c.getString(address)?.let { put(c.getLong(id), it) } }
        }.orEmpty()

    private fun unreadByThread(): Map<Long, Int> {
        val counts = mutableMapOf<Long, Int>()
        resolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.BODY), "${Telephony.Sms.READ} = 0", null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (Reactions.parse(c.getString(1).orEmpty()) == null) counts.merge(c.getLong(0), 1, Int::plus)
            }
        }
        resolver.query(Telephony.Mms.Inbox.CONTENT_URI, arrayOf(Telephony.Mms.THREAD_ID), "${Telephony.Mms.READ} = 0 AND $REAL_MMS", null, null)?.use { c ->
            while (c.moveToNext()) counts.merge(c.getLong(0), 1, Int::plus)
        }
        return counts
    }

    /** The chat list shows a reaction as "❤️ to “…”", not the raw text. */
    private fun shownSnippet(snippet: String): String =
        Reactions.parse(snippet)?.let(Folding::orphanText) ?: snippet.ifBlank { "📷 Picture" }

    /**
     * How many unread messages show in the chat list, all chats together. Newt Home shows the
     * same number, so the two always agree: messages in a chat with no readable number, and
     * delivery or read receipts, aren't counted.
     */
    fun unreadCount(): Int {
        val unread = unreadByThread()
        if (unread.isEmpty()) return 0
        val addresses = canonicalAddresses()
        val threads = Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build()
        return resolver.query(threads, arrayOf(Telephony.Threads._ID, Telephony.Threads.RECIPIENT_IDS), null, null, null)?.use { c ->
            var total = 0
            while (c.moveToNext()) {
                val shown = c.getString(1).orEmpty().split(' ').any { it.toLongOrNull()?.let(addresses::containsKey) == true }
                if (shown) total += unread[c.getLong(0)] ?: 0
            }
            total
        } ?: 0
    }

    private companion object {
        /** MMS message type 130: a notice that a message is waiting at the carrier. */
        const val PduHeadersNotification = 130

        /**
         * Real messages only: sent (128), waiting (130) and downloaded (132). Not delivery
         * reports (134) or read reports (136), which Android also files in the inbox.
         */
        val REAL_MMS = "${Telephony.Mms.MESSAGE_TYPE} IN (128, 130, 132)"

        /** Numbers compare by their last 10 digits, so "+1 (972) 555-0111" matches "9725550111". */
        fun numberKey(number: String): String {
            val digits = number.filter { it.isDigit() }
            return if (digits.length >= 10) digits.takeLast(10) else number.trim().lowercase()
        }
    }
}
