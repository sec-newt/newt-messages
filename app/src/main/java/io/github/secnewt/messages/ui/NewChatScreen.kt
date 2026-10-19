package io.github.secnewt.messages.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.secnewt.messages.chats.ContactNumber
import io.github.secnewt.messages.chats.Recipients
import io.github.secnewt.ui.ScreenHeader
import io.github.secnewt.ui.neonFieldColors

/**
 * Starting a chat: search contacts by name or number, or type any number. Pick one person
 * for a one-to-one chat, or several for a new group.
 */
@Composable
fun NewChatScreen(
    contacts: List<ContactNumber>,
    onBack: () -> Unit,
    onStart: (List<String>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Chosen numbers, in the order picked.
    var chosen by rememberSaveable { mutableStateOf(listOf<String>()) }
    val names = contacts.associate { it.number to it.name }
    fun toggle(number: String) {
        chosen = if (number in chosen) chosen - number else chosen + number
    }

    Scaffold(
        topBar = { Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { ScreenHeader("New message", onBack) } },
        bottomBar = {
            if (chosen.isNotEmpty()) {
                Button(
                    onClick = { onStart(chosen) },
                    modifier = Modifier.fillMaxWidth().imePadding().padding(16.dp).heightIn(min = 56.dp),
                ) {
                    Text(
                        if (chosen.size == 1) "Text ${names[chosen.first()] ?: chosen.first()}" else "Start a group of ${chosen.size}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Name or number") },
                    singleLine = true,
                    colors = neonFieldColors(),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (chosen.isNotEmpty()) {
                item {
                    Text(
                        "To: " + chosen.joinToString(", ") { names[it] ?: it },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            Recipients.typedNumber(query)?.let { number ->
                if (number !in chosen) {
                    item {
                        TextButton(
                            onClick = {
                                toggle(number)
                                query = ""
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) { Text("Add $number", style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
            val found = Recipients.search(contacts, query)
            if (found.isEmpty() && Recipients.typedNumber(query) == null) {
                item {
                    Text(
                        if (contacts.isEmpty()) "No contacts with phone numbers. Type a number above." else "No contact matches.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(found, key = { it.number + it.name }) { contact ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable(role = Role.Checkbox) { toggle(contact.number) }
                        .padding(vertical = 8.dp),
                ) {
                    Initial(contact.name)
                    Column(Modifier.weight(1f)) {
                        Text(contact.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(contact.label, contact.number).joinToString(" · "),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Checkbox(checked = contact.number in chosen, onCheckedChange = null)
                }
                HorizontalDivider()
            }
        }
    }
}
