package io.github.secnewt.messages.notify

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.secnewt.messages.ui.Pictures
import java.io.File

/**
 * Small copies of received pictures for notifications. The notification shade can't read the
 * message store, so it gets a shrunk copy from the app's own cache, readable only by the
 * shade (Android's System UI). Copies older than two days are cleared.
 */
object NotifyPictures {

    private const val KEEP_MILLIS = 2 * 24 * 3_600_000L
    private const val SYSTEM_UI = "com.android.systemui"

    /** A copy of [picture] the shade can show, or null if it couldn't be made. */
    fun share(context: Context, picture: String): Uri? = try {
        val dir = File(context.cacheDir, "notify").apply { mkdirs() }
        val now = System.currentTimeMillis()
        dir.listFiles()?.filter { now - it.lastModified() > KEEP_MILLIS }?.forEach { it.delete() }
        // One copy per picture, named from a SHA-256 of its address: never two pictures in one file.
        val name = java.security.MessageDigest.getInstance("SHA-256").digest(picture.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(32)
        val file = File(dir, "p$name.jpg")
        if (!file.exists()) {
            val bytes = Pictures.forSending(context, Uri.parse(picture), maxBytes = 400_000, maxPx = 1024) ?: return null
            file.writeBytes(bytes)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.mmsfiles", file)
        context.grantUriPermission(SYSTEM_UI, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        uri
    } catch (e: Exception) {
        null
    }
}
