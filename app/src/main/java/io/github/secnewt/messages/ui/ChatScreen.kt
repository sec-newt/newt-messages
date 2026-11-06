package io.github.secnewt.messages.ui

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.text.withLink
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import io.github.secnewt.messages.chats.Links
import androidx.compose.runtime.setValue
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
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
    /** Shown in place of the message box when this chat can't be sent to yet. */
    cantSend: String? = null,
    /** Fetch a picture or group message that didn't download. */
    onDownload: (Message) -> Unit = {},
    /** Attach something to send; [attached] says what's attached, if anything. */
    onAttach: ((AttachKind) -> Unit)? = null,
    attached: String? = null,
    onRemoveAttachment: () -> Unit = {},
    /** Keep a copy of a picture or file: (address, name, type). */
    onSave: ((String, String, String) -> Unit)? = null,
    /** Who's in the chat, renaming, adding or removing people, deleting. */
    onDetails: (() -> Unit)? = null,
    /** React to a message with an emoji (or take it back). */
    onReact: ((Message, String) -> Unit)? = null,
    onDeleteMessage: ((Message) -> Unit)? = null,
    /** Send a message's words and pictures on to another chat. */
    onForward: ((Message) -> Unit)? = null,
    /** A phone number in a message: call it, text it, or add it to contacts. */
    onCallNumber: ((String) -> Unit)? = null,
    onTextNumber: ((String) -> Unit)? = null,
    onAddNumberToContacts: ((String) -> Unit)? = null,
) {
    var acting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Message?>(null) }
    var reactionsOf by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Message?>(null) }
    var selecting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Message?>(null) }
    var number by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    reactionsOf?.let { message -> WhoReactedDialog(message) { reactionsOf = null } }
    selecting?.let { message -> SelectTextDialog(message.body) { selecting = null } }
    number?.let { shown ->
        NumberActionsSheet(
            shown,
            onCall = onCallNumber?.let { call -> { number = null; call(shown) } },
            onText = onTextNumber?.let { text -> { number = null; text(shown) } },
            onAddToContacts = onAddNumberToContacts?.let { add -> { number = null; add(shown) } },
            onDismiss = { number = null },
        )
    }
    acting?.let { message ->
        MessageActionsSheet(
            message,
            onReact = onReact?.let { react -> { emoji: String -> acting = null; react(message, emoji) } },
            onSelect = { acting = null; selecting = message },
            onForward = onForward?.let { forward -> { acting = null; forward(message) } },
            onDelete = onDeleteMessage?.let { delete -> { acting = null; delete(message) } },
            onDismiss = { acting = null },
        )
    }
    var viewing by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    viewing?.let { uri ->
        // The message the picture came in, for reacting to it or forwarding it from full size.
        val message = messages.lastOrNull { uri in it.images }
        PictureViewer(
            uri,
            onClose = { viewing = null },
            onSave = onSave?.let { save -> { save(uri, "picture", "image/jpeg") } },
            onForward = if (message != null && onForward != null) ({ viewing = null; onForward(message) }) else null,
            onReact = if (message != null && onReact != null && io.github.secnewt.messages.reactions.Reactions.canReact(message)) {
                { emoji -> onReact(message, emoji) }
            } else {
                null
            },
            mine = message?.reactions.orEmpty().filter { it.from == null }.map { it.emoji }.toSet(),
        )
    }
    Scaffold(
        topBar = {
            // The screen draws under the status bar, so the top bar steps down below it.
            Row(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { ScreenHeader(chat.title, onBack) }
                if (onDetails != null) {
                    androidx.compose.material3.IconButton(onClick = onDetails, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.Info, contentDescription = if (chat.isGroup) "Group details" else "Chat details")
                    }
                }
            }
        },
        bottomBar = {
            if (cantSend != null) {
                Text(
                    cantSend,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            } else {
                Column {
                    if (attached != null) AttachmentLine(attached, onRemoveAttachment)
                    ComposeBar(draft, onDraftChange, onSend, onAttach, canSend = draft.isNotBlank() || attached != null)
                }
            }
        },
    ) { padding ->
        // The newest message is item 0, at the bottom. When one arrives, the list would stay
        // pinned to the message that was showing and leave the new one just off screen, so:
        // follow new messages when already at the newest end, and always after I send one.
        val list = androidx.compose.foundation.lazy.rememberLazyListState()
        val newest = messages.lastOrNull()
        androidx.compose.runtime.LaunchedEffect(newest?.id) {
            if (newest != null && (newest.mine || list.firstVisibleItemIndex <= 1)) list.animateScrollToItem(0)
        }
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            reverseLayout = true,
        ) {
            items(messages.asReversed(), key = { it.id }) { message ->
                Bubble(
                    message, hue = message.from?.let(chat::hue), showName = chat.isGroup, time = time(message.timeMillis),
                    onDownload = { onDownload(message) }, onOpenPicture = { viewing = it }, onSave = onSave,
                    onOptions = { acting = message },
                    onShowReactions = { reactionsOf = message },
                    onNumber = { number = it },
                )
            }
        }
    }
}

