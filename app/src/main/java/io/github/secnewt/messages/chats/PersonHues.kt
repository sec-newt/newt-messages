package io.github.secnewt.messages.chats

/**
 * The colors a person can have, as ARGB: [dark] for dark mode (and behind initials in both
 * themes, with dark text above 7:1), [light] for outlines on a light background.
 *
 * The first eight are Newt Phone's, in its order, so a person who keeps the color their name
 * picks has the same one in both apps. The last four only come in when a group needs more.
 * Saved by [name], so the order can change without moving anyone's color.
 */
enum class PersonHue(val label: String, val dark: Long, val light: Long) {
    BLUE("Blue", 0xFF7AA2F7, 0xFF2E7DE9),
    PURPLE("Purple", 0xFFBB9AF7, 0xFF9854F1),
    CYAN("Cyan", 0xFF7DCFFF, 0xFF007197),
    GREEN("Green", 0xFF9ECE6A, 0xFF587539),
    ORANGE("Orange", 0xFFFF9E64, 0xFFB15C00),
    RED("Red", 0xFFF7768E, 0xFFF52A65),
    YELLOW("Yellow", 0xFFE0AF68, 0xFF8C6C3E),
    TEAL("Teal", 0xFF73DACA, 0xFF118C74),
    PINK("Pink", 0xFFFCA7EA, 0xFFB4359A),
    LIME("Lime", 0xFFC3E88D, 0xFF4E7A12),
    LAVENDER("Lavender", 0xFFC0CAF5, 0xFF5A64A8),
    PEACH("Peach", 0xFFFFB59A, 0xFFA8452A),
    ;

    companion object {
        /** My own messages are blue, so nobody else gets blue unless I pick it for them. */
        val MINE = BLUE

        private const val PHONE_COLORS = 8

        /** The color a name picks on its own: the same one Newt Phone gives it. */
        fun fromName(name: String): PersonHue {
            var hash = 0
            for (c in name.lowercase()) hash = hash * 31 + c.code
            return entries[Math.floorMod(hash, PHONE_COLORS)]
        }
    }
}

/**
 * Who gets which color in a chat. Colors I picked stay put; everyone else gets a color nobody
 * else in the chat has (and not my blue), keeping the one their name picks when it's free.
 * Only past eleven other people do colors have to repeat.
 */
object ChatColors {

    /** People match by number, so "+1 (972) 555-0111" and "9725550111" are one person. */
    fun key(address: String): String {
        val digits = address.filter { it.isDigit() }
        return if (digits.length >= 10) digits.takeLast(10) else address.trim().lowercase()
    }

    /** [picked] is the color I chose for someone (by [key]), for this chat or every chat. */
    fun assign(people: List<Person>, picked: Map<String, PersonHue>): Map<String, PersonHue> {
        val result = linkedMapOf<String, PersonHue>()
        val taken = mutableSetOf(PersonHue.MINE)
        // Sorted, so the same group always comes out the same however Android lists it.
        val everyone = people.distinctBy { key(it.address) }.sortedBy { key(it.address) }
        for (person in everyone) {
            picked[key(person.address)]?.let { result[key(person.address)] = it; taken += it }
        }
        val rest = everyone.filter { key(it.address) !in result }
        // Everyone whose own color is free keeps it first, so a clash only moves the later one.
        val bumped = mutableListOf<Person>()
        for (person in rest) {
            val own = PersonHue.fromName(person.shownName)
            if (own in taken) bumped += person else { result[key(person.address)] = own; taken += own }
        }
        for (person in bumped) {
            val own = PersonHue.fromName(person.shownName)
            val free = PersonHue.entries.firstOrNull { it !in taken }
            val hue = free ?: own
            result[key(person.address)] = hue
            taken += hue
        }
        return result
    }
}
