package io.github.secnewt.messages.mms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/**
 * This phone's own numbers: the SIM a message came in on, the default texting SIM, and the
 * main line. Empty without READ_PHONE_NUMBERS, or when the SIMs don't say.
 */
object OwnNumbers {

    fun of(context: Context, subscription: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID): List<String> {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        return try {
            val subscriptions = context.getSystemService(SubscriptionManager::class.java)
            val fromSims = listOf(subscription, SubscriptionManager.getDefaultSmsSubscriptionId())
                .filter(SubscriptionManager::isValidSubscriptionId)
                .mapNotNull { subscriptions?.getPhoneNumber(it) }
            val line1 = context.getSystemService(TelephonyManager::class.java)?.line1Number
            (fromSims + listOfNotNull(line1)).filter { it.isNotBlank() }.distinct()
        } catch (_: SecurityException) {
            emptyList()
        }
    }
}
