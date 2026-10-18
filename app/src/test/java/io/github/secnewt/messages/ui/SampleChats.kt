package io.github.secnewt.messages.ui

import io.github.secnewt.messages.chats.Chat
import io.github.secnewt.messages.chats.Message
import io.github.secnewt.messages.chats.Person
import io.github.secnewt.messages.chats.Reaction

/** Made-up chats for screenshots. */
object SampleChats {
    const val HOUR = 3_600_000L
    val becca = Person("+19725550111", "Becca")
    val sam = Person("+12145550122", "Sam")
    val pastor = Person("+14695550133", "Pastor Dave")
    val mom = Person("+19725550144", "Mom")

    val chats = listOf(
        Chat(1, listOf(becca), "Can you grab milk on the way home?", 10 * HOUR, unread = 2, pinned = true),
        Chat(2, listOf(mom), "Love you! Call me Sunday", 9 * HOUR),
        Chat(3, listOf(Person("+12145550155")), "Is this still available?", 8 * HOUR),
        Chat(4, listOf(becca, sam, mom), "Sam: I'll bring the chairs", 11 * HOUR, unread = 1, name = "Family"),
        Chat(5, listOf(pastor, sam, becca), "Pastor Dave: Small group moved to Thursday", 7 * HOUR, name = "Church small group"),
        Chat(6, listOf(Person("262966")), "482913 is your verification code", 6 * HOUR, unread = 1),
        Chat(7, listOf(Person("UPS")), "Your package was delivered", 5 * HOUR),
    )

    val family = chats[3]
    val familyMessages = listOf(
        Message(1, "Who's bringing what Saturday?", 8 * HOUR, mom),
        Message(2, "I can do the brisket", 9 * HOUR, reactions = listOf(Reaction("❤️", mom), Reaction("❤️", becca), Reaction("😂", sam))),
        Message(3, "I'll bring the chairs", 10 * HOUR, sam, reactions = listOf(Reaction("👍", null))),
        Message(4, "Thank you both!", 11 * HOUR, becca),
    )

    val time: (Long) -> String = { "${(it / HOUR - 1) % 12 + 1}:00 ${if (it / HOUR < 12) "AM" else "PM"}" }
}
