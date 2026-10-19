package io.github.secnewt.messages.chats

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap

/**
 * Saves a picture or file from a message where other apps can find it: pictures to
 * Pictures/Newt Messages, everything else to Download/Newt Messages. Uses Android's media
 * store, so no storage permission is needed.
 */
object Saving {

    /** Where it went ("Pictures/Newt Messages"), or null if it couldn't be saved. */
    fun save(context: Context, from: String, name: String, type: String): String? {
        val picture = type.startsWith("image/")
        val folder = (if (picture) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS) + "/Newt Messages"
        val collection = if (picture) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName(name, type))
            put(MediaStore.MediaColumns.MIME_TYPE, type)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val target = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openInputStream(Uri.parse(from)).use { input ->
                resolver.openOutputStream(target).use { output ->
                    if (input == null || output == null) error("couldn't open")
                    input.copyTo(output)
                }
            }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            folder
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            null
        }
    }

    /** Just the last part of a name, with only plain characters, nothing hidden, at most 80 long. */
    fun cleanName(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_").trim('.', ' ').take(80)

    /**
     * A safe file name: no folders, nothing hidden, and an extension that matches the type.
     * Names come from whoever sent the message, so they're never trusted as paths.
     */
    fun fileName(name: String, type: String): String {
        val base = cleanName(name).ifEmpty { "attachment" }
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type)
            ?: when (type) {
                "image/jpeg" -> "jpg"
                "text/x-vcard", "text/vcard" -> "vcf"
                else -> null
            }
        return if (extension == null || base.lowercase().endsWith(".$extension")) base else "$base.$extension"
    }
}
