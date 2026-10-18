package io.github.secnewt.messages.reactions

import io.github.secnewt.messages.chats.Message
import io.github.secnewt.messages.chats.Reaction

/**
 * Turns a chat's raw texts into what's shown: reaction texts disappear and become emoji on
 * the message they quote. Removals take an emoji back off. A reaction whose message can't be
 * found (deleted, or too far back) stays as a short line instead of a raw bubble.
 */
object Folding {

    /** [oldestFirst] is every stored text in the chat. Returns the visible ones, oldest first. */
    fun fold(oldestFirst: List<Message>): List<Message> {
        val shown = mutableListOf<Message>()
        for (message in oldestFirst) {
            val reaction = Reactions.parse(message.body)
            if (reaction == null) {
                shown += message
                continue
            }
            val target = Reactions.target(reaction, shown.asReversed())
            if (target == null) {
                if (!reaction.removed) shown += message.copy(body = orphanText(reaction))
                continue
            }
            val index = shown.indexOf(target)
            val reactions = target.reactions.filterNot { it.emoji == reaction.emoji && it.from == message.from }
            shown[index] = target.copy(
                reactions = if (reaction.removed) reactions else reactions + Reaction(reaction.emoji, message.from),
            )
        }
        return shown
    }

    /** "❤️ to “Let's do lunch”", for a reaction whose message isn't here any more. */
    fun orphanText(reaction: ParsedReaction): String =
        "${reaction.emoji} to “${reaction.quoted.removeSurrounding("\"")}”"
}
