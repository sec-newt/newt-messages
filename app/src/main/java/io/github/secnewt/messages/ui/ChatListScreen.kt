package io.github.secnewt.messages.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.secnewt.messages.chats.Chat
import io.github.secnewt.messages.chats.ChatSorting
import io.github.secnewt.messages.chats.ChatTab
import io.github.secnewt.ui.TabHeader

private val ChatTab.icon: ImageVector
    get() = when (this) {
        ChatTab.PEOPLE -> Icons.Filled.Person
        ChatTab.GROUPS -> PeopleIcon
        ChatTab.CODES -> Icons.Filled.Notifications
    }

/** Two people, for the Groups tab (Material's "people" icon, Apache 2.0; not in icons-core). */
private val PeopleIcon: ImageVector by lazy {
    ImageVector.Builder(name = "People", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = addPathNodes(
                "M16,11c1.66,0 2.99,-1.34 2.99,-3S17.66,5 16,5c-1.66,0 -3,1.34 -3,3s1.34,3 3,3z" +
                    "M8,11c1.66,0 2.99,-1.34 2.99,-3S9.66,5 8,5C6.34,5 5,6.34 5,8s1.34,3 3,3z" +
                    "M8,13c-2.33,0 -7,1.17 -7,3.5V19h14v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z" +
                    "M16,13c-0.29,0 -0.62,0.02 -0.97,0.05 1.16,0.84 1.97,1.97 1.97,3.45V19h6v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z",
            ),
            fill = SolidColor(Color.Black),
        ).build()
}

