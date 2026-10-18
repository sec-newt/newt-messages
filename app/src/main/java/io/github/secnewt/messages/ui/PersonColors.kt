package io.github.secnewt.messages.ui

import androidx.compose.ui.graphics.Color

/**
 * Tokyo Night accents, one per person, picked from their name the same way Newt Phone
 * does (its `contactColor`), so a person has the same color in both apps. Dark text on each
 * stays above 7:1.
 */
private val PersonColors = listOf(
    Color(0xFF7AA2F7), Color(0xFFBB9AF7), Color(0xFF7DCFFF), Color(0xFF9ECE6A),
    Color(0xFFFF9E64), Color(0xFFF7768E), Color(0xFFE0AF68), Color(0xFF73DACA),
)

val OnPersonColor = Color(0xFF0B0C10)

fun personColor(name: String): Color {
    var hash = 0
    for (c in name.lowercase()) hash = hash * 31 + c.code
    return PersonColors[Math.floorMod(hash, PersonColors.size)]
}
