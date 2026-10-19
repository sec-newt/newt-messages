package io.github.secnewt.messages.settings

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log

/**
 * Says who a message is from (and, if turned on, what it says) with the phone's
 * text-to-speech voice. Works like Newt Phone's caller announcement: the voice is started on
 * the main thread, plays at the notification volume, and is let go when it's done.
 */
object Speaker {

    private const val TAG = "NewtSpeak"
    private const val TIMEOUT_MS = 20_000L
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null

    /** True if the settings and the phone's state say to speak now. */
    fun wanted(context: Context): Boolean {
        val settings = SettingsStore(context).settings()
        val audio = context.getSystemService(AudioManager::class.java)
        val headphones = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type in setOf(
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET,
            )
        }
        val dnd = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        return Speaking.shouldSpeak(settings.speak, settings.quietDuringDnd, audio.ringerMode == AudioManager.RINGER_MODE_NORMAL, headphones, dnd)
    }

    fun announce(context: Context, text: String) {
        val app = context.applicationContext
        if (!wanted(app)) return
        Log.i(TAG, "Announcing a message")
        speak(app, text)
    }

    /** Speaks [text] now, whatever the settings (used by the settings screen's Test button too). */
    fun speak(context: Context, text: String) {
        val app = context.applicationContext
        main.post {
            stop()
            var started: TextToSpeech? = null
            val give = Runnable { if (engine === started) stop() }
            started = TextToSpeech(app) { status ->
                val voice = started ?: return@TextToSpeech
                if (engine !== voice) return@TextToSpeech
                if (status != TextToSpeech.SUCCESS) {
                    Log.w(TAG, "No text-to-speech voice is available")
                    stop()
                    return@TextToSpeech
                }
                // The notification stream, so it follows the notification volume.
                voice.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                voice.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) = Unit
                    override fun onDone(id: String?) {
                        main.post { if (engine === voice) stop() }
                    }
                    @Deprecated("Replaced by onError(String, Int)")
                    override fun onError(id: String?) {
                        main.post { if (engine === voice) stop() }
                    }
                    override fun onError(id: String?, errorCode: Int) {
                        Log.w(TAG, "The voice couldn't speak (error $errorCode)")
                        main.post { if (engine === voice) stop() }
                    }
                })
                if (voice.speak(text, TextToSpeech.QUEUE_FLUSH, null, "message") != TextToSpeech.SUCCESS) stop()
            }
            engine = started
            // Never hold on to the voice if it never answers.
            main.postDelayed(give, TIMEOUT_MS)
        }
    }

    private fun stop() {
        engine?.let {
            it.stop()
            it.shutdown()
        }
        engine = null
    }
}
