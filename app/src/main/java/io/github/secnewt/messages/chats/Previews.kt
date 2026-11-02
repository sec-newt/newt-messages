package io.github.secnewt.messages.chats

import io.github.secnewt.messages.reactions.Folding
import io.github.secnewt.messages.reactions.Reactions

/**
 * The line under a chat's name in the chat list. Android keeps a summary of each chat, but for
 * picture and group messages it stores the (almost always empty) subject, so a chat with any
 * picture message in it could show "📷 Picture" long after newer texts. The newest message
 * itself is used instead whenever that summary is empty.
 */
object Previews {

    /** Android's summary, when it has words in it; otherwise null, and use [of] the newest message. */
    fun fromSummary(summary: String): String? =
        Reactions.parse(summary)?.let(Folding::orphanText) ?: summary.takeIf { it.isNotBlank() }

    /** What the newest message says: its words, or what it carries when it has none. */
    fun of(message: Message?): String = when {
        message == null -> "📷 Picture"
        message.body.isNotBlank() -> Reactions.parse(message.body)?.let(Folding::orphanText) ?: message.body
        message.images.isNotEmpty() -> if (message.images.size == 1) "📷 Picture" else "📷 ${message.images.size} pictures"
        message.files.isNotEmpty() -> "📎 ${message.files.first().name}"
        else -> "📷 Picture"
    }
}
