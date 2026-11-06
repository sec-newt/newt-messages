package io.github.secnewt.messages.ui

import io.github.secnewt.ui.NewtButton
import io.github.secnewt.ui.NewtPrimaryButton
import io.github.secnewt.ui.NewtTone
import io.github.secnewt.ui.NewtStateChip
import io.github.secnewt.ui.NewtSettingsCard
import io.github.secnewt.ui.NewtSwitchRow
import io.github.secnewt.ui.newtStripe
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.secnewt.messages.chats.Chat
import io.github.secnewt.messages.chats.Person
import io.github.secnewt.messages.chats.PersonHue
import io.github.secnewt.ui.ScreenHeader
import io.github.secnewt.ui.neonFieldColors

/**
 * Everything about a chat: who's in it (text, call, save an unknown number to contacts, or
 * change their color), the group's name, adding or removing people, and deleting the chat.
 *
 * Group texts have no shared member list: each phone just has a list of numbers. So adding
 * or removing someone starts a new group with the new people, and the screen says so.
 */
@Composable
fun ChatDetailsScreen(
    chat: Chat,
    onBack: () -> Unit,
    onText: (Person) -> Unit,
    onCall: (Person) -> Unit,
    onAddToContacts: (Person) -> Unit,
    /** Open a saved contact in the contacts app, to look (false) or edit (true). */
    onOpenContact: (Person, Boolean) -> Unit = { _, _ -> },
    onRename: (String) -> Unit,
    /** Start a chat with these numbers (a new group when people are added or removed). */
    onNewMembers: (List<String>) -> Unit,
    onPickContact: ((String) -> Unit) -> Unit,
    onDelete: () -> Unit,
    onPin: (Boolean) -> Unit = {},
    onMute: (Boolean) -> Unit = {},
    isBlocked: (Person) -> Boolean = { false },
    onBlock: (Person, Boolean) -> Unit = { _, _ -> },
    /** Pick someone's color (null: back to automatic), for every chat or just this one. */
    onPickColor: (Person, PersonHue?, Boolean) -> Unit = { _, _, _ -> },
) {
    var coloring by rememberSaveable { mutableStateOf<String?>(null) }
    coloring?.let { address ->
        val person = chat.people.first { it.address == address }
        ColorDialog(
            chat, person,
            onPick = { hue, everyChat -> coloring = null; onPickColor(person, hue, everyChat) },
            onCancel = { coloring = null },
        )
    }
    var blocking by rememberSaveable { mutableStateOf<String?>(null) }
    blocking?.let { address ->
        val person = chat.people.first { it.address == address }
        AlertDialog(
            onDismissRequest = { blocking = null },
            title = { Text("Block ${person.shownName}?") },
            text = {
                Text(
                    "Android turns away texts and calls from this number, in every app, including Newt Phone. " +
                        "You can unblock it here or in Newt Phone's block list.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = { blocking = null; onBlock(person, true) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Block", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { blocking = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel", style = MaterialTheme.typography.titleMedium) }
            },
        )
    }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf(false) }

    if (renaming) RenameGroupDialog(chat.name.orEmpty(), onRename = { renaming = false; onRename(it) }, onCancel = { renaming = false })
    if (deleting) DeleteChatDialog(chat.title, onDelete = { deleting = false; onDelete() }, onCancel = { deleting = false })
    if (adding) {
        AddPersonDialog(
            onAdd = { number -> adding = false; onNewMembers(chat.people.map { it.address } + number) },
            onPickContact = onPickContact,
            onCancel = { adding = false },
        )
    }
    removing?.let { address ->
        val person = chat.people.first { it.address == address }
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${person.shownName}?") },
            text = {
                Text(
                    "Group texts have no shared member list, so this starts a new group without " +
                        "${person.shownName}. This chat stays as it is, and they can still reply to it.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    onNewMembers(chat.people.map { it.address } - address)
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Start new group", style = MaterialTheme.typography.titleMedium) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cancel", style = MaterialTheme.typography.titleMedium)
                }
            },
        )
    }

    Scaffold(topBar = { Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) { ScreenHeader(if (chat.isGroup) "Group details" else "Chat details", onBack) } }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        if (chat.isGroup) {
                            Text(chat.name ?: "No name yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            if (chat.isGroup) "${chat.people.size} people, plus you" else "With",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    if (chat.isGroup) NewtButton("Rename", onClick = { renaming = true }, icon = Icons.Filled.Edit)
                }
            }
            items(chat.people, key = { it.address }) { person ->
                val blocked = isBlocked(person)
                MemberRow(
                    person,
                    hue = chat.hue(person),
                    onColor = { coloring = person.address },
                    blocked = blocked,
                    onBlock = { if (blocked) onBlock(person, false) else blocking = person.address },
                    canRemove = chat.people.size > 2,
                    onText = { onText(person) },
                    onCall = { onCall(person) },
                    onAddToContacts = { onAddToContacts(person) },
                    onOpenContact = { edit -> onOpenContact(person, edit) },
                    onRemove = { removing = person.address },
                )
            }
            item {
                NewtSettingsCard("This chat", icon = Icons.Filled.Settings) {
                    NewtSwitchRow("Pin to top", chat.pinned, onPin, hint = "At the top of its tab")
                    NewtSwitchRow("Mute this chat", chat.muted, onMute, hint = "Messages still arrive")
                }
            }
            item {
                NewtPrimaryButton(
                    if (chat.isGroup) "Add someone" else "Add someone (starts a group)",
                    onClick = { adding = true },
                    icon = Icons.Filled.Add,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (chat.isGroup) {
                item {
                    Text(
                        "Adding or removing someone starts a new group; this chat stays as it is.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                NewtPrimaryButton(
                    "Delete this chat",
                    onClick = { deleting = true },
                    icon = Icons.Filled.Delete,
                    tone = NewtTone.Danger,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * One person in the chat, on a card striped in their color with the color's name written out.
 * Text and Call are worded buttons; everything else, including Block and Remove, is behind ⋯.
 */
@Composable
private fun MemberRow(
    person: Person,
    hue: PersonHue,
    onColor: () -> Unit,
    blocked: Boolean,
    onBlock: () -> Unit,
    canRemove: Boolean,
    onText: () -> Unit,
    onCall: () -> Unit,
    onAddToContacts: () -> Unit,
    onRemove: () -> Unit,
    onOpenContact: (Boolean) -> Unit = {},
) {
    val who = person.shownName
    val shape = RoundedCornerShape(16.dp)
    var menu by rememberSaveable { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .newtStripe(hue.outline(), tint = false)
            .padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(who, person.photo, size = 48, color = hue.fill)
            Column(Modifier.weight(1f)) {
                Text(who, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${hue.label} · ${if (person.name != null) person.address else "not in your contacts"}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (blocked) NewtStateChip("Blocked", NewtTone.Danger, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            NewtButton("Text", onClick = onText, modifier = Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "Text $who" })
            NewtButton("Call", onClick = onCall, modifier = Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "Call $who" })
            Box {
                OutlinedIconButton(
                    onClick = { menu = true },
                    border = BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(58.dp).semantics { contentDescription = "More for $who" },
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    @Composable
                    fun item(text: String, icon: ImageVector?, spoken: String? = null, danger: Boolean = false, action: () -> Unit) =
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            leadingIcon = {
                                if (icon != null) {
                                    Icon(icon, contentDescription = null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                } else {
                                    Swatch(hue, size = 22)
                                }
                            },
                            onClick = { menu = false; action() },
                            modifier = Modifier.heightIn(min = 56.dp).then(if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier),
                        )
                    if (person.contact != null) {
                        item("View contact", Icons.Filled.Person, "View $who's contact") { onOpenContact(false) }
                        item("Edit contact", Icons.Filled.Edit, "Edit $who's contact") { onOpenContact(true) }
                    }
                    if (person.name == null) item("Add to contacts", Icons.Filled.Add, "Add $who to contacts", action = onAddToContacts)
                    item("Change color", null, "Change $who's color, now ${hue.label}", action = onColor)
                    if (canRemove) item("Remove from group", Icons.Filled.Close, danger = true, action = onRemove)
                    item(if (blocked) "Unblock $who" else "Block $who", Icons.Filled.Warning, danger = !blocked, action = onBlock)
                }
            }
        }
    }
}

@Composable
private fun Toggle(title: String, explanation: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = on, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Switch(checked = on, onCheckedChange = null)
    }
}

/** Type a number, or choose one from contacts. */
@Composable
private fun AddPersonDialog(onAdd: (String) -> Unit, onPickContact: ((String) -> Unit) -> Unit, onCancel: () -> Unit) {
    var number by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add someone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Phone number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = neonFieldColors(),
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(onClick = { onPickContact { picked -> number = picked } }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Choose from contacts", style = MaterialTheme.typography.titleSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (number.any { it.isDigit() }) onAdd(number.trim()) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Add", style = MaterialTheme.typography.titleMedium)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel", style = MaterialTheme.typography.titleMedium) }
        },
    )
}

/** A dot of someone's color, as their bubble outline shows it in this theme. */
@Composable
private fun Swatch(hue: PersonHue, size: Int) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(size.dp).background(hue.outline(), androidx.compose.foundation.shape.CircleShape),
    )
}

