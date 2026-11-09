package io.github.secnewt.messages.ui

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.secnewt.messages.chats.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** "0:12", "1:05", "1:02:03". */
internal fun clockOf(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** How long a video or sound is, or null. */
private fun durationOf(context: android.content.Context, uri: String): Long? = try {
    MediaMetadataRetriever().use { reader ->
        reader.setDataSource(context, Uri.parse(uri))
        reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    }
} catch (e: Exception) {
    null
}

/** Whether Android's animations are off (Settings → Accessibility → Remove animations). */
internal fun animationsOff(context: android.content.Context): Boolean =
    android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * A video in a message: its first frame with a big play mark and "Play video · 0:12" written
 * under it. Tap to watch it full screen.
 */
@Composable
internal fun VideoTile(file: Attachment, ink: Color, onPlay: () -> Unit, onLongPress: (() -> Unit)? = null) {
    val context = LocalContext.current
    val frame by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file.uri) {
        value = withContext(Dispatchers.IO) { Pictures.preview(context, file.uri, file.type, maxPx = 640) }
    }
    val length by produceState<Long?>(null, file.uri) { value = withContext(Dispatchers.IO) { durationOf(context, file.uri) } }
    val label = "Play video" + (length?.let { " · ${clockOf(it)}" } ?: "")
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                Modifier.combinedClickableCompat(onClick = onPlay, onLongClick = onLongPress, label = label),
            )
            .semantics { contentDescription = label },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.widthIn(min = 200.dp, max = 260.dp).heightIn(min = 140.dp, max = 320.dp).background(Color.Black),
        ) {
            frame?.let {
                androidx.compose.foundation.Image(it, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            }
            Icon(
                Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White,
                modifier = Modifier.size(64.dp).background(Color.Black.copy(alpha = 0.7f), CircleShape).border(2.dp, Color.White, CircleShape).padding(8.dp),
            )
        }
        Text("▶ $label", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ink)
    }
}

/** A tap, and optionally a long press, on one target. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)?, label: String): Modifier =
    this.then(
        Modifier.combinedClickable(
            role = Role.Button,
            onClickLabel = label,
            onLongClickLabel = if (onLongClick != null) "Message options" else null,
            onLongClick = onLongClick,
            onClick = onClick,
        ),
    )

/**
 * A video full screen, starting straight away, with big worded Pause / Play, Save and Close.
 * Android's own player reads it from the message store; nothing leaves the phone.
 */
@Composable
internal fun VideoPlayer(file: Attachment, onClose: () -> Unit, onSave: (() -> Unit)?) {
    var view by remember { mutableStateOf<VideoView?>(null) }
    var playing by remember { mutableStateOf(true) }
    var problem by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { view?.stopPlayback() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (problem) {
                    Text("This video can't play on this phone. Try Save, then open it in another app.", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(24.dp))
                } else {
                    AndroidView(
                        factory = { context ->
                            VideoView(context).apply {
                                setVideoURI(Uri.parse(file.uri))
                                setOnCompletionListener { playing = false }
                                setOnErrorListener { _, _, _ -> problem = true; true }
                                start()
                                view = this
                            }
                        },
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                if (!problem) {
                    Button(
                        onClick = {
                            view?.let { if (playing) it.pause() else it.start() }
                            playing = !playing
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    ) { Text(if (playing) "Pause" else "Play", style = MaterialTheme.typography.titleMedium) }
                }
                if (onSave != null) {
                    OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Text("Save", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                }
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Text("Close", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
            }
        }
    }
}

/**
 * A voice message or other sound: a big worded Play / Pause button and how far along it is,
 * "0:04 of 0:12". It plays through the phone's media volume.
 */
@Composable
internal fun AudioRow(file: Attachment, ink: Color) {
    val context = LocalContext.current
    var player by remember(file.uri) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(file.uri) { mutableStateOf(false) }
    var at by remember(file.uri) { mutableIntStateOf(0) }
    var problem by remember(file.uri) { mutableStateOf(false) }
    val length by produceState<Long?>(null, file.uri) { value = withContext(Dispatchers.IO) { durationOf(context, file.uri) } }
    DisposableEffect(file.uri) { onDispose { player?.release() } }
    LaunchedEffect(playing) {
        while (playing) {
            at = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
            delay(250)
        }
    }
    fun toggle() {
        if (playing) {
            player?.pause()
            playing = false
            return
        }
        val p = player ?: try {
            MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                setDataSource(context, Uri.parse(file.uri))
                setOnCompletionListener { playing = false; at = 0; seekTo(0) }
                prepare()
            }.also { player = it }
        } catch (e: Exception) {
            problem = true
            null
        } ?: return
        p.start()
        playing = true
    }
    val total = length?.let(::clockOf)
    val where = if (playing || at > 0) clockOf(at.toLong()) + (total?.let { " of $it" } ?: "") else total ?: ""
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("🔊 ${file.name}", style = MaterialTheme.typography.titleMedium, color = ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = ::toggle,
                enabled = !problem,
                modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = (if (playing) "Pause " else "Play ") + file.name },
            ) { Text(if (playing) "❚❚ Pause" else "▶ Play", style = MaterialTheme.typography.titleMedium, color = ink) }
            Text(
                if (problem) "Can't play here; use Save" else where,
                fontFamily = io.github.secnewt.ui.NewtFonts.Mono,
                style = MaterialTheme.typography.titleMedium,
                color = ink,
            )
        }
    }
}

/**
 * A moving picture (GIF) in a message. It plays on its own, unless Android's animations are
 * off; then it stays still on its first frame. Tap for full size, hold for options.
 */
@Composable
internal fun AnimatedPicture(
    drawable: android.graphics.drawable.AnimatedImageDrawable,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)?,
) {
    val context = LocalContext.current
    DisposableEffect(drawable) {
        if (!animationsOff(context)) drawable.start()
        onDispose { drawable.stop() }
    }
    AndroidView(
        factory = { c ->
            android.widget.ImageView(c).apply {
                adjustViewBounds = true
                scaleType = android.widget.ImageView.ScaleType.FIT_START
                setImageDrawable(drawable)
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        },
        modifier = Modifier
            .widthIn(max = 260.dp)
            .heightIn(max = 320.dp)
            .combinedClickableCompat(onClick = onOpen, onLongClick = onLongPress, label = "See it full size")
            .semantics { contentDescription = "Moving picture" },
    )
}
