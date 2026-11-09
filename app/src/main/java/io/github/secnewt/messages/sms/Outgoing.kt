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
        val pictures = attachments.count { it.type.startsWith("image/") }
        val others = attachments.filterNot { it.type.startsWith("image/") }.map { file ->
            val bytes = runCatching { context.contentResolver.openInputStream(Uri.parse(file.uri))?.use { it.readBytes() } }.getOrNull()
                ?: return "Couldn't read ${file.name}."
            Mms.Part(bytes, file.type, file.name)
        }
        val left = room - others.sumOf { it.bytes.size }
        if (left < 0 || (pictures > 0 && left < 20_000 * pictures)) {
            return "Too big for a picture message: your carrier allows about ${limit / 1024} KB in all."
        }
        // Pictures are shrunk to share what's left.
        val shrunk = attachments.filter { it.type.startsWith("image/") }.map { picture ->
            val bytes = Pictures.forSending(context, Uri.parse(picture.uri), maxBytes = left / pictures)
                ?: return "${picture.name} couldn't be made small enough to send."
            Mms.Part(bytes, "image/jpeg", picture.name.substringBeforeLast('.') + ".jpg")
        }
        Mms.send(context, to, text, shrunk + others)
        return null
    }

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
