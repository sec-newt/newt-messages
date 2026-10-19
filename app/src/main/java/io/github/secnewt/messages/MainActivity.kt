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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import io.github.secnewt.messages.chats.GroupNames
import io.github.secnewt.messages.chats.Message
import io.github.secnewt.messages.mms.Mms
import io.github.secnewt.messages.ui.Pictures
import android.net.Uri
import android.telephony.SmsManager
import android.widget.Toast
import io.github.secnewt.messages.notify.Notifier
import io.github.secnewt.messages.sms.MessageStore
import io.github.secnewt.messages.sms.SmsSender
import io.github.secnewt.messages.ui.ChatDetailsScreen
import io.github.secnewt.messages.ui.ChatListScreen
import android.provider.ContactsContract
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
    private var isDefault by mutableStateOf(false)
    private var showDetails by mutableStateOf(false)
    // Where a number chosen from contacts goes (the Add someone dialog).
    private var picked: ((String) -> Unit)? = null
    private val pickNumber = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        val number = contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
        if (number != null) picked?.invoke(number)
        picked = null
    }
    private var startDraft by mutableStateOf("")
    // Who an "sms:" link was for, so a brand-new chat (no messages yet) can still open.
    private var startPeople by mutableStateOf<List<String>>(emptyList())
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
        isDefault = isMessagingApp()
        setContent {
            NewtTheme {
                if (isDefault) Messages() else WelcomeScreen(onMakeDefault = ::askToBeDefault)
            }
        }
        askForPermissions()
    }

    // Android shows its own "Use Newt Messages as your SMS app?" question.
    private val becomeDefault = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = isMessagingApp()
        askForPermissions()
    }

    private fun askToBeDefault() {
        becomeDefault.launch(getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_SMS))
    }

    private fun askForPermissions() {
        if (!isDefault) return
        val wanted = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_PHONE_NUMBERS)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) requestPermissions(wanted.toTypedArray(), 1)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        isDefault = isMessagingApp()
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
        startPeople = address.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        openThread = Telephony.Threads.getOrCreateThreadId(this, startPeople.toSet())
        startDraft = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
    }

    @androidx.compose.runtime.Composable
    private fun Messages() {
        val store = MessageStore(this)
        var tab by rememberSaveable { mutableStateOf(ChatTab.PEOPLE) }
        val chats by produceState(emptyList<Chat>(), version) { value = withContext(Dispatchers.IO) { store.chats() } }
        val thread = openThread
        val chat = chats.firstOrNull { it.threadId == thread }
            ?: thread?.takeIf { startPeople.isNotEmpty() }?.let {
                Chat(it, startPeople.map(store::person), "", System.currentTimeMillis(), name = GroupNames(this).name(it))
            }
        if (thread == null || chat == null) {
            ChatListScreen(
                chats, tab, onTab = { tab = it }, onOpen = { openThread = it.threadId }, time = ::time,
                onDelete = { chat -> Thread { delete(chat.threadId) }.start() },
            )
            return
        }
        var draft by rememberSaveable(thread) { mutableStateOf(startDraft) }
        // One picture at a time, picked with Android's photo picker (no photo permission needed).
        var picture by rememberSaveable(thread) { mutableStateOf<String?>(null) }
        val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) picture = uri.toString()
        }
        val messages by produceState(emptyList<Message>(), thread, version) { value = withContext(Dispatchers.IO) { store.messages(thread) } }
        LaunchedEffect(thread, version) {
            withContext(Dispatchers.IO) {
                store.markRead(thread)
                Notifier(this@MainActivity).cancel(thread)
            }
        }
        if (showDetails) {
            BackHandler { showDetails = false }
            ChatDetailsScreen(
                chat,
                onBack = { showDetails = false },
                onText = { person -> open(listOf(person.address)) },
                onCall = { person -> startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", person.address, null))) },
                onAddToContacts = { person ->
                    startActivity(
                        Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
                            .putExtra(ContactsContract.Intents.Insert.PHONE, person.address),
                    )
                },
                onRename = { name ->
                    GroupNames(this).rename(thread, name)
                    version++
                },
                onNewMembers = { numbers -> open(numbers, carryName = chat.name) },
                onPickContact = { done ->
                    picked = done
                    pickNumber.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                },
                onDelete = {
                    showDetails = false
                    openThread = null
                    Thread { delete(thread) }.start()
                },
            )
            return
        }
        BackHandler { openThread = null }
        ChatScreen(
            chat, messages, draft,
            onDraftChange = { draft = it },
            onSend = {
                val text = draft.trim()
                val attached = picture
                if (text.isNotEmpty() || attached != null) {
                    draft = ""
                    startDraft = ""
                    picture = null
                    Thread { send(chat, text, attached) }.start()
                }
            },
            onBack = { openThread = null },
            time = ::time,
            onDownload = { message -> Thread { retry(message) }.start() },
            onAttach = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            attached = picture?.let { "1 picture" },
            onRemoveAttachment = { picture = null },
            onSave = { uri, name, type -> Thread { save(uri, name, type) }.start() },
            onDetails = { showDetails = true },
        )
    }

    /** One-to-one words go as a text; groups and pictures go as a picture message (MMS). */
    private fun send(chat: Chat, text: String, picture: String?) {
        val to = chat.people.map { it.address }
        if (!chat.isGroup && picture == null) {
            SmsSender.send(this, to.first(), text)
            return
        }
        val images = picture?.let { uri ->
            val limit = getSystemService(SmsManager::class.java).carrierConfigValues
                .getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, 300 * 1024)
            // Leave room for the words and the message's own headers.
            val bytes = Pictures.forSending(this, Uri.parse(uri), maxBytes = limit * 9 / 10 - text.length - 2048)
            if (bytes == null) {
                runOnUiThread { Toast.makeText(this, "That picture couldn't be made small enough to send.", Toast.LENGTH_LONG).show() }
                return
            }
            listOf(bytes to "image/jpeg")
        }.orEmpty()
        Mms.send(this, to, text, images)
    }

    /** Opens the chat with exactly these people, starting it if it's new. */
    private fun open(numbers: List<String>, carryName: String? = null) {
        val people = numbers.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (people.isEmpty()) return
        val thread = Telephony.Threads.getOrCreateThreadId(this, people.toSet())
        // A group that changed members keeps its name, unless the new group already has one.
        if (carryName != null && people.size > 1 && GroupNames(this).name(thread) == null) GroupNames(this).rename(thread, carryName)
        startPeople = people
        startDraft = ""
        showDetails = false
        openThread = thread
        version++
    }

    private fun save(uri: String, name: String, type: String) {
        // A picture's real type (PNG, GIF…) comes from the message store.
        val real = contentResolver.getType(Uri.parse(uri))?.takeIf { it.contains('/') } ?: type
        val where = io.github.secnewt.messages.chats.Saving.save(this, uri, name, real)
        runOnUiThread {
            Toast.makeText(this, if (where != null) "Saved to $where" else "Couldn't save it", Toast.LENGTH_LONG).show()
        }
    }

    private fun delete(threadId: Long) {
        val removed = MessageStore(this).deleteChat(threadId)
        Notifier(this).cancel(threadId)
        android.util.Log.i("NewtSms", "Deleted a chat ($removed messages)")
    }

    private fun retry(message: Message) {
        val (notice, location) = MessageStore(this).waitingNotice(message.id) ?: return
        Mms.download(this, notice, location, -1)
    }

    private fun time(millis: Long): String =
        if (DateUtils.isToday(millis)) DateFormat.getTimeFormat(this).format(Date(millis))
        else DateUtils.formatDateTime(this, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)

    companion object {
        const val EXTRA_THREAD = "thread"
    }
}