@Composable
private fun Bubble(
    message: Message,
    /** The sender's color in this chat; null for my own messages. */
    hue: io.github.secnewt.messages.chats.PersonHue?,
    showName: Boolean,
    time: String,
    onDownload: () -> Unit,
    onOpenPicture: (String) -> Unit,
    onSave: ((String, String, String) -> Unit)?,
    onOptions: (() -> Unit)? = null,
    onShowReactions: () -> Unit = {},
    onNumber: (String) -> Unit = {},
) {
    // Long-press for react / copy / forward / delete; screen readers get it as an action instead.
    val options = listOfNotNull(
        onOptions?.let { open -> androidx.compose.ui.semantics.CustomAccessibilityAction("Message options") { open(); true } },
        if (message.reactions.isNotEmpty()) androidx.compose.ui.semantics.CustomAccessibilityAction("See who reacted") { onShowReactions(); true } else null,
    ).ifEmpty { null }
    val who = message.from?.shownName
    val color = hue?.outline() ?: MaterialTheme.colorScheme.primary
    val reactions = message.reactions.joinToString(", ") { r -> "${r.emoji} from ${r.from?.shownName ?: "you"}" }
    val spoken = buildString {
        append(if (who == null) "You" else who)
        if (message.images.isNotEmpty()) append(", ${message.images.size} picture${if (message.images.size > 1) "s" else ""}")
        append(": ${message.body}. $time")
        if (reactions.isNotEmpty()) append(". Reactions: $reactions")
    }
    Row(
        modifier = Modifier.fillMaxWidth().then(
            if (message.waiting || message.images.isNotEmpty() || message.files.isNotEmpty() || Links.find(message.body).isNotEmpty()) {
                Modifier.semantics(mergeDescendants = false) { if (options != null) customActions = options }
            } else {
                Modifier.clearAndSetSemantics {
                    contentDescription = spoken
                    if (options != null) customActions = options
                }
            },
        ),
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
                modifier = if (onOptions != null) {
                    Modifier.pointerInput(onOptions) { detectTapGestures(onLongPress = { onOptions() }) }
                } else {
                    Modifier
                },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(2.dp, color),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    message.images.forEach { uri -> MmsImage(uri, onLongPress = onOptions) { onOpenPicture(uri) } }
                    message.files.forEach { file ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📎 ${file.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                            if (onSave != null) {
                                androidx.compose.material3.OutlinedButton(
                                    onClick = { onSave(file.uri, file.name, file.type) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) { Text("Save", style = MaterialTheme.typography.titleMedium) }
                            }
                        }
                    }
                    if (message.body.isNotEmpty()) Text(linked(message.body, onNumber), style = MaterialTheme.typography.bodyLarge)
                    if (message.waiting) {
                        androidx.compose.material3.OutlinedButton(onClick = onDownload, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Download", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (message.reactions.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    message.reactions.groupBy { it.emoji }.forEach { (emoji, list) ->
                        // Tap to see who reacted.
                        Text(
                            if (list.size > 1) "$emoji ${list.size}" else emoji,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClickLabel = "See who reacted", onClick = onShowReactions)
                                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .heightIn(min = 36.dp)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Web and email addresses and phone numbers underlined and tappable. Addresses open in the
 * browser or mail app; a phone number opens [onNumber]'s menu (call, text, copy, add).
 */
@Composable
private fun linked(text: String, onNumber: (String) -> Unit): androidx.compose.ui.text.AnnotatedString {
    val links = Links.find(text)
    if (links.isEmpty()) return androidx.compose.ui.text.AnnotatedString(text)
    val style = androidx.compose.ui.text.TextLinkStyles(
        androidx.compose.ui.text.SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
            fontWeight = FontWeight.SemiBold,
        ),
    )
    return androidx.compose.ui.text.buildAnnotatedString {
        var at = 0
        links.forEach { link ->
            append(text.substring(at, link.start))
            val shown = text.substring(link.start, link.end)
            val number = link.number
            val annotation = if (number != null) {
                androidx.compose.ui.text.LinkAnnotation.Clickable("number:$number", style) { onNumber(shown) }
            } else {
                androidx.compose.ui.text.LinkAnnotation.Url(link.target, style)
            }
            withLink(annotation) { append(shown) }
            at = link.end
        }
        append(text.substring(at))
    }
}

/** A picture from the message store, shrunk to fit; a plain label until it loads. Tap for full size, hold for options. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MmsImage(uri: String, onLongPress: (() -> Unit)? = null, onOpen: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val image by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uri) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Pictures.thumbnail(context, uri) }
    }
    val loaded = image
    if (loaded != null) {
        androidx.compose.foundation.Image(
            loaded, contentDescription = "Picture",
            modifier = Modifier
                .widthIn(max = 260.dp)
                .heightIn(max = 320.dp)
                .combinedClickable(
                    onClickLabel = "See it full size",
                    onLongClickLabel = if (onLongPress != null) "Message options" else null,
                    onLongClick = onLongPress,
                    onClick = onOpen,
                ),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
    } else {
        Text("📷 Picture", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AttachmentLine(label: String, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Text("📷 $label", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        androidx.compose.material3.TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Remove", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ComposeBar(draft: String, onDraftChange: (String) -> Unit, onSend: () -> Unit, onAttach: ((AttachKind) -> Unit)?, canSend: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (onAttach != null) {
            var choosing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            Box {
                androidx.compose.material3.IconButton(onClick = { choosing = true }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "Attach")
                }
                androidx.compose.material3.DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                    AttachKind.entries.forEach { kind ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(kind.label, style = MaterialTheme.typography.titleMedium) },
                            onClick = {
                                choosing = false
                                onAttach(kind)
                            },
                            modifier = Modifier.heightIn(min = 56.dp),
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = { Text("Message") },
            colors = neonFieldColors(),
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        )
        FilledIconButton(onClick = onSend, enabled = canSend, modifier = Modifier.size(56.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}

/** What you can do with one message: react, copy or pick out its words, forward it, or delete it. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionsSheet(
    message: Message,
    onReact: ((String) -> Unit)?,
    onSelect: () -> Unit,
    onForward: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var confirmDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onReact != null && io.github.secnewt.messages.reactions.Reactions.canReact(message)) {
                Text("React", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ReactionRow(message.reactions.filter { it.from == null }.map { it.emoji }.toSet(), onReact)
            }
            if (message.body.isNotBlank() && !message.waiting) {
                androidx.compose.material3.TextButton(
                    onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(message.body))
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text("Copy text", style = MaterialTheme.typography.titleMedium) }
                androidx.compose.material3.TextButton(onClick = onSelect, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("Select text", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (onForward != null && !message.waiting && (message.body.isNotBlank() || message.images.isNotEmpty() || message.files.isNotEmpty())) {
                androidx.compose.material3.TextButton(onClick = onForward, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("Forward", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (onDelete != null) {
                if (!confirmDelete) {
                    androidx.compose.material3.TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text("Delete this message", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Text("Delete it from this phone? This can't be undone.", style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        androidx.compose.material3.Button(onClick = onDelete, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text("Delete", style = MaterialTheme.typography.titleMedium)
                        }
                        androidx.compose.material3.OutlinedButton(onClick = { confirmDelete = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text("Keep it", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

/** The emoji to react with; ones I've already put on are outlined, and tapping one takes it back. */
@Composable
internal fun ReactionRow(mine: Set<String>, onReact: (String) -> Unit, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        io.github.secnewt.messages.reactions.Reactions.CHOICES.forEach { emoji ->
            val on = emoji in mine
            androidx.compose.material3.IconButton(
                onClick = { onReact(emoji) },
                modifier = Modifier
                    .size(48.dp)
                    .then(if (on) Modifier.border(2.dp, tint, RoundedCornerShape(12.dp)) else Modifier)
                    .semantics { contentDescription = (if (on) "Take back " else "React ") + emoji },
            ) { Text(emoji, style = MaterialTheme.typography.headlineSmall) }
        }
    }
}

/** A phone number from a message: call it, text it, copy it, or add it to contacts. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NumberActionsSheet(
    number: String,
    onCall: (() -> Unit)?,
    onText: (() -> Unit)?,
    onAddToContacts: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(number, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            @Composable
            fun Choice(label: String, onClick: () -> Unit) {
                androidx.compose.material3.TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            }
            onCall?.let { Choice("Call", it) }
            onText?.let { Choice("Text", it) }
            Choice("Copy number") {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(number))
                onDismiss()
            }
            onAddToContacts?.let { Choice("Add to contacts", it) }
        }
    }
}

/**
 * A message's words, big, full screen, to pick out just part of them: hold a word, drag the
 * handles, then Copy from Android's own menu.
 */
@Composable
private fun SelectTextDialog(text: String, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Select text", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Button(onClick = onClose, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text("Done", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text(
                    "Hold a word, then drag the handles to pick what you want.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.text.selection.SelectionContainer(
                    Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                ) {
                    Text(text, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

/** Who put which emoji on a message. */
@Composable
private fun WhoReactedDialog(message: Message, onClose: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Reactions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                message.reactions.groupBy { it.emoji }.forEach { (emoji, list) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(emoji, style = MaterialTheme.typography.headlineSmall)
                        Text(list.joinToString(", ") { it.from?.shownName ?: "You" }, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Close", style = MaterialTheme.typography.titleMedium)
            }
        },
    )
}

/** What can be attached to a message. */
enum class AttachKind(val label: String) {
    MEDIA("Photo or video"),
    CAMERA("Take a photo"),
    FILE("File"),
    CONTACT("Contact card"),
}
