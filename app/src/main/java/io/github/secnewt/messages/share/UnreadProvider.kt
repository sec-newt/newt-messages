package io.github.secnewt.messages.share

import android.content.ContentProvider
import android.content.ContentValues
import android.database.ContentObserver
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import io.github.secnewt.messages.reactions.Reactions

/**
 * Shares how many messages are unread with other apps signed with the Newt key (Newt Home
 * shows "3 unread" under Messages). Only the number: no names, numbers or words. Read-only,
 * and guarded by a signature permission, so no other app can read it.
 */
class UnreadProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val context = context ?: return false
        // Tell readers when messages change, while this process is running.
        try {
            context.contentResolver.registerContentObserver(
                Telephony.MmsSms.CONTENT_URI, true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        context.contentResolver.notifyChange(countUri(context.packageName), null)
                    }
                },
            )
        } catch (e: SecurityException) {
            // Not the messaging app (yet); readers ask again when they come back.
        }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        if (uri.path != "/count") return null
        return MatrixCursor(arrayOf(COLUMN)).apply { addRow(arrayOf(unread())) }
    }

    /** Unread texts that aren't reactions, plus unread picture and group messages. */
    private fun unread(): Int {
        val resolver = context?.contentResolver ?: return 0
        return try {
            val texts = resolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.BODY), "${Telephony.Sms.READ} = 0", null, null,
            )?.use { c -> buildList { while (c.moveToNext()) add(c.getString(0).orEmpty()) } }.orEmpty()
            val mms = resolver.query(Telephony.Mms.Inbox.CONTENT_URI, arrayOf(Telephony.Mms._ID), "${Telephony.Mms.READ} = 0", null, null)
                ?.use { it.count } ?: 0
            count(texts, mms)
        } catch (e: RuntimeException) {
            // Not the messaging app: nothing to count.
            0
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val COLUMN = "unread"

        /** Unread texts that aren't reactions, plus unread picture messages, capped at 999. */
        fun count(unreadTexts: List<String>, unreadMms: Int): Int =
            (unreadTexts.count { Reactions.parse(it) == null } + unreadMms).coerceIn(0, 999)
        fun countUri(packageName: String): Uri = Uri.parse("content://$packageName.unread/count")
    }
}
