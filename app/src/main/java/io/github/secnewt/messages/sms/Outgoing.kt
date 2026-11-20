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
            val bytes = readUpTo(context, file.uri, Int.MAX_VALUE.toLong()) ?: return "Couldn't read ${file.name}."
            Mms.Part(bytes, file.type, file.name)
        }
        var left = room - others.sumOf { it.bytes.size }
        // Each picture needs at least a little room.
        val forVideos = left - 20_000L * pictures
        if (left < 0 || forVideos < 0) {
            return "Too big for a picture message: your carrier allows about ${limit / 1024} KB in all."
        }
        // Videos share what's left (leaving the pictures their bit). One that already fits goes as
        // it is; one too big is made smaller, and if that fails, the message says why.
        val shrunkVideos = videos.map { video ->
            val share = (if (pictures > 0) forVideos * 7 / 10 else forVideos) / videos.size
            val asIs = readUpTo(context, video.uri, share)
            if (asIs != null) {
                Mms.Part(asIs, video.type, video.name)
            } else {
                when (val made = io.github.secnewt.messages.ui.Videos.shrink(context.applicationContext, Uri.parse(video.uri), share)) {
                    is io.github.secnewt.messages.ui.Videos.Result.Made -> Mms.Part(made.bytes, "video/mp4", video.name.substringBeforeLast('.') + ".mp4")
                    io.github.secnewt.messages.ui.Videos.Result.TooLong -> return "${video.name} is too long for a picture message. " +
                        "About ${io.github.secnewt.messages.chats.VideoPlan.longestSeconds(share)} seconds fits."
                    io.github.secnewt.messages.ui.Videos.Result.Failed -> return "Couldn't make ${video.name} small enough to send. " +
                        "Try a shorter video."
                }
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

    /**
     * A file's bytes if there are at most [limit] of them; null if there are more, or it can't
     * be read. Stops reading past the limit, so a long video isn't loaded whole just to find out.
     */
    private fun readUpTo(context: Context, uri: String, limit: Long): ByteArray? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > limit) return@use null
            }
            out.toByteArray()
        }
    }.getOrNull()

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
