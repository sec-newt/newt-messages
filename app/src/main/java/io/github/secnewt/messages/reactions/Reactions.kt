package io.github.secnewt.messages.reactions

import io.github.secnewt.messages.chats.Message

/** A reaction that arrived as a text: which emoji, on which quoted words, added or removed. */
data class ParsedReaction(val emoji: String, val quoted: String, val removed: Boolean = false)

/**
 * Over SMS and MMS a reaction is just a text. iPhones send `Loved “Let's do lunch”`;
 * Google Messages sends the emoji and the quote wrapped in invisible characters. This turns
 * those back into reactions. The patterns follow QUIK's (GPL-3.0,
 * github.com/quik-sms/quik, `data/src/main/assets/emojis/en.json`).
 */
object Reactions {

    private const val Q = "[“\"](.+?)[”\"]"

    private val IPHONE_ADDED = listOf(
        "❤️" to Regex("^(?s)Loved $Q$"),
        "👍" to Regex("^(?s)Liked $Q$"),
        "👎" to Regex("^(?s)Disliked $Q$"),
        "😂" to Regex("^(?s)Laughed at $Q$"),
        "‼️" to Regex("^(?s)Emphasized $Q$"),
        "❓" to Regex("^(?s)Questioned $Q$"),
    )
    private val IPHONE_REMOVED = listOf(
        "❤️" to Regex("^(?s)Removed a heart from $Q$"),
        "👍" to Regex("^(?s)Removed a like from $Q$"),
        "👎" to Regex("^(?s)Removed a dislike from $Q$"),
        "😂" to Regex("^(?s)Removed a laugh from $Q$"),
        "‼️" to Regex("^(?s)Removed an exclamation from $Q$"),
        "❓" to Regex("^(?s)Removed a question mark from $Q$"),
    )
    private val IPHONE_ANY_ADDED = Regex("^(?s)Reacted (.+?) to $Q$")
    private val IPHONE_ANY_REMOVED = Regex("^(?s)Removed (.+?) from $Q$")

    // Google Messages: hair spaces (U+200A) around the parts; the emoji sits between zero-width
    // spaces (U+200B) when added, or zero-width non-joiners (U+200C) when removed.
    private val GOOGLE_ADDED = Regex("(?s)^ [^​ ]*​([^​]*)​[^​ ]* (.*) [^​ ]* \\Z")
    private val GOOGLE_REMOVED = Regex("(?s)^ [^‌ ]*‌([^‌]*)‌[^‌ ]* (.*) [^‌ ]* \\Z")

    /** The reaction in this text, or null when it's an ordinary message. */
    fun parse(body: String): ParsedReaction? {
        GOOGLE_REMOVED.find(body)?.let { return ParsedReaction(it.groupValues[1], it.groupValues[2], removed = true) }
        GOOGLE_ADDED.find(body)?.let { return ParsedReaction(it.groupValues[1], it.groupValues[2]) }
        for ((emoji, regex) in IPHONE_REMOVED) regex.find(body)?.let { return ParsedReaction(emoji, it.groupValues[1], removed = true) }
        for ((emoji, regex) in IPHONE_ADDED) regex.find(body)?.let { return ParsedReaction(emoji, it.groupValues[1]) }
        IPHONE_ANY_REMOVED.find(body)?.let { return ParsedReaction(it.groupValues[1], it.groupValues[2], removed = true) }
        IPHONE_ANY_ADDED.find(body)?.let {
            // "Reacted with a sticker" has no emoji to show.
            if (it.groupValues[1].startsWith("with ")) return null
            return ParsedReaction(it.groupValues[1], it.groupValues[2])
        }
        return null
    }

    /** How far back to look for the message a reaction quotes. */
    const val SEARCH_LIMIT = 500

    /**
     * The message the reaction is about: the newest one (within [SEARCH_LIMIT]) whose text
     * matches the quote. iPhones cut long quotes short with "…", so a quote ending in "…"
     * matches a message that starts with the rest. Reaction texts themselves never match.
     * [newestFirst] is the chat's messages, newest first.
     */
    fun target(reaction: ParsedReaction, newestFirst: List<Message>): Message? {
        // Google Messages keeps the quote marks inside the quoted part.
        val quote = normalize(reaction.quoted).removeSurrounding("\"")
        val prefix = quote.removeSuffix("…").removeSuffix("...").trimEnd().takeIf { it != quote }
        return newestFirst.asSequence().take(SEARCH_LIMIT)
            .filter { parse(it.body) == null }
            .firstOrNull { message ->
                val body = normalize(message.body)
                body == quote || (prefix != null && prefix.isNotEmpty() && body.startsWith(prefix))
            }
    }

    // Curly and straight quotes and apostrophes, and runs of spaces, compare equal.
    private fun normalize(text: String): String =
        text.replace('’', '\'').replace('‘', '\'').replace('“', '"').replace('”', '"')
            .replace(Regex("\\s+"), " ").trim()

    /**
     * The text to send for a reaction. iPhone wording reads cleanly everywhere, and Google
     * Messages shows it as a reaction too, so it's the default.
     */
    fun iphoneText(emoji: String, quoted: String, removed: Boolean = false): String {
        val word = IPHONE_WORDS[emoji]
        val quote = "“$quoted”"
        return when {
            removed && word != null -> "Removed ${word.second} from $quote"
            removed -> "Removed $emoji from $quote"
            word != null -> "${word.first} $quote"
            else -> "Reacted $emoji to $quote"
        }
    }

    private val IPHONE_WORDS = mapOf(
        "❤️" to ("Loved" to "a heart"),
        "👍" to ("Liked" to "a like"),
        "👎" to ("Disliked" to "a dislike"),
        "😂" to ("Laughed at" to "a laugh"),
        "‼️" to ("Emphasized" to "an exclamation"),
        "❓" to ("Questioned" to "a question mark"),
    )
}
