package io.github.secnewt.messages.settings

import android.content.Context

/** How a reaction notifies. */
enum class ReactionNotify(val label: String, val explanation: String) {
    QUIET("As a reaction", "A quiet notification like \"Becca ❤️ your message\""),
    OFF("Don't notify", "Reactions just show on the message"),
    LIKE_A_TEXT("Like a text", "With sound, like any message"),
}

/** What a notification shows while the phone is locked. */
enum class LockScreen(val label: String, val explanation: String) {
    SHOW("Show messages", "Who it's from and what it says, as far as Android's own lock screen setting allows"),
    HIDE_WORDS("Hide what they say", "Who it's from, but not the words or pictures"),
    HIDE_ALL("Hide everything", "Nothing on the lock screen; unlock to see"),
}

/** Saying who a message is from, out loud. */
enum class SpeakMode(val label: String, val explanation: String) {
    OFF("Off", "Never speak"),
    ALWAYS("Always", "\"Message from Becca\", when the ringer is on or headphones are in"),
    HEADPHONES_ONLY("Headphones only", "Only when headphones are connected"),
}

data class MessagesSettings(
    val reactions: ReactionNotify = ReactionNotify.QUIET,
    val speak: SpeakMode = SpeakMode.OFF,
    /** Stay quiet during Do Not Disturb, even with headphones. */
    val quietDuringDnd: Boolean = true,
    /** Read the message out after who it's from (never a sign-in code). */
    val readMessage: Boolean = false,
    /** What notifications show on the lock screen. */
    val lockScreen: LockScreen = LockScreen.SHOW,
)

/** The app's settings and each chat's own (pinned, muted), in its private storage. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun settings(): MessagesSettings {
        val defaults = MessagesSettings()
        return MessagesSettings(
            reactions = enumOr(prefs.getString("reactions", null), defaults.reactions),
            speak = enumOr(prefs.getString("speak", null), defaults.speak),
            quietDuringDnd = prefs.getBoolean("quiet_during_dnd", defaults.quietDuringDnd),
            readMessage = prefs.getBoolean("read_message", defaults.readMessage),
            lockScreen = enumOr(prefs.getString("lock_screen", null), defaults.lockScreen),
        )
    }

    fun save(settings: MessagesSettings) {
        prefs.edit()
            .putString("reactions", settings.reactions.name)
            .putString("speak", settings.speak.name)
            .putBoolean("quiet_during_dnd", settings.quietDuringDnd)
            .putBoolean("read_message", settings.readMessage)
            .putString("lock_screen", settings.lockScreen.name)
            .apply()
    }

    fun pinned(threadId: Long): Boolean = threadId.toString() in prefs.getStringSet("pinned", emptySet()).orEmpty()
    fun setPinned(threadId: Long, on: Boolean) = toggle("pinned", threadId, on)

    fun muted(threadId: Long): Boolean = threadId.toString() in prefs.getStringSet("muted", emptySet()).orEmpty()
    fun setMuted(threadId: Long, on: Boolean) = toggle("muted", threadId, on)

    /**
     * Archived chats, out of the tabs until someone writes again: a chat stays archived only
     * while its newest message is no newer than when it was archived.
     */
    fun archived(threadId: Long, lastTimeMillis: Long): Boolean =
        Archive.isArchived(archivedAt(threadId), lastTimeMillis)

    fun setArchived(threadId: Long, on: Boolean, now: Long = System.currentTimeMillis()) {
        val kept = prefs.getStringSet("archived", emptySet()).orEmpty().filterNot { it.substringBefore(':') == threadId.toString() }.toMutableSet()
        if (on) kept += "$threadId:$now"
        prefs.edit().putStringSet("archived", kept).apply()
    }

    private fun archivedAt(threadId: Long): Long? = prefs.getStringSet("archived", emptySet()).orEmpty()
        .firstOrNull { it.substringBefore(':') == threadId.toString() }?.substringAfter(':')?.toLongOrNull()

    private fun toggle(key: String, threadId: Long, on: Boolean) {
        val set = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        if (on) set += threadId.toString() else set -= threadId.toString()
        prefs.edit().putStringSet(key, set).apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}

/** When to say who a message is from. Kept free of Android so it can be tested. */
object Speaking {
    fun shouldSpeak(mode: SpeakMode, quietDuringDnd: Boolean, ringerOn: Boolean, headphones: Boolean, doNotDisturb: Boolean): Boolean {
        if (mode == SpeakMode.OFF) return false
        if (doNotDisturb && quietDuringDnd) return false
        if (headphones) return true
        return mode == SpeakMode.ALWAYS && ringerOn
    }

    /**
     * What gets said, with the message read out when [body] is given: "Message from Becca:
     * Can you grab milk?". A sign-in code is never read aloud, just called one.
     */
    fun text(name: String?, number: String, group: String?, body: String?): String {
        val who = text(name, number, group)
        val words = body?.trim()?.takeIf { it.isNotEmpty() } ?: return who
        if (io.github.secnewt.messages.chats.Codes.find(words) != null) return "$who: a sign-in code"
        return "$who: " + (if (words.length > 300) words.take(300) + "…" else words)
    }

    /** "Message from Becca", or the number read digit by digit. */
    fun text(name: String?, number: String, group: String?): String {
        val who = name ?: number.filter { it.isDigit() }.let { d ->
            if (d.length == 10) listOf(d.substring(0, 3), d.substring(3, 6), d.substring(6)).joinToString(", ") { it.toList().joinToString(" ") }
            else d.toList().joinToString(" ")
        }
        return if (group != null) "Message from $who, in $group" else "Message from $who"
    }
}

/** Archiving, kept free of Android so it can be tested. */
object Archive {
    /** Archived at [archivedAt]; a message that came after brings the chat back. */
    fun isArchived(archivedAt: Long?, lastTimeMillis: Long): Boolean = archivedAt != null && lastTimeMillis <= archivedAt
}
