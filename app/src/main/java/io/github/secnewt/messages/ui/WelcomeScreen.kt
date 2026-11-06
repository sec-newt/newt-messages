package io.github.secnewt.messages.ui

import io.github.secnewt.ui.NewtSteps
import io.github.secnewt.ui.NewtStep
import io.github.secnewt.ui.NewtStepState
import io.github.secnewt.ui.NewtPrimaryButton
import io.github.secnewt.ui.NewtSettingsCard
import io.github.secnewt.ui.NewtInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.secnewt.messages.R

/**
 * Shown until Newt Messages is the phone's messaging app: numbered steps with the button on the
 * current one, and what stays the same underneath.
 */
@Composable
fun WelcomeScreen(onMakeDefault: () -> Unit = {}) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text("Texts, picture messages and group chats, in big clear type.", style = MaterialTheme.typography.bodyLarge)
            NewtSteps(
                listOf(
                    NewtStep(
                        "Make Newt Messages your messaging app",
                        NewtStepState.Now,
                        hint = "Android asks you to confirm.",
                        action = { NewtPrimaryButton("Make it my messaging app", onClick = onMakeDefault, modifier = Modifier.fillMaxWidth()) },
                    ),
                    NewtStep("Allow notifications", NewtStepState.ToDo, hint = "Android asks right after."),
                    NewtStep("Open a chat", NewtStepState.ToDo, hint = "Your texts are already here."),
                ),
            )
            NewtSettingsCard("What stays the same", icon = Icons.Filled.Check) {
                Text(
                    "Your messages stay in Android's shared message store, so switching apps loses nothing.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
                NewtInfo(
                    "Keep your old messaging app installed while this is new. To switch back, open Settings → Apps → " +
                        "Default apps → SMS app and pick it again.",
                    label = "Switching back",
                )
            }
        }
    }
}
