package io.github.secnewt.messages.mms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.FileProvider
import io.github.secnewt.messages.mms.pdu.AcknowledgeInd
import io.github.secnewt.messages.mms.pdu.CharacterSets
import io.github.secnewt.messages.mms.pdu.EncodedStringValue
import io.github.secnewt.messages.mms.pdu.NotificationInd
import io.github.secnewt.messages.mms.pdu.PduBody
import io.github.secnewt.messages.mms.pdu.PduComposer
import io.github.secnewt.messages.mms.pdu.PduHeaders
import io.github.secnewt.messages.mms.pdu.PduParser
import io.github.secnewt.messages.mms.pdu.PduPart
import io.github.secnewt.messages.mms.pdu.PduPersister
import io.github.secnewt.messages.mms.pdu.RetrieveConf
import io.github.secnewt.messages.mms.pdu.SendReq
import io.github.secnewt.messages.notify.Notifier
import java.io.File
import java.util.UUID
import kotlin.concurrent.thread

/** The app's own file share, limited to cache/mms/ (see res/xml/mms_paths.xml). */
class MmsFileProvider : FileProvider()

/**
 * Picture and group messages (MMS). Android's own MMS service does every network step
 * (downloading, sending, and the "got it" acknowledgement to the carrier) over the phone's
 * mobile data, so this app needs no internet permission. This code only reads and writes the
 * message format and keeps the shared message store up to date.
 */
object Mms {

    private const val TAG = "NewtMms"
    private const val PHONE = "com.android.phone"

    /** A picture message is waiting at the carrier: save the notice, then fetch it. */
    fun onPush(context: Context, data: ByteArray, subscription: Int) {
        val notice = PduParser(data, true).parse() as? NotificationInd ?: run {
            Log.w(TAG, "A push that isn't an MMS notice; ignored")
            return
        }
        val location = notice.getContentLocation()?.let { String(it) } ?: return
        if (alreadyHave(context, location)) {
            Log.i(TAG, "Duplicate MMS notice; ignored")
            return
        }
        // Saved first, so a failed download still shows in the chat with a Retry button.
        val noticeUri = PduPersister.getPduPersister(context)
            .persist(notice, Telephony.Mms.Inbox.CONTENT_URI, true, true, null, subscription)
        Log.i(TAG, "Saved an MMS notice; downloading")
        download(context, noticeUri, location, subscription)
    }

    /** Fetches a waiting message (also used by Retry). */
    fun download(context: Context, noticeUri: Uri, location: String, subscription: Int) {
        val file = newFile(context, "in")
        val share = share(context, file)
        context.grantUriPermission(PHONE, share, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val done = Intent(context, MmsResultReceiver::class.java)
            .setAction(MmsResultReceiver.DOWNLOADED)
            .putExtra(MmsResultReceiver.EXTRA_FILE, file.name)
            .putExtra(MmsResultReceiver.EXTRA_NOTICE, noticeUri.toString())
            .putExtra(MmsResultReceiver.EXTRA_SUBSCRIPTION, subscription)
        val pending = PendingIntent.getBroadcast(context, file.name.hashCode(), done, PendingIntent.FLAG_IMMUTABLE)
        manager(context, subscription).downloadMultimediaMessage(context, location, share, null, pending)
    }

    /** The download finished: save the message in place of its notice, acknowledge it, notify. */
    fun onDownloaded(context: Context, ok: Boolean, fileName: String, noticeUri: Uri, subscription: Int) {
        val file = File(dir(context), fileName)
        try {
            val bytes = if (ok && file.exists()) file.readBytes() else null
            val message = bytes?.let { PduParser(it, true).parse() } as? RetrieveConf
            if (message == null) {
                // The notice stays in the chat with a Download button, so nothing is lost.
                Log.w(TAG, "An MMS download failed (ok=$ok); its notice stays for a retry")
                threadOf(context, noticeUri)?.let { Notifier(context).mmsFailed(it) }
                return
            }
            val persister = PduPersister.getPduPersister(context)
            val uri = persister.persist(message, Telephony.Mms.Inbox.CONTENT_URI, true, true, null, subscription)
            context.contentResolver.delete(noticeUri, null, null)
            val values = ContentValues().apply {
                put(Telephony.Mms.READ, 0)
                put(Telephony.Mms.SEEN, 0)
                put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000)
            }
            context.contentResolver.update(uri, values, null, null)
            Log.i(TAG, "Stored an incoming MMS")
            message.getTransactionId()?.let { acknowledge(context, it, subscription) }
            threadOf(context, uri)?.let { Notifier(context).update(it, announce = true) }
        } finally {
            file.delete()
        }
    }

