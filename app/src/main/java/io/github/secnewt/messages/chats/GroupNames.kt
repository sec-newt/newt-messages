package io.github.secnewt.messages.chats

import android.content.Context

/**
 * Names I give group chats ("Family"). Android's message store has no place for them, so they
 * live in this app's private storage, on this phone only; other people never see them.
 */
class GroupNames(context: Context) {

    private val prefs = context.getSharedPreferences("group_names", Context.MODE_PRIVATE)

    fun name(threadId: Long): String? = prefs.getString(threadId.toString(), null)

    /** A blank name goes back to listing the people. */
    fun rename(threadId: Long, name: String) {
        val clean = name.trim().take(MAX_LENGTH)
        prefs.edit().apply { if (clean.isEmpty()) remove(threadId.toString()) else putString(threadId.toString(), clean) }.apply()
    }

    companion object {
        const val MAX_LENGTH = 40
    }
}
