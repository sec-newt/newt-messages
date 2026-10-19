package io.github.secnewt.messages.ui

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

/** Shown until Newt Messages is the phone's messaging app, with the button to make it so. */
@Composable
fun WelcomeScreen(onMakeDefault: () -> Unit = {}) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Texts, picture messages and group chats. Your messages stay in Android's shared " +
                    "message store, so switching apps loses nothing.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Still new: keep your old messaging app installed. To switch back, open Settings → " +
                    "Apps → Default apps → SMS app and pick it again.",
                style = MaterialTheme.typography.bodyLarge,
            )
            androidx.compose.material3.Button(
                onClick = onMakeDefault,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text("Make Newt Messages my messaging app", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
