package io.github.secnewt.messages.ui

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.secnewt.messages.settings.MessagesSettings
import io.github.secnewt.messages.settings.ReactionNotify
import io.github.secnewt.messages.settings.SpeakMode
import io.github.secnewt.ui.ScreenHeader
import io.github.secnewt.ui.SectionHeading

/** The app's settings: how reactions notify, and saying who a message is from. */
@Composable
fun SettingsScreen(settings: MessagesSettings, onChange: (MessagesSettings) -> Unit, onBack: () -> Unit, onOpenVoiceSettings: () -> Unit = {}) {
    Scaffold(topBar = { Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) { ScreenHeader("Settings", onBack) } }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionHeading("Reactions") }
            item {
                Choices(ReactionNotify.entries, settings.reactions, { it.label }, { it.explanation }) {
                    onChange(settings.copy(reactions = it))
                }
            }
            item { SectionHeading("Say who's messaging", modifier = Modifier.padding(top = 16.dp)) }
            item {
                Choices(SpeakMode.entries, settings.speak, { it.label }, { it.explanation }) {
                    onChange(settings.copy(speak = it))
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .toggleable(value = settings.quietDuringDnd, role = Role.Switch) { onChange(settings.copy(quietDuringDnd = it)) },
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Quiet during Do Not Disturb", style = MaterialTheme.typography.titleMedium)
                        Text("Don't speak while Do Not Disturb is on, even with headphones.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.quietDuringDnd, onCheckedChange = null)
                }
            }
            item {
                androidx.compose.material3.TextButton(onClick = onOpenVoiceSettings, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text("Voice settings (Android)", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun <T> Choices(options: List<T>, current: T, label: (T) -> String, explanation: (T) -> String, onPick: (T) -> Unit) {
    Column(Modifier.selectableGroup()) {
        options.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .selectable(selected = option == current, role = Role.RadioButton) { onPick(option) },
            ) {
                RadioButton(selected = option == current, onClick = null)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(label(option), style = MaterialTheme.typography.titleMedium)
                    Text(explanation(option), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
