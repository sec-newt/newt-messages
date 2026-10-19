package io.github.secnewt.messages.chats

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers

/**
 * Android's own block list, shared with Newt Phone and every other app. Android turns away
 * texts and calls from these numbers before any app sees them. Only the default messaging
 * (or phone) app may change it.
 */
class Blocking(private val context: Context) {

    fun isBlocked(number: String): Boolean = try {
        BlockedNumberContract.isBlocked(context, number)
    } catch (e: Exception) {
        false
    }

    /** True if it worked. */
    fun block(number: String): Boolean = try {
        context.contentResolver.insert(
            BlockedNumbers.CONTENT_URI, ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) },
        ) != null
    } catch (e: Exception) {
        false
    }

    fun unblock(number: String): Boolean = try {
        BlockedNumberContract.unblock(context, number) > 0
    } catch (e: Exception) {
        false
    }
}
