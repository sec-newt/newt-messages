package io.github.secnewt.messages.settings

import android.content.Context

/** How a reaction notifies. */
enum class ReactionNotify(val label: String, val explanation: String) {
    QUIET("As a reaction", "A quiet notification like \"Becca ❤️ your message\""),
    OFF("Don't notify", "Reactions just show on the message"),
    LIKE_A_TEXT("Like a text", "With sound, like any message"),
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
        )
    }

    fun save(settings: MessagesSettings) {
        prefs.edit()
            .putString("reactions", settings.reactions.name)
            .putString("speak", settings.speak.name)
            .putBoolean("quiet_during_dnd", settings.quietDuringDnd)
            .apply()
    }

    fun pinned(threadId: Long): Boolean = threadId.toString() in prefs.getStringSet("pinned", emptySet()).orEmpty()
    fun setPinned(threadId: Long, on: Boolean) = toggle("pinned", threadId, on)

    fun muted(threadId: Long): Boolean = threadId.toString() in prefs.getStringSet("muted", emptySet()).orEmpty()
    fun setMuted(threadId: Long, on: Boolean) = toggle("muted", threadId, on)

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

    /** "Message from Becca", or the number read digit by digit. */
    fun text(name: String?, number: String, group: String?): String {
        val who = name ?: number.filter { it.isDigit() }.let { d ->
            if (d.length == 10) listOf(d.substring(0, 3), d.substring(3, 6), d.substring(6)).joinToString(", ") { it.toList().joinToString(" ") }
            else d.toList().joinToString(" ")
        }
        return if (group != null) "Message from $who, in $group" else "Message from $who"
    }
}
