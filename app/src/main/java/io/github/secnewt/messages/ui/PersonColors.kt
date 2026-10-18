package io.github.secnewt.messages.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Tokyo Night accents, one per person, picked from their name the same way Newt Phone
 * does (its `contactColor`), so a person has the same color in both apps. Dark text on each
 * stays above 7:1.
 */
private val PersonColors = listOf(
    Color(0xFF7AA2F7), Color(0xFFBB9AF7), Color(0xFF7DCFFF), Color(0xFF9ECE6A),
    Color(0xFFFF9E64), Color(0xFFF7768E), Color(0xFFE0AF68), Color(0xFF73DACA),
)

/**
 * The same eight hues from Tokyo Night Day, for outlines on a light background (the bright
 * ones above are too pale on white). Same order, so a person keeps their hue.
 */
private val PersonColorsOnLight = listOf(
    Color(0xFF2E7DE9), Color(0xFF9854F1), Color(0xFF007197), Color(0xFF587539),
    Color(0xFFB15C00), Color(0xFFF52A65), Color(0xFF8C6C3E), Color(0xFF118C74),
)

val OnPersonColor = Color(0xFF0B0C10)

private fun index(name: String): Int {
    var hash = 0
    for (c in name.lowercase()) hash = hash * 31 + c.code
    return Math.floorMod(hash, PersonColors.size)
}

/** Behind a person's initial, with [OnPersonColor] text: the bright hue in both themes. */
fun personColor(name: String): Color = PersonColors[index(name)]

/** For a person's bubble outline: bright on dark, the deeper Day hue on light. */
@Composable
@ReadOnlyComposable
fun personOutline(name: String): Color =
    if (onDarkBackground()) PersonColors[index(name)] else PersonColorsOnLight[index(name)]

/** Whether the theme's background is dark. */
@Composable
@ReadOnlyComposable
fun onDarkBackground(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f
