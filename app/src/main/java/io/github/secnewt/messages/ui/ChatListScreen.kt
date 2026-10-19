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
import androidx.compose.ui.graphics.Color
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
    onDelete: ((Chat) -> Unit)? = null,
    onNewChat: (() -> Unit)? = null,
) {
    var deleting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Chat?>(null) }
    deleting?.let { chat ->
        DeleteChatDialog(chat.title, onDelete = { deleting = null; onDelete?.invoke(chat) }, onCancel = { deleting = null })
    }
    Scaffold(
        bottomBar = { ChatTabBar(chats, tab, onTab) },
        floatingActionButton = {
            if (onNewChat != null) {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = onNewChat,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New message", style = MaterialTheme.typography.titleMedium) },
                )
            }
        },
    ) { padding ->
        val shown = ChatSorting.inTab(chats, tab)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            // Room at the bottom so the New message button never covers the last chat.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        ) {
            item { TabHeader(tab.label, onOpenSettings) }
            if (shown.isEmpty()) {
                item {
                    Text(
                        emptyText(tab),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(shown, key = { it.threadId }) { chat ->
                ChatRow(chat, time(chat.lastTimeMillis), onLongPress = if (onDelete != null) ({ deleting = chat }) else null) { onOpen(chat) }
                HorizontalDivider()
            }
        }
    }
}

private fun emptyText(tab: ChatTab) = when (tab) {
    ChatTab.PEOPLE -> "No chats yet."
    ChatTab.GROUPS -> "No group chats yet."
    ChatTab.CODES -> "Sign-in codes, delivery notices and other automatic texts land here."
}

@Composable
private fun ChatRow(chat: Chat, time: String, onLongPress: (() -> Unit)?, onClick: () -> Unit) {
    val unread = chat.unread > 0
    val spoken = buildString {
        append(chat.title)
        if (chat.pinned) append(", pinned")
        if (unread) append(", ${chat.unread} unread")
        append(". ${chat.lastMessage}. $time")
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongPress,
                onLongClickLabel = if (onLongPress != null) "Delete chat" else null,
            )
            .clearAndSetSemantics {
                contentDescription = spoken
                onClick(label = "Open chat") { onClick(); true }
                // Screen readers get Delete as an action, since a long press is hard to find.
                if (onLongPress != null) customActions = listOf(CustomAccessibilityAction("Delete chat") { onLongPress(); true })
            }
            .padding(vertical = 10.dp),
    ) {
        Initial(chat.title)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (chat.pinned) "📌 " else "") + chat.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Badge(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                        Text(chat.unread.toString(), style = MaterialTheme.typography.titleSmall)
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