/** The chat list: one tab each for People, Groups, and Codes & alerts. */
@Composable
fun ChatListScreen(
    chats: List<Chat>,
    tab: ChatTab,
    onTab: (ChatTab) -> Unit,
    onOpen: (Chat) -> Unit,
    time: (Long) -> String,
    onOpenSettings: () -> Unit = {},
    onNewChat: (() -> Unit)? = null,
    /** Search: what's typed, and the messages that match it (from the message store). */
    query: String = "",
    onQuery: ((String) -> Unit)? = null,
    hits: List<io.github.secnewt.messages.chats.SearchHit> = emptyList(),
    /** What to do with the chats picked by long-press. Null hides selecting. */
    onBulk: ((BulkAction, List<Chat>) -> Unit)? = null,
) {
    // Chats picked by long-press; while any are picked, taps add or remove instead of opening.
    var picked by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(listOf<Long>()) }
    val pickedChats = chats.filter { it.threadId in picked }
    val selecting = picked.isNotEmpty() && onBulk != null
    var confirming by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<BulkAction?>(null) }
    fun toggle(chat: Chat) {
        picked = if (chat.threadId in picked) picked - chat.threadId else picked + chat.threadId
    }
    fun run(action: BulkAction) {
        onBulk?.invoke(action, pickedChats)
        picked = emptyList()
    }
    if (selecting) androidx.activity.compose.BackHandler { picked = emptyList() }
    confirming?.let { action ->
        BulkConfirmDialog(action, pickedChats, onConfirm = { confirming = null; run(action) }, onCancel = { confirming = null })
    }

    val searching = query.isNotBlank()
    // Swipe left and right between People, Groups, and Codes & alerts; the tabs follow.
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = tab.ordinal) { ChatTab.entries.size }
    androidx.compose.runtime.LaunchedEffect(tab) { if (pager.currentPage != tab.ordinal) pager.animateScrollToPage(tab.ordinal) }
    androidx.compose.runtime.LaunchedEffect(pager) {
        androidx.compose.runtime.snapshotFlow { pager.settledPage }.collect { page -> onTab(ChatTab.entries[page]) }
    }

    Scaffold(
        topBar = {
            if (selecting) SelectionBar(pickedChats, onAction = { action -> if (action.confirm) confirming = action else run(action) }, onCancel = { picked = emptyList() })
        },
        bottomBar = { ChatTabBar(chats, tab, onTab) },
        floatingActionButton = {
            if (onNewChat != null && !selecting) {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = onNewChat,
                    modifier = Modifier.semantics { contentDescription = "New message" },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New message", style = MaterialTheme.typography.titleMedium) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                if (!selecting) TabHeader(if (searching) "Search" else tab.label, onOpenSettings)
                if (onQuery != null && !selecting) {
                    androidx.compose.material3.OutlinedTextField(
                        value = query,
                        onValueChange = onQuery,
                        label = { Text("Search chats and messages") },
                        singleLine = true,
                        colors = io.github.secnewt.ui.neonFieldColors(),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        trailingIcon = if (searching) ({
                            androidx.compose.material3.TextButton(onClick = { onQuery("") }) { Text("Clear") }
                        }) else null,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
            if (searching) {
                SearchResults(chats, query, hits, time, onOpen)
            } else {
                androidx.compose.foundation.pager.HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                    val pageTab = ChatTab.entries[page]
                    val shown = ChatSorting.inTab(chats, pageTab)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // Room at the bottom so the New message button never covers the last chat.
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    ) {
                        if (shown.isEmpty()) {
                            item {
                                Text(
                                    emptyText(pageTab),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        }
                        items(shown, key = { it.threadId }) { chat ->
                            ChatRow(
                                chat, time(chat.lastTimeMillis),
                                picked = if (onBulk != null) chat.threadId in picked else null,
                                onLongPress = if (onBulk != null) ({ toggle(chat) }) else null,
                            ) { if (selecting) toggle(chat) else onOpen(chat) }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/** What can be done to several picked chats at once. */
enum class BulkAction(val label: String, val confirm: Boolean) {
    PIN("Pin", false), UNPIN("Unpin", false), MUTE("Mute", false), UNMUTE("Unmute", false),
    MARK_READ("Mark read", false), BLOCK("Block", true), DELETE("Delete", true),
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SelectionBar(picked: List<Chat>, onAction: (BulkAction) -> Unit, onCancel: () -> Unit) {
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (picked.size == 1) "1 chat picked" else "${picked.size} chats picked",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
                )
                androidx.compose.material3.TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Done", style = MaterialTheme.typography.titleMedium)
                }
            }
            // Wraps onto more lines at large text, so every action stays visible.
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val allPinned = picked.all { it.pinned }
                val allMuted = picked.all { it.muted }
                listOf(
                    if (allPinned) BulkAction.UNPIN else BulkAction.PIN,
                    if (allMuted) BulkAction.UNMUTE else BulkAction.MUTE,
                    BulkAction.MARK_READ, BulkAction.BLOCK, BulkAction.DELETE,
                ).forEach { action ->
                    androidx.compose.material3.OutlinedButton(onClick = { onAction(action) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(
                            action.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (action.confirm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BulkConfirmDialog(action: BulkAction, picked: List<Chat>, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val people = picked.flatMap { it.people }.distinctBy { it.address }
    val (title, body) = when (action) {
        BulkAction.DELETE -> (if (picked.size == 1) "Delete this chat?" else "Delete ${picked.size} chats?") to
            "Every message in them is deleted from this phone, for every messaging app. This can't be undone."
        else -> (if (people.size == 1) "Block ${people.first().shownName}?" else "Block ${people.size} people?") to
            "Android turns away texts and calls from them, in every app, including Newt Phone. " +
            "You can unblock each one from a chat's details or Newt Phone's block list."
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { Text(body, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(action.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel", style = MaterialTheme.typography.titleMedium)
            }
        },
    )
}

@Composable
private fun SearchResults(chats: List<Chat>, query: String, hits: List<io.github.secnewt.messages.chats.SearchHit>, time: (Long) -> String, onOpen: (Chat) -> Unit) {
    val people = io.github.secnewt.messages.chats.Search.chats(chats, query)
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
        if (people.isEmpty() && hits.isEmpty()) {
            item { Text("Nothing matches “${query.trim()}”.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 16.dp)) }
        }
        if (people.isNotEmpty()) item { io.github.secnewt.ui.SectionLabel("Chats") }
        items(people, key = { "c" + it.threadId }) { chat ->
            ChatRow(chat, time(chat.lastTimeMillis), picked = null, onLongPress = null) { onOpen(chat) }
            HorizontalDivider()
        }
        if (hits.isNotEmpty()) item { io.github.secnewt.ui.SectionLabel("Messages", modifier = Modifier.padding(top = 12.dp)) }
        items(hits.mapNotNull { hit -> chats.firstOrNull { it.threadId == hit.threadId }?.let { hit to it } }) { (hit, chat) ->
            ChatRow(
                chat.copy(lastMessage = io.github.secnewt.messages.chats.Search.excerpt(hit.body, query), unread = 0, pinned = false),
                time(hit.timeMillis), picked = null, onLongPress = null,
            ) { onOpen(chat) }
            HorizontalDivider()
        }
    }
}

private fun emptyText(tab: ChatTab) = when (tab) {
    ChatTab.PEOPLE -> "No chats yet."
    ChatTab.GROUPS -> "No group chats yet."
    ChatTab.CODES -> "Sign-in codes, delivery notices and other automatic texts land here."
}

/**
 * One chat in the list. Unread chats are hard to miss: a thick bar in the accent color down
 * the left, a tinted background, bold words, and the count in a big pill. [picked] is null
 * when selecting isn't offered, otherwise whether this chat is picked.
 */
@Composable
private fun ChatRow(chat: Chat, time: String, picked: Boolean?, onLongPress: (() -> Unit)?, onClick: () -> Unit) {
    val unread = chat.unread > 0
    val spoken = buildString {
        if (picked == true) append("Picked. ")
        append(chat.title)
        if (chat.pinned) append(", pinned")
        if (chat.muted) append(", muted")
        if (unread) append(", ${chat.unread} unread")
        append(". ${chat.lastMessage}. $time")
    }
    val accent = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(
                when {
                    picked == true -> MaterialTheme.colorScheme.secondaryContainer
                    unread -> MaterialTheme.colorScheme.surfaceContainerHigh
                    else -> Color.Transparent
                },
            )
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongPress,
                onLongClickLabel = if (onLongPress != null) "Pick" else null,
            )
            .clearAndSetSemantics {
                contentDescription = spoken
                onClick(label = "Open chat") { onClick(); true }
                // Screen readers get picking as an action, since a long press is hard to find.
                if (onLongPress != null) customActions = listOf(CustomAccessibilityAction(if (picked == true) "Unpick" else "Pick") { onLongPress(); true })
            }
            .padding(end = 4.dp),
    ) {
        Box(Modifier.width(6.dp).heightIn(min = 72.dp).background(if (unread) accent else Color.Transparent))
        if (picked == true) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp).background(accent, CircleShape)) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        } else {
            Initial(chat.title)
        }
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (chat.pinned) "📌 " else "") + (if (chat.muted) "🔕 " else "") + chat.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (unread) FontWeight.ExtraBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    time,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    color = if (unread) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.lastMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .heightIn(min = 32.dp)
                            .widthIn(min = 32.dp)
                            .background(accent, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp),
                    ) {
                        Text(chat.unread.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

/** A person's (or group's) first letter on their color. */
@Composable
fun Initial(name: String, size: Int = 48) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size.dp).background(personColor(name), CircleShape),
    ) {
        Text(
            name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OnPersonColor,
        )
    }
}

/** Bottom tabs, with the word always shown and an unread count on each. */
@Composable
private fun ChatTabBar(chats: List<Chat>, selected: ChatTab, onSelect: (ChatTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        ChatTab.entries.forEach { tab ->
            val unread = ChatSorting.inTab(chats, tab).sumOf { it.unread }
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = {
                    BadgedBox(badge = { if (unread > 0) Badge { Text(unread.toString()) } }) {
                        Icon(tab.icon, contentDescription = null)
                    }
                },
                label = {
                    Text(
                        if (tab == ChatTab.CODES) "Codes" else tab.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (tab == selected) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                alwaysShowLabel = true,
                modifier = Modifier.semantics {
                    contentDescription = tab.label + if (unread > 0) ", $unread unread" else ""
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
