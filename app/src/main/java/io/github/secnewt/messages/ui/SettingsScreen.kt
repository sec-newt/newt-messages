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
) {
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
        }
    }
}