/**
 * Pick a color for someone. Each color says who else in this chat has it, so it's easy to keep
 * everyone different; tapping one sets it right away.
 */
@Composable
private fun ColorDialog(chat: Chat, person: Person, onPick: (PersonHue?, Boolean) -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("${person.shownName}'s color") },
        text = { ColorPicker(chat, person, onPick, Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel", style = MaterialTheme.typography.titleMedium) }
        },
    )
}

/** The picker itself: where it applies, then every color with who else here has it. */
@Composable
internal fun ColorPicker(chat: Chat, person: Person, onPick: (PersonHue?, Boolean) -> Unit, modifier: Modifier = Modifier) {
    // In a group, most often it's this group that needs telling apart.
    var everyChat by rememberSaveable { mutableStateOf(!chat.isGroup) }
    val current = chat.hue(person)
    val others = chat.people.filter { it.address != person.address }.groupBy(chat::hue)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Where", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Column(Modifier.selectableGroup()) {
            Choice("Just this chat", null, selected = !everyChat) { everyChat = false }
            Choice("Every chat", "Replaces colors picked for them in single chats.", selected = everyChat) { everyChat = true }
        }
        Text(
            "Color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
        Column(Modifier.selectableGroup()) {
            Choice("Automatic", "A color nobody else in the chat has.", selected = false) { onPick(null, everyChat) }
            PersonHue.entries.forEach { hue ->
                val note = when {
                    others[hue] != null -> "${others.getValue(hue).joinToString(", ") { it.shownName }} has this here"
                    hue == PersonHue.MINE -> "Same as your messages"
                    else -> null
                }
                Choice(hue.label, note, selected = hue == current, hue = hue) { onPick(hue, everyChat) }
            }
        }
    }
}

/** One choice in a list: a big row with words, a dot of color when it's a color, and a check when it's the current one. */
@Composable
private fun Choice(label: String, note: String?, selected: Boolean, hue: PersonHue? = null, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick),
    ) {
        if (hue != null) Swatch(hue, size = 32) else androidx.compose.material3.RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            if (note != null) Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (hue != null && selected) Text("✓ Now", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}
