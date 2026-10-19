package io.github.secnewt.messages.settings

import android.app.NotificationManager
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log

/** Says who a message is from, with the phone's text-to-speech voice, then lets the voice go. */
object Speaker {

    private var engine: TextToSpeech? = null

    fun announce(context: Context, text: String) {
        val app = context.applicationContext
        val settings = SettingsStore(app).settings()
        val audio = app.getSystemService(AudioManager::class.java)
        val headphones = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type in setOf(
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET,
            )
        }
        val dnd = app.getSystemService(NotificationManager::class.java).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        val speak = Speaking.shouldSpeak(settings.speak, settings.quietDuringDnd, audio.ringerMode == AudioManager.RINGER_MODE_NORMAL, headphones, dnd)
        if (!speak) return
        Log.i("NewtSpeak", "Announcing a message")
        engine?.shutdown()
        var tts: TextToSpeech? = null
        tts = TextToSpeech(app) { status ->
            val voice = tts ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) {
                voice.shutdown()
                return@TextToSpeech
            }
            voice.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) = voice.shutdown()
                @Deprecated("Replaced by onError(String, Int)")
                override fun onError(id: String?) = voice.shutdown()
            })
            voice.speak(text, TextToSpeech.QUEUE_FLUSH, null, "message")
        }
        engine = tts
    }
}