    /** Sends a text and/or pictures to one person or a group. */
    fun send(context: Context, to: List<String>, text: String, images: List<Pair<ByteArray, String>> = emptyList(), subscription: Int = -1) {
        val request = SendReq().apply {
            setTo(to.map { EncodedStringValue(it) }.toTypedArray())
            setDate(System.currentTimeMillis() / 1000)
            setBody(
                PduBody().apply {
                    if (text.isNotBlank()) addPart(textPart(text))
                    images.forEachIndexed { i, (bytes, type) -> addPart(imagePart(bytes, type, i)) }
                },
            )
        }
        val bytes = PduComposer(context, request).make() ?: run {
            Log.e(TAG, "Couldn't build an MMS")
            return
        }
        request.setMessageSize(bytes.size.toLong())
        val saved = PduPersister.getPduPersister(context)
            .persist(request, Telephony.Mms.Outbox.CONTENT_URI, true, true, null, subscription)
        val file = newFile(context, "out").apply { writeBytes(bytes) }
        val share = share(context, file)
        context.grantUriPermission(PHONE, share, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val done = Intent(context, MmsResultReceiver::class.java)
            .setAction(MmsResultReceiver.SENT)
            .putExtra(MmsResultReceiver.EXTRA_FILE, file.name)
            .putExtra(MmsResultReceiver.EXTRA_MESSAGE, saved.toString())
        // Mutable: Android adds the carrier's reply to it.
        val pending = PendingIntent.getBroadcast(context, file.name.hashCode(), done, PendingIntent.FLAG_MUTABLE)
        val overrides = Bundle().apply { putBoolean(SmsManager.MMS_CONFIG_GROUP_MMS_ENABLED, true) }
        manager(context, subscription).sendMultimediaMessage(context, share, null, overrides, pending)
        Log.i(TAG, "Sending an MMS to ${to.size} recipient(s)")
    }

    fun onSent(context: Context, ok: Boolean, fileName: String, message: Uri) {
        File(dir(context), fileName).delete()
        val box = if (ok) Telephony.Mms.MESSAGE_BOX_SENT else Telephony.Mms.MESSAGE_BOX_FAILED
        context.contentResolver.update(message, ContentValues().apply { put(Telephony.Mms.MESSAGE_BOX, box) }, null, null)
        Log.i(TAG, if (ok) "MMS sent" else "An MMS failed to send")
    }

    /** Tells the carrier the message arrived, so it isn't sent again. */
    private fun acknowledge(context: Context, transactionId: ByteArray, subscription: Int) {
        try {
            val ack = AcknowledgeInd(PduHeaders.CURRENT_MMS_VERSION, transactionId)
            val bytes = PduComposer(context, ack).make() ?: return
            val file = newFile(context, "ack").apply { writeBytes(bytes) }
            val share = share(context, file)
            context.grantUriPermission(PHONE, share, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val done = Intent(context, MmsResultReceiver::class.java).setAction(MmsResultReceiver.ACKNOWLEDGED)
                .putExtra(MmsResultReceiver.EXTRA_FILE, file.name)
            val pending = PendingIntent.getBroadcast(context, file.name.hashCode(), done, PendingIntent.FLAG_MUTABLE)
            manager(context, subscription).sendMultimediaMessage(context, share, null, null, pending)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't acknowledge an MMS", e)
        }
    }

    private fun textPart(text: String) = PduPart().apply {
        setCharset(CharacterSets.UTF_8)
        setContentType("text/plain".toByteArray())
        setContentLocation("text.txt".toByteArray())
        setContentId("<text>".toByteArray())
        setData(text.toByteArray(Charsets.UTF_8))
    }

    private fun imagePart(bytes: ByteArray, type: String, index: Int) = PduPart().apply {
        val name = "image$index." + (if (type == "image/png") "png" else "jpg")
        setContentType(type.toByteArray())
        setContentLocation(name.toByteArray())
        setContentId("<image$index>".toByteArray())
        setName(name.toByteArray())
        setData(bytes)
    }

    private fun alreadyHave(context: Context, location: String): Boolean =
        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI, arrayOf(Telephony.Mms._ID), "${Telephony.Mms.CONTENT_LOCATION} = ?", arrayOf(location), null,
        )?.use { it.count > 0 } ?: false

    fun threadOf(context: Context, uri: Uri): Long? =
        context.contentResolver.query(uri, arrayOf(Telephony.Mms.THREAD_ID), null, null, null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun manager(context: Context, subscription: Int): SmsManager {
        val manager = context.getSystemService(SmsManager::class.java)
        return if (subscription >= 0) manager.createForSubscriptionId(subscription) else manager
    }

    private fun dir(context: Context) = File(context.cacheDir, "mms").apply { mkdirs() }

    private fun newFile(context: Context, kind: String) = File(dir(context), "$kind-${UUID.randomUUID()}.pdu")

    private fun share(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".mmsfiles", file)
}

/** Android's MMS service reporting back on a download, a send, or an acknowledgement. */
class MmsResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ok = resultCode == android.app.Activity.RESULT_OK
        val file = intent.getStringExtra(EXTRA_FILE) ?: return
        val pending = goAsync()
        thread {
            try {
                when (intent.action) {
                    DOWNLOADED -> Mms.onDownloaded(
                        context, ok, file, Uri.parse(intent.getStringExtra(EXTRA_NOTICE) ?: return@thread),
                        intent.getIntExtra(EXTRA_SUBSCRIPTION, -1),
                    )
                    SENT -> Mms.onSent(context, ok, file, Uri.parse(intent.getStringExtra(EXTRA_MESSAGE) ?: return@thread))
                    ACKNOWLEDGED -> File(File(context.cacheDir, "mms"), file).delete()
                }
            } catch (e: Exception) {
                Log.e("NewtMms", "Couldn't finish an MMS step", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val DOWNLOADED = "io.github.secnewt.messages.MMS_DOWNLOADED"
        const val SENT = "io.github.secnewt.messages.MMS_SENT"
        const val ACKNOWLEDGED = "io.github.secnewt.messages.MMS_ACKNOWLEDGED"
        const val EXTRA_FILE = "file"
        const val EXTRA_NOTICE = "notice"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_SUBSCRIPTION = "subscription"
    }
}
