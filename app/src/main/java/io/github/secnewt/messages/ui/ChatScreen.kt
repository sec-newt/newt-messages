package io.github.secnewt.messages.ui

import io.github.secnewt.ui.newtStripe
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
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
import io.github.secnewt.messages.chats.SendStatus
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
    /** Reach further back: there are older messages than these. Null when these are all. */
    onShowOlder: (() -> Unit)? = null,
    /** Send one of my messages again after it failed. */
    onRetry: ((Message) -> Unit)? = null,
    /** Attach something to send; [attached] is what's attached so far, shown as small pictures. */
    onAttach: ((AttachKind) -> Unit)? = null,
    attached: List<io.github.secnewt.messages.chats.Attachment> = emptyList(),
    onRemoveAttachment: (io.github.secnewt.messages.chats.Attachment) -> Unit = {},
    /** Keep a copy of a picture or file: (address, name, type). */
    onSave: ((String, String, String) -> Unit)? = null,
    /** Who's in the chat, renaming, adding or removing people, deleting. */
    onDetails: (() -> Unit)? = null,
    /** React to a message with an emoji (or take it back). */
    onReact: ((Message, String) -> Unit)? = null,
    onDeleteMessage: ((Message) -> Unit)? = null,
    /** Copy a sign-in code from a message (marked private, cleared in a couple of minutes). */
    onCopyCode: ((String) -> Unit)? = null,
    /** Send a message's words and pictures on to another chat. */
    onForward: ((Message) -> Unit)? = null,
    /** A phone number in a message: call it, text it, or add it to contacts. */
    onCallNumber: ((String) -> Unit)? = null,
    onTextNumber: ((String) -> Unit)? = null,
    onAddNumberToContacts: ((String) -> Unit)? = null,
    /** Who a message was really from and to, and when (read from the message store). */
    loadDetails: (suspend (Message) -> io.github.secnewt.messages.chats.MessageDetails?)? = null,
    /** Now, for "Today" and "Yesterday" between days; null keeps the phone's clock (checked each minute). */
    now: Long? = null,
) {
    // Deleting a message: it goes from the screen at once, with Undo for a few seconds; only
    // then is it really deleted (or straight away if the chat is closed first).
    var pendingDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(setOf<Long>()) }
    val snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
    val undoScope = androidx.compose.runtime.rememberCoroutineScope()
    val allMessages = androidx.compose.runtime.rememberUpdatedState(messages)
    val committed = androidx.compose.runtime.remember { mutableSetOf<Long>() }
    val deleteNow = androidx.compose.runtime.rememberUpdatedState(onDeleteMessage)
    fun commit(id: Long) {
        if (!committed.add(id)) return
        allMessages.value.firstOrNull { it.id == id }?.let { m -> deleteNow.value?.invoke(m) }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { pendingDelete.forEach(::commit) }
    }
    val deleteWithUndo: ((Message) -> Unit)? = onDeleteMessage?.let {
        { message ->
            pendingDelete = pendingDelete + message.id
            // One Undo at a time: a newer delete settles the one before it.
            snackbar.currentSnackbarData?.dismiss()
            undoScope.launch {
                val result = snackbar.showSnackbar("Message deleted", actionLabel = "Undo", duration = androidx.compose.material3.SnackbarDuration.Long)
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    pendingDelete = pendingDelete - message.id
                } else {
                    commit(message.id)
                }
            }
        }
    }
    @Suppress("NAME_SHADOWING")
    val messages = if (pendingDelete.isEmpty()) messages else messages.filterNot { it.id in pendingDelete }

    // Find in this chat: matching messages, newest first; Older and Newer step through them.
    var finding by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var findQuery by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var findAt by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val found = androidx.compose.runtime.remember(messages, findQuery) {
        val q = findQuery.trim()
        if (q.isEmpty()) emptyList() else messages.filter { it.body.contains(q, ignoreCase = true) }.map { it.id }.asReversed()
    }
    if (findAt >= found.size) findAt = 0
    if (finding) androidx.activity.compose.BackHandler { finding = false; findQuery = "" }
    var acting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Message?>(null) }
    var reactionsOf by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Message?>(null) }
    var selecting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Message?>(null) }
    var number by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    reactionsOf?.let { message -> WhoReactedDialog(message) { reactionsOf = null } }
    selecting?.let { message -> SelectTextDialog(message.body) { selecting = null } }
    var detailsOf by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Message?>(null) }
    detailsOf?.let { message -> loadDetails?.let { load -> MessageDetailsDialog(message, load) { detailsOf = null } } }
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
            onDetails = if (loadDetails != null) ({ acting = null; detailsOf = message }) else null,
            onDelete = deleteWithUndo?.let { delete -> { acting = null; delete(message) } },
            onDismiss = { acting = null },
        )
    }
    var video by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<io.github.secnewt.messages.chats.Attachment?>(null) }
    video?.let { file ->
        VideoPlayer(file, onClose = { video = null }, onSave = onSave?.let { save -> { save(file.uri, file.name, file.type) } })
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
        snackbarHost = {
            androidx.compose.material3.SnackbarHost(snackbar) { data ->
                // Big words and a big Undo, not Material's small ones.
                androidx.compose.material3.Snackbar(
                    action = {
                        data.visuals.actionLabel?.let { label ->
                            androidx.compose.material3.TextButton(onClick = { data.performAction() }, modifier = Modifier.heightIn(min = 56.dp)) {
                                Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.inversePrimary)
                            }
                        }
                    },
                    modifier = Modifier.padding(12.dp),
                ) { Text(data.visuals.message, style = MaterialTheme.typography.bodyLarge) }
            }
        },
        topBar = {
            if (finding) {
                FindBar(
                    findQuery, onQuery = { findQuery = it; findAt = 0 }, count = found.size, at = findAt, more = onShowOlder != null,
                    onOlder = { if (found.isNotEmpty()) findAt = (findAt + 1) % found.size },
                    onNewer = { if (found.isNotEmpty()) findAt = (findAt - 1 + found.size) % found.size },
                    onClose = { finding = false; findQuery = "" },
                )
            } else {
            // The screen draws under the status bar, so the top bar steps down below it. At large
            // text the title gets a line of its own, so it's never squeezed by the buttons.
            val roomy = androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.3f
            val extras: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                if (chat.isGroup) {
                    Text(
                        "${chat.people.size} people",
                        fontFamily = io.github.secnewt.ui.NewtFonts.Mono,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                // Worded, not just a magnifying glass.
                androidx.compose.material3.TextButton(
                    onClick = { finding = true },
                    modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = "Find in this chat" },
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Text("Find", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
                }
                if (onDetails != null) {
                    androidx.compose.material3.IconButton(onClick = onDetails, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.Info, contentDescription = if (chat.isGroup) "Group details" else "Chat details")
                    }
                }
            }
            if (roomy) {
                Row(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { ScreenHeader(chat.title, onBack) }
                    extras()
                }
            } else {
                Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    ScreenHeader(chat.title, onBack)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) { extras() }
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
                    if (attached.isNotEmpty()) AttachmentStrip(attached, onRemoveAttachment)
                    ComposeBar(draft, onDraftChange, onSend, onAttach, canSend = draft.isNotBlank() || attached.isNotEmpty())
                }
            }
        },
    ) { padding ->
        // The newest message is item 0, at the bottom. When one arrives, the list would stay
        // pinned to the message that was showing and leave the new one just off screen, so:
        // follow new messages when already at the newest end, and always after I send one.
        val list = androidx.compose.foundation.lazy.rememberLazyListState()
        val newest = messages.lastOrNull()
        // Sent and Delivered show on my newest message only; Sending and failures show on every one.
        val newestMine = messages.lastOrNull { it.mine }?.id
        // Follow a new message when the one before it was showing (a day line may come in with
        // it, so this goes by which messages are on screen, not by position), and always after I send.
        var lastNewest by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(newest?.id) }
        androidx.compose.runtime.LaunchedEffect(newest?.id) {
            val before = lastNewest
            lastNewest = newest?.id
            if (newest == null || before == newest.id) return@LaunchedEffect
            val wasShowing = before == null || list.layoutInfo.visibleItemsInfo.any { it.key == before }
            if (newest.mine || wasShowing) list.animateScrollToItem(0)
        }
        // The newest message isn't on screen: a "Newest" button to jump down, counting messages that came since.
        val newestId = androidx.compose.runtime.rememberUpdatedState(newest?.id)
        val away by androidx.compose.runtime.remember {
            androidx.compose.runtime.derivedStateOf {
                val shown = list.layoutInfo.visibleItemsInfo
                shown.isNotEmpty() && newestId.value != null && shown.none { it.key == newestId.value }
            }
        }
        var seen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(newest?.id) }
        androidx.compose.runtime.LaunchedEffect(away, newest?.id) { if (!away) seen = newest?.id }
        val unseen = messages.indexOfLast { it.id == seen }.let { at -> if (at < 0) 0 else messages.size - 1 - at }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        // Who sent what, grouped: the name once, the picture and time once, a line between days.
        val clock by androidx.compose.runtime.produceState(now ?: System.currentTimeMillis(), now) {
            if (now == null) while (true) {
                kotlinx.coroutines.delay(60_000)
                value = System.currentTimeMillis()
            }
        }
        // Worked out again only when the messages or the day change, not on every keystroke.
        val today = clock / 3_600_000 // the hour is enough to notice midnight
        val places = androidx.compose.runtime.remember(messages, today) { io.github.secnewt.messages.chats.Timeline.place(messages, clock) }
        // The list's items in order (newest first, day lines after their message), to find where a match is.
        val order = androidx.compose.runtime.remember(messages, places) {
            buildList<Any> {
                for (i in messages.indices.reversed()) {
                    add(messages[i].id)
                    if (places[i].day != null) add("day-${messages[i].id}")
                }
            }
        }
        val current = found.getOrNull(findAt)
        androidx.compose.runtime.LaunchedEffect(current) {
            current?.let { id -> order.indexOf(id).takeIf { it >= 0 }?.let { list.animateScrollToItem(it) } }
        }
        Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            reverseLayout = true,
        ) {
            // Newest first, since the list is drawn bottom-up; a day's line goes after (above) its first message.
            for (i in messages.indices.reversed()) {
                val message = messages[i]
                val place = places[i]
                item(key = message.id) {
                Bubble(
                    message, hue = message.from?.let(chat::hue), showName = chat.isGroup, time = time(message.timeMillis),
                    first = place.first, last = place.last,
                    onDownload = { onDownload(message) }, onOpenPicture = { viewing = it }, onSave = onSave,
                    onOptions = { acting = message },
                    onShowReactions = { reactionsOf = message },
                    onNumber = { number = it },
                    onPlayVideo = { video = it },
                    highlight = findQuery.trim().takeIf { finding && it.isNotEmpty() },
                    onCopyCode = onCopyCode,
                    showStatus = message.id == newestMine || message.status == SendStatus.SENDING || message.status.failed,
                    onRetry = onRetry?.let { retry -> { retry(message) } },
                )
                }
                place.day?.let { day -> item(key = "day-${message.id}") { DayLine(day) } }
            }
            // At the top, above the oldest message shown (the list is drawn bottom-up).
            if (onShowOlder != null) {
                item(key = "older") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.OutlinedButton(onClick = onShowOlder, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text("Show older messages", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
        if (away) {
            JumpToNewest(unseen, Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                scope.launch { list.animateScrollToItem(0) }
            }
        }
        }
    }
}

/** "Today", "Yesterday" or "Mon, Nov 3", centered between two lines where the day changes. */
@Composable
private fun DayLine(day: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp).semantics { heading() },
    ) {
        androidx.compose.material3.HorizontalDivider(Modifier.weight(1f))
        Text(day, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.HorizontalDivider(Modifier.weight(1f))
    }
}

