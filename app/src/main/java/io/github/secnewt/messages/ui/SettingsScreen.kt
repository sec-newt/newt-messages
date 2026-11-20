package io.github.secnewt.messages.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.secnewt.messages.settings.MessagesSettings
import io.github.secnewt.messages.settings.ReactionNotify
import io.github.secnewt.messages.settings.SpeakMode
import io.github.secnewt.ui.NewtButton
import io.github.secnewt.ui.NewtChoice
import io.github.secnewt.ui.NewtPickList
import io.github.secnewt.ui.NewtSettingsCard
import io.github.secnewt.ui.NewtSwitchRow
import io.github.secnewt.ui.NewtValueRow
import io.github.secnewt.ui.ScreenHeader

/** The app's settings: how reactions notify, and saying who a message is from. */
@Composable
fun SettingsScreen(
    settings: MessagesSettings,
    onChange: (MessagesSettings) -> Unit,
    onBack: () -> Unit,
    onOpenVoiceSettings: () -> Unit = {},
    onTestVoice: () -> Unit = {},
    /** Numbers on Android's block list, shared with Newt Phone. */
    blocked: List<String>? = emptyList(),
    onUnblock: (String) -> Unit = {},
    /** "0.1.80", for About. */
    version: String = "",
) {
    var unblocking by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    unblocking?.let { number ->
        val shown = io.github.secnewt.messages.chats.PhoneFormat.pretty(number)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { unblocking = null },
            title = { Text("Unblock $shown?") },
            text = { Text("Their texts and calls come through again, here and in Newt Phone.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { onUnblock(number); unblocking = null }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text("Unblock", style = MaterialTheme.typography.titleMedium)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { unblocking = null }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text("Cancel", style = MaterialTheme.typography.titleMedium)
                }
            },
        )
    }
    Scaffold(topBar = { Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) { ScreenHeader("Settings", onBack) } }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                NewtSettingsCard("When someone reacts", icon = Icons.Filled.Favorite) {
                    NewtPickList(
                        options = ReactionNotify.entries,
                        selected = settings.reactions,
                        label = { it.label },
                        hint = { it.explanation },
                        onSelect = { onChange(settings.copy(reactions = it)) },
                    )
                }
            }
            item {
                NewtSettingsCard("Say who's messaging", icon = Icons.Filled.Notifications) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        NewtChoice(
                            options = SpeakMode.entries,
                            selected = settings.speak,
                            label = { if (it == SpeakMode.HEADPHONES_ONLY) "Headphones" else it.label },
                            onSelect = { onChange(settings.copy(speak = it)) },
                            explanation = settings.speak.explanation,
                        )
                    }
                    NewtSwitchRow("Quiet in Do Not Disturb", settings.quietDuringDnd, { onChange(settings.copy(quietDuringDnd = it)) })
                    NewtSwitchRow(
                        "Read the message too",
                        settings.readMessage,
                        { onChange(settings.copy(readMessage = it)) },
                        hint = "Never reads sign-in codes",
                    )
                    NewtValueRow("Voice settings", null, onClick = onOpenVoiceSettings, hint = "Android's own voice and speed")
                }
            }
            item {
                NewtButton("Test the voice", onClick = onTestVoice, icon = Icons.Filled.PlayArrow, modifier = Modifier.fillMaxWidth())
            }
            item {
                NewtSettingsCard("On the lock screen", icon = Icons.Filled.Lock) {
                    NewtPickList(
                        options = io.github.secnewt.messages.settings.LockScreen.entries,
                        selected = settings.lockScreen,
                        label = { it.label },
                        hint = { it.explanation },
                        onSelect = { onChange(settings.copy(lockScreen = it)) },
                    )
                }
            }
            item {
                NewtSettingsCard("Blocked numbers", icon = Icons.Filled.Warning) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            when {
                                blocked == null -> "Android didn't share the block list just now. Try again later."
                                blocked.isEmpty() -> "Nobody is blocked. Block someone from a chat's details, or by holding a chat in the list."
                                else -> "Texts and calls from these are turned away, here and in Newt Phone."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        blocked.orEmpty().forEach { number ->
                            val shown = io.github.secnewt.messages.chats.PhoneFormat.pretty(number)
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            ) {
                                Text(shown, style = MaterialTheme.typography.titleMedium, fontFamily = io.github.secnewt.ui.NewtFonts.Mono, modifier = Modifier.weight(1f))
                                androidx.compose.material3.OutlinedButton(
                                    onClick = { unblocking = number },
                                    modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = "Unblock $shown" },
                                ) { Text("Unblock", style = MaterialTheme.typography.titleMedium) }
                            }
                        }
                    }
                }
            }
            item {
                NewtSettingsCard("About", icon = Icons.Filled.Info) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Newt Messages ${version}".trim(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Texts and picture messages stay on this phone and go out only as texts. No internet, no ads, no tracking.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
