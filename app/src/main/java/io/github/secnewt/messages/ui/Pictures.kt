package io.github.secnewt.messages.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

/** Loading pictures to show, and shrinking pictures to send. */
object Pictures {

    /** A picture from [uri], about [maxPx] on its long side, or null if it can't be read. */
    fun thumbnail(context: Context, uri: String, maxPx: Int = 1024): ImageBitmap? = try {
        decode(context, Uri.parse(uri), maxPx)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }

    /**
     * A GIF (or other moving picture) ready to play, about [maxPx] on its long side; null when
     * the picture doesn't move or can't be read, so it's shown as a still one instead.
     */
    fun animated(context: Context, uri: String, maxPx: Int = 720): android.graphics.drawable.AnimatedImageDrawable? = try {
        val type = context.contentResolver.getType(Uri.parse(uri)).orEmpty()
        if (type != "image/gif" && type != "image/webp") {
            null
        } else {
            val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))
            ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                val long = maxOf(info.size.width, info.size.height)
                if (long > maxPx) {
                    val scale = maxPx.toFloat() / long
                    decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
                }
            } as? android.graphics.drawable.AnimatedImageDrawable
        }
    } catch (e: Exception) {
        null
    }

    /**
     * A small preview of a picture or a video's first frame, or null if there isn't one. A
     * video's frame comes from Android's own media reader, for any video the app can open.
     */
    fun preview(context: Context, uri: String, type: String, maxPx: Int = 384): ImageBitmap? = try {
        if (type.startsWith("video/")) {
            android.media.MediaMetadataRetriever().use { reader ->
                reader.setDataSource(context, Uri.parse(uri))
                reader.getScaledFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxPx, maxPx)?.asImageBitmap()
            }
        } else {
            thumbnail(context, uri, maxPx)
        }
    } catch (e: Exception) {
        null
    }

    /**
     * A picture made small enough for a picture message: at most [maxPx] on its long side, as a
     * JPEG under [maxBytes]. Null if it can't be read or made small enough.
     */
    fun forSending(context: Context, uri: Uri, maxBytes: Int, maxPx: Int = 1600): ByteArray? = try {
        var size = maxPx
        var made: ByteArray? = null
        while (made == null && size >= 320) {
            val bitmap = decode(context, uri, size, flatten = true) ?: break
            for (quality in intArrayOf(85, 70, 55, 40)) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= maxBytes) {
                    made = out.toByteArray()
                    break
                }
            }
            bitmap.recycle()
            size = size * 3 / 4
        }
        made
    } catch (e: Exception) {
        null
    }

    /**
     * The picture, upright, at most [maxPx] on its long side. Android's image decoder turns it
     * the way the camera meant (the "orientation" tag in a photo), which the older decoder
     * ignored, so phone photos came out sideways. With [flatten], see-through parts (PNG, GIF)
     * go on white, so they don't turn black as a JPEG.
     */
    private fun decode(context: Context, uri: Uri, maxPx: Int, flatten: Boolean = false): Bitmap? {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val long = maxOf(info.size.width, info.size.height)
            if (long > maxPx) {
                val scale = maxPx.toFloat() / long
                decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
            }
            // Software, so it can be drawn on and compressed.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        if (!flatten || !decoded.hasAlpha()) return decoded
        val flat = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(decoded, 0f, 0f, null)
        }
        decoded.recycle()
        return flat
    }
}
