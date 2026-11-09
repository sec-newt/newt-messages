package io.github.secnewt.messages.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import io.github.secnewt.messages.chats.VideoPlan
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Making a video small enough for a picture message, on the phone (nothing leaves it). */
object Videos {

    /** How long a video is, or null if it can't be read. */
    fun durationMillis(context: Context, uri: Uri): Long? = try {
        MediaMetadataRetriever().use { reader ->
            reader.setDataSource(context, uri)
            reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        }
    } catch (e: Exception) {
        null
    }

    /**
     * The video re-made as a small MP4 under [maxBytes], or null if it's too long to fit or
     * couldn't be made. Run it off the main thread: it waits for Android's video encoder.
     * Setting the quality is marked "unstable" in Media3 (it may change between versions).
     */
    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    fun shrink(context: Context, uri: Uri, maxBytes: Long): ByteArray? {
        val duration = durationMillis(context, uri) ?: return null
        val plan = VideoPlan.plan(duration, maxBytes) ?: return null
        val out = File(File(context.cacheDir, "video").apply { mkdirs() }, "send-${System.nanoTime()}.mp4")
        val done = CountDownLatch(1)
        val ok = AtomicBoolean(false)
        var transformer: Transformer? = null
        // Media3 runs from a thread with a looper; the main one is fine, the work itself is elsewhere.
        val main = Handler(Looper.getMainLooper())
        main.post {
            try {
                val encoders = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(plan.videoBitsPerSecond).build())
                    .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(VideoPlan.AUDIO_BITS_PER_SECOND).build())
                    .build()
                transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoders)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            ok.set(true)
                            done.countDown()
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            Log.w("NewtVideo", "Couldn't shrink a video: ${exportException.errorCodeName}")
                            done.countDown()
                        }
                    })
                    .build()
                val item = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                    .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(plan.height))))
                    .build()
                transformer?.start(item, out.path)
            } catch (e: Exception) {
                Log.w("NewtVideo", "Couldn't start shrinking a video", e)
                done.countDown()
            }
        }
        if (!done.await(5, TimeUnit.MINUTES)) main.post { transformer?.cancel() }
        return try {
            if (ok.get() && out.length() in 1..maxBytes) out.readBytes() else null
        } finally {
            out.delete()
        }
    }
}
