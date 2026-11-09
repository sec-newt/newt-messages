package io.github.secnewt.messages.sms

import android.content.Context
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import io.github.secnewt.messages.chats.Attachment
import io.github.secnewt.messages.chats.Message
import io.github.secnewt.messages.mms.Mms
import io.github.secnewt.messages.ui.Pictures

/**
 * Sending, from the chat screen and from a notification alike. One-to-one words go as a text;
 * groups and attachments go as a picture message (MMS). Run it off the main thread.
 */
object Outgoing {

    /**
     * Sends, or says why it couldn't: null when it's on its way, otherwise words for the
     * person. When it couldn't, nothing was saved, so the words and attachments should go back
     * in the message box. (Once saved, a failure shows on the message itself, with Try again.)
     */
    fun send(context: Context, to: List<String>, text: String, attachments: List<Attachment> = emptyList()): String? = try {
        prepareAndSend(context, to, text, attachments)
    } catch (e: Exception) {
        Log.e("NewtSms", "Couldn't send", e)
        "Couldn't send that message. It's back in the message box."
    }

    private fun prepareAndSend(context: Context, to: List<String>, text: String, attachments: List<Attachment>): String? {
        if (to.isEmpty()) return "Nobody to send it to."
        if (to.size == 1 && attachments.isEmpty()) {
            SmsSender.send(context, to.first(), text)
            return null
        }
        val limit = context.getSystemService(SmsManager::class.java).carrierConfigValues
            .getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, 300 * 1024)
        // Leave room for the words and the message's own headers.
        val room = limit * 9 / 10 - text.toByteArray().size - 2048
        fun isPicture(a: Attachment) = a.type.startsWith("image/")
        fun isVideo(a: Attachment) = a.type.startsWith("video/")
        val pictures = attachments.count(::isPicture)
        val videos = attachments.filter(::isVideo)
        // Files that are sent as they are: documents, contact cards, sound.
        val others = attachments.filterNot { isPicture(it) || isVideo(it) }.map { file ->
            val bytes = runCatching { context.contentResolver.openInputStream(Uri.parse(file.uri))?.use { it.readBytes() } }.getOrNull()
                ?: return "Couldn't read ${file.name}."
            Mms.Part(bytes, file.type, file.name)
        }
        var left = room - others.sumOf { it.bytes.size }
        // Each picture needs at least a little room.
        val forVideos = left - 20_000L * pictures
        if (left < 0 || forVideos < 0) {
            return "Too big for a picture message: your carrier allows about ${limit / 1024} KB in all."
        }
        // Videos share what's left (leaving the pictures their bit); a video too big is made smaller.
        val shrunkVideos = videos.map { video ->
            val share = (if (pictures > 0) forVideos * 7 / 10 else forVideos) / videos.size
            val size = sizeOf(context, Uri.parse(video.uri))
            if (size != null && size <= share) {
                val bytes = runCatching { context.contentResolver.openInputStream(Uri.parse(video.uri))?.use { it.readBytes() } }.getOrNull()
                    ?: return "Couldn't read ${video.name}."
                Mms.Part(bytes, video.type, video.name)
            } else {
                val bytes = io.github.secnewt.messages.ui.Videos.shrink(context, Uri.parse(video.uri), share)
                    ?: return "${video.name} is too long for a picture message. " +
                        "About ${io.github.secnewt.messages.chats.VideoPlan.longestSeconds(share)} seconds fits."
                Mms.Part(bytes, "video/mp4", video.name.substringBeforeLast('.') + ".mp4")
            }
        }
        left -= shrunkVideos.sumOf { it.bytes.size }
        // Pictures are shrunk to share what's left.
        val shrunk = attachments.filter(::isPicture).map { picture ->
            val bytes = Pictures.forSending(context, Uri.parse(picture.uri), maxBytes = (left / pictures).toInt())
                ?: return "${picture.name} couldn't be made small enough to send."
            Mms.Part(bytes, "image/jpeg", picture.name.substringBeforeLast('.') + ".jpg")
        } + shrunkVideos
        Mms.send(context, to, text, shrunk + others)
        return null
    }

    /** A file's size in bytes, if the app that has it says. */
    private fun sizeOf(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }
    }.getOrNull()?.takeIf { it > 0 }

    /** Tries a message that failed again. Null when it's on its way, otherwise why not. */
    fun retry(context: Context, message: Message): String? = try {
        if (message.id < 0) {
            Mms.resend(context, android.net.Uri.withAppendedPath(android.provider.Telephony.Mms.CONTENT_URI, (-message.id).toString()))
        } else {
            SmsSender.resend(context, message.id)
        }
        null
    } catch (e: Exception) {
        Log.e("NewtSms", "Couldn't try again", e)
        "Couldn't try that message again."
    }
}