/** Back down to the newest message, saying how many came in while scrolled up. */
@Composable
private fun JumpToNewest(unseen: Int, modifier: Modifier, onClick: () -> Unit) {
    io.github.secnewt.ui.NewtPrimaryButton(
        if (unseen > 0) "$unseen new" else "Newest",
        onClick = onClick,
        icon = Icons.Filled.KeyboardArrowDown,
        modifier = modifier.semantics {
            contentDescription = if (unseen > 0) "Go to newest message, $unseen new" else "Go to newest message"
        },
    )
}

@Composable
private fun Bubble(
    message: Message,
    /** The sender's color in this chat; null for my own messages. */
    hue: io.github.secnewt.messages.chats.PersonHue?,
    showName: Boolean,
    time: String,
    /** First of a run from one person (name shows) and last (picture and time show). */
    first: Boolean = true,
    last: Boolean = true,
    onDownload: () -> Unit,
    onOpenPicture: (String) -> Unit,
    onSave: ((String, String, String) -> Unit)?,
    onOptions: (() -> Unit)? = null,
    onShowReactions: () -> Unit = {},
    onNumber: (String) -> Unit = {},
    onPlayVideo: (io.github.secnewt.messages.chats.Attachment) -> Unit = {},
    /** Words being looked for (Find in this chat), shown loudly where they match. */
    highlight: String? = null,
    onCopyCode: ((String) -> Unit)? = null,
    showStatus: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    val status = message.status.takeIf { showStatus && it != SendStatus.NONE }
    val canRetry = status?.failed == true && onRetry != null
    // Long-press for react / copy / forward / delete; screen readers get it as an action instead.
    val options = listOfNotNull(
        onOptions?.let { open -> androidx.compose.ui.semantics.CustomAccessibilityAction("Message options") { open(); true } },
        if (message.reactions.isNotEmpty()) androidx.compose.ui.semantics.CustomAccessibilityAction("See who reacted") { onShowReactions(); true } else null,
        if (canRetry) androidx.compose.ui.semantics.CustomAccessibilityAction("Try again") { onRetry?.invoke(); true } else null,
    ).ifEmpty { null }
    val who = message.from?.shownName
    // A sign-in code in it, worked out once per message, not on every redraw.
    val code = androidx.compose.runtime.remember(message.body, message.mine) {
        if (message.mine) null else io.github.secnewt.messages.chats.Codes.find(message.body)
    }
    val color = hue?.outline() ?: MaterialTheme.colorScheme.primary
    val reactions = message.reactions.joinToString(", ") { r -> "${r.emoji} from ${r.from?.shownName ?: "you"}" }
    val spoken = buildString {
        append(if (who == null) "You" else who)
        if (message.images.isNotEmpty()) append(", ${message.images.size} picture${if (message.images.size > 1) "s" else ""}")
        append(": ${message.body}. $time")
        if (reactions.isNotEmpty()) append(". Reactions: $reactions")
        if (status != null) append(". ${status.label}")
    }
    Row(
        // The whole row (avatar, bubble and the space beside it) is the long-press target.
        // Grouped messages sit close; a new person or a pause gets room above.
        modifier = Modifier.fillMaxWidth().padding(top = if (first) 12.dp else 3.dp)
            .then(if (onOptions != null) Modifier.pointerInput(onOptions) { detectTapGestures(onLongPress = { onOptions() }) } else Modifier)
            .then(
            // Anything with its own button keeps its parts for screen readers (a sign-in code's Copy, too).
            if (canRetry || message.waiting || message.images.isNotEmpty() || message.files.isNotEmpty() || Links.find(message.body).isNotEmpty() ||
                (onCopyCode != null && code != null)
            ) {
                Modifier.semantics(mergeDescendants = false) { if (options != null) customActions = options }
            } else {
                Modifier.clearAndSetSemantics {
                    contentDescription = spoken
                    if (options != null) customActions = options
                }
            },
        ),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (showName && who != null && hue != null) {
            // Once per run, beside its last message; the others keep its space so they line up.
            if (last) Avatar(who, message.from?.photo, size = 36, color = hue.fill) else Spacer(Modifier.width(36.dp))
        }
        Column(
            horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            // Mine: the solid gradient, so "me vs. them" reads from across the room. Theirs: a stripe
            // of their color down the edge, and in groups their name inside the bubble.
            // Grouped bubbles join: the corners where they meet are tighter.
            val joinTop = if (first) 16.dp else 6.dp
            val joinBottom = if (last) 16.dp else 6.dp
            val shape = if (message.mine) {
                RoundedCornerShape(topStart = 16.dp, topEnd = joinTop, bottomEnd = joinBottom, bottomStart = 16.dp)
            } else {
                RoundedCornerShape(topStart = joinTop, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = joinBottom)
            }
            val ink = if (message.mine) io.github.secnewt.ui.onNewtGradient() else MaterialTheme.colorScheme.onSurface
            val dim = if (message.mine) io.github.secnewt.ui.onNewtGradient() else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(shape)
                    .then(
                        if (message.mine) {
                            Modifier.background(io.github.secnewt.ui.newtGradient())
                        } else {
                            Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .newtStripe(color, tint = false)
                        },
                    )
                    .padding(start = if (message.mine) 14.dp else 18.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            ) {
                if (showName && who != null && first) {
                    // On dark, the name is in the person's color. On light those hues can't reach
                    // 7:1 as text, so the name stays dark; the stripe carries the color.
                    Text(
                        who,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (onDarkBackground()) color else MaterialTheme.colorScheme.onSurface,
                    )
                }
                message.images.forEach { uri -> MmsImage(uri, onLongPress = onOptions) { onOpenPicture(uri) } }
                message.files.forEach { file ->
                    // Videos show their first frame and play full screen; sound plays right here.
                    when {
                        file.type.startsWith("video/") -> VideoTile(file, ink, sent = time, onPlay = { onPlayVideo(file) }, onLongPress = onOptions)
                        file.type.startsWith("audio/") -> AudioRow(file, ink, sent = time)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val media = file.type.startsWith("video/") || file.type.startsWith("audio/")
                        if (!media) Text("📎 ${file.name}", style = MaterialTheme.typography.titleMedium, color = ink, modifier = Modifier.weight(1f, fill = false))
                        if (onSave != null) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { onSave(file.uri, file.name, file.type) },
                                // Named, so two files' Save buttons don't sound the same.
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Save ${file.name}, $time" },
                            ) {
                                Text("Save", style = MaterialTheme.typography.titleMedium, color = ink)
                            }
                        }
                    }
                }
                if (message.body.isNotEmpty()) {
                    Text(
                        marked(linked(message.body, onNumber, linkColor = if (message.mine) ink else MaterialTheme.colorScheme.primary), highlight),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 25.sp),
                        color = ink,
                    )
                }
                // A sign-in code someone sent: a button to copy it, right here.
                if (code != null && onCopyCode != null) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onCopyCode(code) },
                        modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = "Copy code ${code.toList().joinToString(" ")}" },
                    ) {
                        Text("Copy $code", style = MaterialTheme.typography.titleMedium, fontFamily = io.github.secnewt.ui.NewtFonts.Mono, color = ink)
                    }
                }
                if (message.waiting) {
                    androidx.compose.material3.OutlinedButton(onClick = onDownload, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Download", style = MaterialTheme.typography.titleMedium, color = ink)
                    }
                }
                // The time shows once per group, and on any message that's still going or didn't go.
                if (last || message.status == SendStatus.SENDING || message.status.failed) Text(time, fontFamily = io.github.secnewt.ui.NewtFonts.Mono, fontSize = 16.sp, color = dim)
            }
            if (status != null) SendStatusLine(status, if (canRetry) onRetry else null)
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
                                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
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
 * Under my message: Sending…, Sent or Delivered quietly; Not sent or Not delivered loudly, in
 * the danger color with a warning sign (not color alone), and a Try again button.
 */
