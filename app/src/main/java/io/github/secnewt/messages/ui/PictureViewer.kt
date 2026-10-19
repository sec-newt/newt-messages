package io.github.secnewt.messages.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A picture full screen. Pinch to zoom, drag to look around; Close or back to leave. */
@Composable
fun PictureViewer(uri: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { Pictures.thumbnail(context, uri, maxPx = 3000) }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val zoom = rememberTransformableState { change, pan, _ ->
        scale = (scale * change).coerceIn(1f, 6f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val loaded = image
            if (loaded != null) {
                Image(
                    loaded,
                    contentDescription = "Picture",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(zoom)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            } else {
                Text("Loading…", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center))
            }
            Button(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(16.dp).heightIn(min = 56.dp)) {
                Text("Close", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** "Delete this chat?" with what it removes said plainly. */
@Composable
fun DeleteChatDialog(title: String, onDelete: () -> Unit, onCancel: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Delete this chat?") },
        text = {
            Text(
                "Every message with $title is deleted from this phone, for every messaging app. This can't be undone.",
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Delete", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel", style = MaterialTheme.typography.titleMedium)
            }
        },
    )
}
