package io.github.secnewt.messages.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.secnewt.messages.chats.Chat
import io.github.secnewt.messages.chats.Message
import io.github.secnewt.ui.ScreenHeader
import io.github.secnewt.ui.neonFieldColors

/**
 * One chat. In groups each person's name sits above their message in their own color, and
 * the bubble's outline is that color too (outlines, not filled bubbles, keep text at 7:1).
 * Reactions show under the message they're about, never as messages of their own.
 */
@Composable
fun ChatScreen(
    chat: Chat,
    /** Oldest first, as they read. */
    messages: List<Message>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    time: (Long) -> String,
) {
    Scaffold(
        topBar = { Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { ScreenHeader(chat.title, onBack) } },
        bottomBar = { ComposeBar(draft, onDraftChange, onSend) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            reverseLayout = true,
        ) {
            items(messages.asReversed(), key = { it.id }) { message ->
                Bubble(message, showName = chat.isGroup, time = time(message.timeMillis))
            }
        }
    }
}

@Composable
private fun Bubble(message: Message, showName: Boolean, time: String) {
    val who = message.from?.shownName
    val color = if (who == null) MaterialTheme.colorScheme.primary else personOutline(who)
    val reactions = message.reactions.joinToString(", ") { r -> "${r.emoji} from ${r.from?.shownName ?: "you"}" }
    val spoken = buildString {
        append(if (who == null) "You" else who)
        append(": ${message.body}. $time")
        if (reactions.isNotEmpty()) append(". Reactions: $reactions")
    }
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            if (showName && who != null) {
                // On dark, the name is in the person's color. On light those hues can't reach
                // 7:1 as text, so the name stays dark and a dot of their color sits beside it.
                val dark = onDarkBackground()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!dark) Box(Modifier.size(10.dp).background(color, CircleShape))
                    Text(
                        who,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (dark) color else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(2.dp, color),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(message.body, style = MaterialTheme.typography.bodyLarge)
                    Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (message.reactions.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    message.reactions.groupBy { it.emoji }.forEach { (emoji, list) ->
                        Text(
                            if (list.size > 1) "$emoji ${list.size}" else emoji,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposeBar(draft: String, onDraftChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = { Text("Message") },
            colors = neonFieldColors(),
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        )
        FilledIconButton(onClick = onSend, enabled = draft.isNotBlank(), modifier = Modifier.size(56.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}
