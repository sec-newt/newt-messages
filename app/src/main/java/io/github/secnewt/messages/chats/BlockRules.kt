package io.github.secnewt.messages.chats

import android.content.Context
import android.net.Uri

/**
 * Newt Phone's "starting with" block rules (like 800555), shared with Newt Messages so a number
 * blocked there is quiet here too. Exact numbers don't need this: they're on Android's own block
 * list, and Android turns those texts away before any app sees them. A text matching a rule is
 * still saved (nothing is lost), just already read, with no notification or announcement.
 */
object BlockRules {

    private val URI: Uri by lazy { Uri.parse("content://$AUTHORITY/startswith") }

    /** The starting digits Newt Phone blocks; empty without Newt Phone or its permission. */
    fun startsWith(context: Context): List<String> = try {
        if (!fromNewtPhone(context)) return emptyList()
        context.contentResolver.query(URI, null, null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) c.getString(0)?.let(::add) }
        }.orEmpty()
    } catch (e: Exception) {
        // Newt Phone isn't installed, or hasn't granted this yet.
        emptyList()
    }

    /**
     * Only Newt Phone itself, signed with the same Newt key as this app. Without Newt Phone,
     * another app could claim this provider's name and hand over rules that silence texts.
     */
    private fun fromNewtPhone(context: Context): Boolean {
        val pm = context.packageManager
        val provider = pm.resolveContentProvider(AUTHORITY, 0) ?: return false
        return provider.packageName == NEWT_PHONE &&
            pm.checkSignatures(context.packageName, NEWT_PHONE) == android.content.pm.PackageManager.SIGNATURE_MATCH
    }

    private const val NEWT_PHONE = "io.github.secnewt.dialer"
    private const val AUTHORITY = "$NEWT_PHONE.blockrules"

    /** Whether [sender] is blocked by one of [prefixes], compared exactly as Newt Phone does. */
    fun blocks(prefixes: List<String>, sender: String?): Boolean {
        val number = normalize(sender) ?: return false
        return prefixes.any { prefix -> normalize(prefix)?.let(number::startsWith) == true }
    }

    fun blocked(context: Context, sender: String?): Boolean = blocks(startsWith(context), sender)

    /** Digits only, a leading US/Canada 1 dropped from 11 digits (Newt Phone's PhoneNumbers.normalize). */
    private fun normalize(raw: String?): String? {
        val digits = raw?.filter { it.isDigit() }.orEmpty()
        if (digits.isEmpty()) return null
        return if (digits.length == 11 && digits.startsWith("1")) digits.substring(1) else digits
    }
}
