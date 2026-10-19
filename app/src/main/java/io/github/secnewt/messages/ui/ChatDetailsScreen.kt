package io.github.secnewt.messages.ui

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
import androidx.compose.foundation.selection.toggleable
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
import io.github.secnewt.ui.ScreenHeader
import io.github.secnewt.ui.neonFieldColors

/**
 * Everything about a chat: who's in it (text, call, or save an unknown number to contacts),
 * the group's name, adding or removing people, and deleting the chat.
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
    onRename: (String) -> Unit,
    /** Start a chat with these numbers (a new group when people are added or removed). */
    onNewMembers: (List<String>) -> Unit,
    onPickContact: ((String) -> Unit) -> Unit,
    onDelete: () -> Unit,
    onPin: (Boolean) -> Unit = {},
    onMute: (Boolean) -> Unit = {},
    isBlocked: (Person) -> Boolean = { false },
    onBlock: (Person, Boolean) -> Unit = { _, _ -> },
) {
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

    Scaffold(topBar = { Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { ScreenHeader(if (chat.isGroup) "Group details" else "Chat details", onBack) } }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (chat.isGroup) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Name", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(chat.name ?: "No name yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = { renaming = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Rename", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            item {
                Text(
                    if (chat.isGroup) "${chat.people.size} people, plus you" else "With",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
            }
            items(chat.people, key = { it.address }) { person ->
                val blocked = isBlocked(person)
                MemberRow(
                    person,
                    blocked = blocked,
                    onBlock = { if (blocked) onBlock(person, false) else blocking = person.address },
                    canRemove = chat.people.size > 2,
                    onText = { onText(person) },
                    onCall = { onCall(person) },
                    onAddToContacts = { onAddToContacts(person) },
                    onRemove = { removing = person.address },
                )
                HorizontalDivider()
            }
            item { Toggle("Pin to top", "Keep this chat at the top of its tab.", chat.pinned, onPin) }
            item { Toggle("Mute this chat", "No notifications from it. Messages still arrive and count as unread.", chat.muted, onMute) }
            item {
                Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(if (chat.isGroup) "Add someone" else "Add someone (starts a group)", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (chat.isGroup) {
                item {
                    Text(
                        "Adding or removing someone starts a new group with the new people. Group texts have " +
                            "no shared member list, so this chat stays as it is.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                OutlinedButton(onClick = { deleting = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 8.dp)) {
                    Text("Delete this chat", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    person: Person,
    blocked: Boolean,
    onBlock: () -> Unit,
    canRemove: Boolean,
    onText: () -> Unit,
    onCall: () -> Unit,
    onAddToContacts: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Initial(person.shownName)
            Column(Modifier.weight(1f)) {
                Text(person.shownName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (blocked) {
                    Text("Blocked", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
                if (person.name != null) {
                    Text(person.address, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Not in your contacts", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Words, not bare icons, so every button says what it does.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            // Screen readers hear whose button it is ("Text Becca"), not just "Text".
            val who = person.shownName
            OutlinedButton(onClick = onText, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Text $who" }) {
                Text("Text", style = MaterialTheme.typography.titleSmall)
            }
            OutlinedButton(onClick = onCall, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Call $who" }) {
                Text("Call", style = MaterialTheme.typography.titleSmall)
            }
            if (person.name == null) {
                OutlinedButton(onClick = onAddToContacts, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Add $who to contacts" }) {
                    Text("Add to contacts", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        TextButton(onClick = onBlock, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(
                if (blocked) "Unblock ${person.shownName}" else "Block ${person.shownName}",
                style = MaterialTheme.typography.titleSmall,
                color = if (blocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        if (canRemove) {
            TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Remove ${person.shownName} from the group", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
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
