package io.github.secnewt.messages.chats

import android.content.Context

/**
 * Colors I picked for people, for every chat or just one, in this app's private storage. A
 * pick for one chat wins over a pick for every chat. Newt Phone doesn't see these.
 */
class PersonColorStore(context: Context) {

    private val prefs = context.getSharedPreferences("person_colors", Context.MODE_PRIVATE)

    /** What I picked for each person in this chat, by [ChatColors.key]. */
    fun picked(threadId: Long, people: List<Person>): Map<String, PersonHue> = buildMap {
        for (person in people) {
            val key = ChatColors.key(person.address)
            (hue(chatKey(threadId, key)) ?: hue(everyKey(key)))?.let { put(key, it) }
        }
    }

    /** Who's in this chat and their colors, with my picks applied. */
    fun colors(threadId: Long, people: List<Person>): Map<String, PersonHue> =
        ChatColors.assign(people, picked(threadId, people))

    /**
     * Pick [hue] for [person], in just [threadId] or (when null) every chat. A null [hue] goes
     * back to automatic there. Anything for every chat clears their one-chat picks, so it
     * really does show everywhere.
     */
    fun pick(person: Person, hue: PersonHue?, threadId: Long?) {
        val key = ChatColors.key(person.address)
        val edit = prefs.edit()
        if (threadId == null) {
            if (hue == null) edit.remove(everyKey(key)) else edit.putString(everyKey(key), hue.name)
            // Picking for every chat replaces any one-chat picks for this person.
            prefs.all.keys.filter { it.endsWith(":$key") && !it.startsWith("all:") }.forEach(edit::remove)
        } else {
            if (hue == null) edit.remove(chatKey(threadId, key)) else edit.putString(chatKey(threadId, key), hue.name)
        }
        edit.apply()
    }

    private fun hue(key: String): PersonHue? =
        prefs.getString(key, null)?.let { name -> PersonHue.entries.firstOrNull { it.name == name } }

    private fun everyKey(key: String) = "all:$key"
    private fun chatKey(threadId: Long, key: String) = "$threadId:$key"
}
