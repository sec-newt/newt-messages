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

    /** What making a video smaller came to. */
    sealed interface Result {
        class Made(val bytes: ByteArray) : Result
        /** Too long to fit at any watchable quality. */
        data object TooLong : Result
        /** It couldn't be read or the phone's encoder couldn't make it. */
        data object Failed : Result
    }

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
     * The video re-made as a small MP4 under [maxBytes]; or why not. Pass the application
     * context: this can run for minutes. Run it off the main thread: it waits for Android's video encoder.
     * Setting the quality is marked "unstable" in Media3 (it may change between versions).
     */
    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    fun shrink(context: Context, uri: Uri, maxBytes: Long): Result {
        val duration = durationMillis(context, uri) ?: return Result.Failed
        val plan = VideoPlan.plan(duration, maxBytes) ?: return Result.TooLong
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
            when {
                ok.get() && out.length() in 1..maxBytes -> Result.Made(out.readBytes())
                // Made, but the encoder overshot the limit.
                ok.get() -> Result.TooLong
                else -> Result.Failed
            }
        } finally {
            out.delete()
        }
    }
}