@Composable
private fun SendStatusLine(status: SendStatus, onRetry: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        if (status.failed) {
            Text(
                "⚠ ${status.label}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
            if (onRetry != null) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onRetry,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Try again", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error) }
            }
        } else {
            Text(status.label, fontFamily = io.github.secnewt.ui.NewtFonts.Mono, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Web and email addresses and phone numbers underlined and tappable. Addresses open in the
 * browser or mail app; a phone number opens [onNumber]'s menu (call, text, copy, add).
 */
@Composable
private fun linked(
    text: String,
    onNumber: (String) -> Unit,
    linkColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
): androidx.compose.ui.text.AnnotatedString {
    val links = Links.find(text)
    if (links.isEmpty()) return androidx.compose.ui.text.AnnotatedString(text)
    val style = androidx.compose.ui.text.TextLinkStyles(
        androidx.compose.ui.text.SpanStyle(
            color = linkColor,
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
    // A GIF moves (unless Android's animations are off); anything else is a still picture.
    // One decode either way: the moving one is tried first, and only GIFs and WebPs are.
    val shown by androidx.compose.runtime.produceState<Any?>(null, uri) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Pictures.animated(context, uri) ?: Pictures.thumbnail(context, uri)
        }
    }
    val loaded = shown as? androidx.compose.ui.graphics.ImageBitmap
    val gif = shown as? android.graphics.drawable.AnimatedImageDrawable
    if (gif != null) {
        AnimatedPicture(gif, onOpen = onOpen, onLongPress = onLongPress)
    } else if (loaded != null) {
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

/**
 * What's attached so far, as small pictures (a picture's own thumbnail; a file's name), each
 * with its own Remove button.
 */
@Composable
private fun AttachmentStrip(attached: List<io.github.secnewt.messages.chats.Attachment>, onRemove: (io.github.secnewt.messages.chats.Attachment) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        // No keys: the same file attached twice must not crash the list.
        items(attached) { file -> AttachmentTile(file) { onRemove(file) } }
    }
}

@Composable
private fun AttachmentTile(file: io.github.secnewt.messages.chats.Attachment, onRemove: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val picture = file.type.startsWith("image/") || file.type.startsWith("video/")
    val image by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file.uri, file.type) {
        // Start empty: this slot may have shown a different attachment before.
        value = null
        if (picture) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Pictures.preview(context, file.uri, file.type) }
    }
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.width(112.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(112.dp).clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(io.github.secnewt.ui.neonOutline(), shape),
        ) {
            val loaded = image
            if (loaded != null) {
                androidx.compose.foundation.Image(
                    loaded, contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (file.type.startsWith("video/")) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape))
                }
            } else {
                Text(
                    (if (file.type.startsWith("text/x-vcard") || file.type.startsWith("text/vcard")) "👤 " else "📎 ") + file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        // A worded button under each one, so it's clear which one comes off.
        androidx.compose.material3.TextButton(
            onClick = onRemove,
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Remove ${file.name}" },
        ) {
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
            modifier = Modifier.weight(1f).heightIn(min = 58.dp),
        )
        // The gradient send button; outlined and dim until there's something to send.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (canSend) {
                        Modifier.background(io.github.secnewt.ui.newtGradient())
                    } else {
                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    },
                )
                .clickable(enabled = canSend, role = androidx.compose.ui.semantics.Role.Button, onClick = onSend)
                .semantics { contentDescription = "Send" },
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = if (canSend) io.github.secnewt.ui.onNewtGradient() else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What you can do with one message: react, copy or pick out its words, forward it, see its details, or delete it. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionsSheet(
    message: Message,
    onReact: ((String) -> Unit)?,
    onSelect: () -> Unit,
    onForward: (() -> Unit)?,
    onDetails: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
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
            if (onDetails != null) {
                androidx.compose.material3.TextButton(onClick = onDetails, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("Details", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (onDelete != null) {
                // No "are you sure?": it can be undone for a few seconds afterwards.
                androidx.compose.material3.TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("Delete this message", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
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
                SelectableText(text, Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState()))
            }
        }
    }
}

/**
 * Words to pick from, with what's picked shown loudly: solid color behind, dark words on it
 * (8:1 or better), not Android's faint see-through tint. A read-only text field, so it knows
 * the selection and can color it; Android's Copy menu comes up as usual.
 */
@Composable
internal fun SelectableText(
    text: String,
    modifier: Modifier = Modifier,
    picked: androidx.compose.ui.text.TextRange = androidx.compose.ui.text.TextRange.Zero,
) {
    var value by androidx.compose.runtime.remember(text) {
        androidx.compose.runtime.mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(text, picked))
    }
    val loud = androidx.compose.ui.text.SpanStyle(
        color = MaterialTheme.colorScheme.onPrimary,
        background = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
    val selection = value.selection
    val highlight = androidx.compose.ui.text.input.VisualTransformation { shown ->
        val styled = if (selection.collapsed) {
            shown
        } else {
            androidx.compose.ui.text.AnnotatedString.Builder(shown).apply { addStyle(loud, selection.min, selection.max) }.toAnnotatedString()
        }
        androidx.compose.ui.text.input.TransformedText(styled, androidx.compose.ui.text.input.OffsetMapping.Identity)
    }
    // The handles in the same color; the built-in tint is off so it can't wash over the dark words.
    val colors = androidx.compose.foundation.text.selection.TextSelectionColors(
        handleColor = MaterialTheme.colorScheme.primary,
        backgroundColor = androidx.compose.ui.graphics.Color.Transparent,
    )
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.text.selection.LocalTextSelectionColors provides colors) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            // Read-only: only the selection ever changes.
            onValueChange = { value = it.copy(text = text) },
            readOnly = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
            visualTransformation = highlight,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

/**
 * Who a message was really from and to, and when. For a group message, To and Cc are the full
 * lists it arrived with, so it shows whether it belongs in this chat.
 */
@Composable
private fun MessageDetailsDialog(
    message: Message,
    load: suspend (Message) -> io.github.secnewt.messages.chats.MessageDetails?,
    onClose: () -> Unit,
) {
    val details by androidx.compose.runtime.produceState<io.github.secnewt.messages.chats.MessageDetails?>(null, message.id) { value = load(message) }
    val format = androidx.compose.runtime.remember {
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Details") },
        text = {
            Column(
                Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val shown = details
                if (shown == null) {
                    Text("Loading…", style = MaterialTheme.typography.bodyLarge)
                } else {
                    DetailsPart("Kind", listOf(if (shown.picture) "Picture or group message (MMS)" else "Text message (SMS)"))
                    DetailsPart("From", listOfNotNull(shown.from?.let(::addressee)))
                    DetailsPart("To", shown.to.map(::addressee))
                    if (shown.cc.isNotEmpty()) DetailsPart("Cc", shown.cc.map(::addressee))
                    shown.sentMillis?.let { DetailsPart("Sent", listOf(format.format(java.util.Date(it)))) }
                    DetailsPart(if (shown.from?.me == true) "Saved" else "Received", listOf(format.format(java.util.Date(shown.receivedMillis))))
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

/** "Shilo Thompson · +12545550121", "You · +19725550100", or the number alone. */
private fun addressee(a: io.github.secnewt.messages.chats.Addressee): String {
    val number = a.person.address
    val name = if (a.me) "You" else a.person.name
    return when {
        name != null && number.isNotBlank() && name != number -> "$name · $number"
        name != null -> name
        else -> number.ifBlank { "Unknown" }
    }
}

@Composable
private fun DetailsPart(label: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (lines.isEmpty()) Text("Nobody listed", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
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

/** Find in this chat: what to look for, how many matched, and Older / Newer to step through them. */
@Composable
private fun FindBar(query: String, onQuery: (String) -> Unit, count: Int, at: Int, more: Boolean, onOlder: () -> Unit, onNewer: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
            androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                label = { Text("Find in this chat") },
                singleLine = true,
                colors = neonFieldColors(),
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            androidx.compose.material3.TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("Done", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (query.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    // Only what's loaded is searched; say so when there's more further back.
                    when {
                        count == 0 && more -> "Nothing in these messages. Show older messages to look further back."
                        count == 0 -> "Nothing found"
                        else -> "${at + 1} of $count"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
                )
                if (count > 1) {
                    androidx.compose.material3.OutlinedButton(onClick = onOlder, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text("Older", style = MaterialTheme.typography.titleMedium)
                    }
                    androidx.compose.material3.OutlinedButton(onClick = onNewer, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text("Newer", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

/** [text] with every match of [query] shown loudly: solid accent behind, dark words on it. */
@Composable
private fun marked(text: androidx.compose.ui.text.AnnotatedString, query: String?): androidx.compose.ui.text.AnnotatedString {
    if (query.isNullOrEmpty()) return text
    val loud = androidx.compose.ui.text.SpanStyle(
        color = MaterialTheme.colorScheme.onPrimary,
        background = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
    val builder = androidx.compose.ui.text.AnnotatedString.Builder(text)
    var from = 0
    while (true) {
        val at = text.text.indexOf(query, from, ignoreCase = true)
        if (at < 0) break
        builder.addStyle(loud, at, at + query.length)
        from = at + query.length
    }
    return builder.toAnnotatedString()
}
