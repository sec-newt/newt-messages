package io.github.secnewt.messages.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * "Reply with a message" from the phone app's incoming-call screen. Android requires every
 * default messaging app to handle it. Only the phone app (which holds
 * SEND_RESPOND_VIA_MESSAGE) can start this.
 */
class RespondService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val to = intent?.data?.schemeSpecificPart?.substringBefore('?')
        if (!text.isNullOrBlank() && !to.isNullOrBlank()) {
            to.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { SmsSender.send(this, it, text) }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
