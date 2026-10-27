package io.github.secnewt.messages.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.secnewt.messages.chats.PersonHue

val OnPersonColor = Color(0xFF0B0C10)

/** Behind a person's initial, with [OnPersonColor] text: the bright hue in both themes. */
val PersonHue.fill: Color get() = Color(dark)

/** For a person's bubble outline: bright on dark, the deeper Day hue on light. */
@Composable
@ReadOnlyComposable
fun PersonHue.outline(): Color = if (onDarkBackground()) Color(dark) else Color(light)

/**
 * The color a name picks on its own, the same way Newt Phone does (its `contactColor`), so a
 * person has the same color in both apps unless a chat needs to tell them apart.
 */
fun personColor(name: String): Color = PersonHue.fromName(name).fill

/** Whether the theme's background is dark. */
@Composable
@ReadOnlyComposable
fun onDarkBackground(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f
