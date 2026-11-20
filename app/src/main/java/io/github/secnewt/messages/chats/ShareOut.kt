package io.github.secnewt.messages.chats

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Share: hands a picture from a message to another app (email, a photo editor, another
 * messenger) through Android's share sheet.
 *
 * Other apps can't read the message store, so the picture is copied into one small folder
 * that only exists for this: cache/share/. Each Share empties it first, so only the last
 * shared picture is ever kept there, and Android can clear it whenever it likes.
 */
object ShareOut {

    /**
     * The share sheet for [from], or null if it couldn't be read. Run it off the main thread.
     * [link] turns the copy into an address the chosen app is allowed to read.
     */
    fun intent(
        context: Context, from: String, name: String, type: String,
        link: (File) -> Uri = { FileProvider.getUriForFile(context, context.packageName + ".mmsfiles", it) },
    ): Intent? = try {
        val folder = File(context.cacheDir, "share")
        folder.deleteRecursively()
        folder.mkdirs()
        val file = File(folder, Saving.fileName(name, type))
        val copied = context.contentResolver.openInputStream(Uri.parse(from))?.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        if (copied == null) {
            null
        } else {
            val uri = link(file)
            val send = Intent(Intent.ACTION_SEND)
                .setType(type)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // The picture shows at the top of the share sheet, and the app picked can read it.
            send.clipData = ClipData.newRawUri(null, uri)
            Intent.createChooser(send, "Share picture")
        }
    } catch (e: Exception) {
        android.util.Log.w("NewtShare", "Couldn't get a picture ready to share", e)
        null
    }
}
