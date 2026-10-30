package io.github.secnewt.messages.chats

import android.content.Context

/**
 * What I was typing in each chat, kept when I leave it so it's there when I come back. Only
 * the words: a picked photo or file can't be kept, since Android's permission to read it ends
 * with the screen. They live in this app's private storage, on this phone only.
 */
class Drafts(context: Context) {

    private val prefs = context.getSharedPreferences("drafts", Context.MODE_PRIVATE)

    fun draft(threadId: Long): String? = prefs.getString(threadId.toString(), null)

    /** Nothing but spaces counts as no draft. */
    fun keep(threadId: Long, text: String) {
        prefs.edit().apply { if (text.isBlank()) remove(threadId.toString()) else putString(threadId.toString(), text) }.apply()
    }

    fun clear(threadId: Long) = keep(threadId, "")
}
