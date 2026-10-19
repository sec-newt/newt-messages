package io.github.secnewt.messages.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
     * A picture made small enough for a picture message: at most [maxPx] on its long side, as a
     * JPEG under [maxBytes]. Null if it can't be read or made small enough.
     */
    fun forSending(context: Context, uri: Uri, maxBytes: Int, maxPx: Int = 1600): ByteArray? {
        var size = maxPx
        while (size >= 320) {
            val bitmap = decode(context, uri, size) ?: return null
            for (quality in intArrayOf(85, 70, 55, 40)) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= maxBytes) return out.toByteArray()
            }
            size = size * 3 / 4
        }
        return null
    }

    private fun decode(context: Context, uri: Uri, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val long = maxOf(bitmap.width, bitmap.height)
        if (long <= maxPx) return bitmap
        val scale = maxPx.toFloat() / long
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    }
}
