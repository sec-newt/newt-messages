package io.github.secnewt.messages

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.secnewt.messages.chats.Chat
import io.github.secnewt.messages.chats.ChatTab
import io.github.secnewt.messages.chats.Message
import io.github.secnewt.messages.notify.Notifier
import io.github.secnewt.messages.sms.MessageStore
import io.github.secnewt.messages.sms.SmsSender
import io.github.secnewt.messages.ui.ChatListScreen
import io.github.secnewt.messages.ui.ChatScreen
import io.github.secnewt.messages.ui.WelcomeScreen
import io.github.secnewt.ui.NewtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

class MainActivity : ComponentActivity() {

    // Goes up whenever the message store changes, so the screens reload.
    private var version by mutableIntStateOf(0)
    private var openThread by mutableStateOf<Long?>(null)
    private var startDraft by mutableStateOf("")
    private val changes = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            version++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        contentResolver.registerContentObserver(Telephony.MmsSms.CONTENT_URI, true, changes)
        setContent {
            NewtTheme {
                if (isMessagingApp()) Messages() else WelcomeScreen()
            }
        }
        if (isMessagingApp()) {
            val wanted = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS)
                .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (wanted.isNotEmpty()) requestPermissions(wanted.toTypedArray(), 1)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        version++
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(changes)
        super.onDestroy()
    }

    private fun isMessagingApp(): Boolean = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)

    /** Opens a chat from a notification, or from an "sms:" link another app sent. */
    private fun handle(intent: Intent?) {
        intent ?: return
        val thread = intent.getLongExtra(EXTRA_THREAD, -1)
        if (thread >= 0) {
            openThread = thread
            return
        }
        val address = intent.data?.takeIf { it.scheme in setOf("sms", "smsto", "mms", "mmsto") }
            ?.schemeSpecificPart?.substringBefore('?')?.takeIf { it.isNotBlank() } ?: return
        if (!isMessagingApp()) return
        openThread = Telephony.Threads.getOrCreateThreadId(this, address.split(',', ';').map { it.trim() }.toSet())
        startDraft = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
    }

    @androidx.compose.runtime.Composable
    private fun Messages() {
        val store = MessageStore(this)
        var tab by rememberSaveable { mutableStateOf(ChatTab.PEOPLE) }
        val chats by produceState(emptyList<Chat>(), version) { value = withContext(Dispatchers.IO) { store.chats() } }
        val thread = openThread
        val chat = chats.firstOrNull { it.threadId == thread }
        if (thread == null || chat == null) {
            ChatListScreen(chats, tab, onTab = { tab = it }, onOpen = { openThread = it.threadId }, time = ::time)
            return
        }
        var draft by rememberSaveable(thread) { mutableStateOf(startDraft) }
        val messages by produceState(emptyList<Message>(), thread, version) { value = withContext(Dispatchers.IO) { store.messages(thread) } }
        LaunchedEffect(thread, version) {
            withContext(Dispatchers.IO) {
                store.markRead(thread)
                Notifier(this@MainActivity).cancel(thread)
            }
        }
        BackHandler { openThread = null }
        ChatScreen(
            chat, messages, draft,
            onDraftChange = { draft = it },
            onSend = {
                val text = draft.trim()
                // Group texts need MMS, which comes next; one-to-one texts send now.
                if (text.isNotEmpty() && !chat.isGroup) {
                    draft = ""
                    startDraft = ""
                    Thread { SmsSender.send(this, chat.people.first().address, text) }.start()
                }
            },
            onBack = { openThread = null },
            time = ::time,
            cantSend = if (chat.isGroup) "Sending to groups comes with picture messages (MMS), in the next update." else null,
        )
    }

    private fun time(millis: Long): String =
        if (DateUtils.isToday(millis)) DateFormat.getTimeFormat(this).format(Date(millis))
        else DateUtils.formatDateTime(this, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)

    companion object {
        const val EXTRA_THREAD = "thread"
    }
}
