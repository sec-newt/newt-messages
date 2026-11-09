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
import android.net.Uri
import android.widget.Toast
import io.github.secnewt.messages.notify.Notifier
import io.github.secnewt.messages.sms.MessageStore
import io.github.secnewt.messages.sms.Outgoing
import io.github.secnewt.messages.ui.ChatDetailsScreen
import io.github.secnewt.messages.ui.ChatListScreen
import io.github.secnewt.messages.ui.NewChatScreen
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
    // One store for as long as the app is open, so contacts and lookups aren't redone on every
    // refresh; and the last chat list, shown straight away while a fresh one loads.
    private val messageStore by lazy { MessageStore(this) }
    private var lastChats by mutableStateOf(emptyList<Chat>())
    private var showDetails by mutableStateOf(false)
    private var newChat by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
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
    private val sending = java.util.concurrent.Executors.newSingleThreadExecutor()
    // Pictures, videos or words shared from another app, waiting for me to pick a chat.
    private var shared by mutableStateOf<List<io.github.secnewt.messages.chats.Attachment>>(emptyList())
    private var sharedText by mutableStateOf("")
    // A message that couldn't be sent, going back into its chat's message box.
    private data class Unsent(val thread: Long, val text: String, val attachments: List<io.github.secnewt.messages.chats.Attachment>)
    private var unsent by mutableStateOf<Unsent?>(null)
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
        // A contact may have been added or edited while we were away.
        messageStore.refreshContacts()
        version++
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(changes)
        super.onDestroy()
    }

    private fun isMessagingApp(): Boolean = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)

    /** Opens a chat from a notification, or from an "sms:" link another app sent; or takes a share. */
    private fun handle(intent: Intent?) {
        intent ?: return
        if (intent.action in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE) && intent.data == null) return takeShare(intent)
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

    /** A share from another app: keep what came, then show the chat list to pick where it goes. */
    private fun takeShare(intent: Intent) {
        val streams = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            androidx.core.content.IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            listOfNotNull(androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        }
        val fallback = intent.type?.takeIf { '*' !in it } ?: "application/octet-stream"
        shared = streams.filter(::safeToShare).map { describe(it, fallback, "file") }
        sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        if (shared.isEmpty() && sharedText.isBlank()) return
        openThread = null
        newChat = false
        showSettings = false
        showDetails = false
    }

    /**
     * Only files the sharing app could hand over itself. As the messaging app, Newt can read the
     * message store and contacts, so a share pointing there (or at Newt's own files, or a raw
     * file path) could slip private data into a message; those are dropped.
     */
    private fun safeToShare(uri: Uri): Boolean {
        if (uri.scheme != android.content.ContentResolver.SCHEME_CONTENT) return false
        val authority = uri.authority?.substringAfterLast('@') ?: return false
        // Providers that guard themselves in code rather than with a declared permission.
        val off = setOf(
            "sms", "mms", "mms-sms", "icc", "com.android.contacts", "contacts", "call_log",
            "com.android.blockednumber", "com.android.simphonebook",
        )
        if (authority.lowercase() in off) return false
        // An app Android hides from Newt can only reach it through the share's own grant; the
        // providers that matter here are system ones, which are always visible.
        val provider = packageManager.resolveContentProvider(authority, 0) ?: return true
        if (provider.packageName == packageName) return false
        // A provider locked behind a permission Newt holds (messages, contacts…) isn't the
        // sharing app's to hand over: Newt would be reading it on their behalf.
        val path = uri.path.orEmpty()
        val needs = listOfNotNull(provider.readPermission) +
            provider.pathPermissions.orEmpty().filter { it.match(path) }.mapNotNull { it.readPermission }
        return needs.none { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    @androidx.compose.runtime.Composable
    private fun Messages() {
        val store = messageStore
        var tab by rememberSaveable { mutableStateOf(ChatTab.PEOPLE) }
        // Reloaded on leaving a chat too, so a draft left there shows in the list.
        val chats by produceState(lastChats, version, openThread) {
            value = withContext(Dispatchers.IO) { store.chats() }.also { lastChats = it }
        }
        val thread = openThread
        val chat = chats.firstOrNull { it.threadId == thread }
            ?: thread?.takeIf { startPeople.isNotEmpty() }?.let {
                val people = startPeople.map(store::person)
                Chat(
                    it, people, "", System.currentTimeMillis(), name = GroupNames(this).name(it),
                    colors = io.github.secnewt.messages.chats.PersonColorStore(this).colors(it, people),
                )
            }
        if (showSettings) {
            BackHandler { showSettings = false }
            val store = io.github.secnewt.messages.settings.SettingsStore(this)
            var current by androidx.compose.runtime.remember { mutableStateOf(store.settings()) }
            // Read off the main thread; null until read, or when Android won't say.
            var blockedVersion by androidx.compose.runtime.remember { mutableStateOf(0) }
            val blocked by androidx.compose.runtime.produceState<List<String>?>(null, blockedVersion) {
                value = withContext(Dispatchers.IO) { io.github.secnewt.messages.chats.Blocking(this@MainActivity).all() }
            }
            val version = androidx.compose.runtime.remember {
                runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
            }
            io.github.secnewt.messages.ui.SettingsScreen(
                current,
                onChange = {
                    current = it
                    store.save(it)
                },
                onBack = { showSettings = false },
                blocked = blocked,
                onUnblock = { number ->
                    Thread {
                        val ok = io.github.secnewt.messages.chats.Blocking(this).unblock(number)
                        runOnUiThread {
                            if (!ok) Toast.makeText(this, "Android didn't allow that change.", Toast.LENGTH_LONG).show()
                            blockedVersion++
                        }
                    }.start()
                },
                version = version,
                onOpenVoiceSettings = { runCatching { startActivity(Intent("com.android.settings.TTS_SETTINGS")) } },
                onTestVoice = {
                    io.github.secnewt.messages.settings.Speaker.speak(
                        this,
                        io.github.secnewt.messages.settings.Speaking.text("Becca", "", null, if (current.readMessage) "Can you grab milk?" else null),
                    ) { spoke ->
                        if (!spoke) {
                            Toast.makeText(
                                this, "No voice found. Check Android's text-to-speech settings (Voice settings, below).", Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                },
            )
            return
        }
        if (newChat) {
            BackHandler { newChat = false }
            val contacts by produceState(emptyList<io.github.secnewt.messages.chats.ContactNumber>()) {
                value = withContext(Dispatchers.IO) { contactNumbers() }
            }
            NewChatScreen(contacts, onBack = { newChat = false }, onStart = { numbers ->
                newChat = false
                open(numbers)
            })
            return
        }
        if (thread == null || chat == null) {
            var query by rememberSaveable { mutableStateOf("") }
            val hits by produceState(emptyList<io.github.secnewt.messages.chats.SearchHit>(), query, version) {
                value = if (query.isBlank()) emptyList() else withContext(Dispatchers.IO) { store.search(query) }
            }
            val sharing = shared.isNotEmpty() || sharedText.isNotBlank()
            if (sharing) BackHandler { shared = emptyList(); sharedText = "" }
            ChatListScreen(
                chats, tab, onTab = { tab = it }, onOpen = { openThread = it.threadId }, time = ::time,
                sharing = if (sharing) io.github.secnewt.messages.chats.Sharing.label(shared, sharedText) else null,
                onCancelShare = { shared = emptyList(); sharedText = "" },
                onNewChat = { newChat = true },
                onBulk = { action, picked -> Thread { bulk(action, picked) }.start() },
                onOpenSettings = { showSettings = true },
                query = query,
                onQuery = { query = it },
                hits = hits,
            )
            return
        }
        val drafts = io.github.secnewt.messages.chats.Drafts(this)
        // Words from an "sms:" link win over an old draft; otherwise pick up where I left off.
        // A share's words go in too, after anything already typed.
        var draft by rememberSaveable(thread) {
            val typed = startDraft.ifEmpty { drafts.draft(thread).orEmpty() }
            mutableStateOf(listOf(typed, sharedText).filter { it.isNotBlank() }.joinToString(" "))
        }
        // Things to send with the words. Pictures come from Android's photo picker, files from
        // its file picker, photos from the camera app: none of them needs a permission here.
        var attachments by androidx.compose.runtime.remember(thread) { mutableStateOf(shared) }
        // What was shared now sits in this chat, ready to send; it doesn't follow me to the next one.
        LaunchedEffect(thread) {
            shared = emptyList()
            sharedText = ""
        }
        fun add(uri: Uri?, fallbackType: String, fallbackName: String) {
            uri ?: return
            attachments = attachments + describe(uri, fallbackType, fallbackName)
        }
        val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { add(it, "image/jpeg", "photo") }
        val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { add(it, "application/octet-stream", "file") }
        var cameraTarget by androidx.compose.runtime.remember { mutableStateOf<Uri?>(null) }
        val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            if (taken) cameraTarget?.let { attachments = attachments + io.github.secnewt.messages.chats.Attachment(it.toString(), "photo.jpg", "image/jpeg") }
        }
        val pickContactCard = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { contact ->
            contact?.let { contactCard(it) }?.let { attachments = attachments + it }
        }
        // A send that failed before anything was saved: its words and attachments come back.
        LaunchedEffect(unsent) {
            val back = unsent?.takeIf { it.thread == thread } ?: return@LaunchedEffect
            unsent = null
            draft = listOf(back.text, draft).filter { it.isNotBlank() }.joinToString(" ")
            drafts.keep(thread, draft)
            attachments = back.attachments + attachments
        }
        // The newest messages first; "Show older messages" reaches further back, a page at a time.
        var reach by rememberSaveable(thread) { mutableIntStateOf(MessageStore.PAGE) }
        val page by produceState(MessageStore.Page(emptyList(), false), thread, version, reach) {
            value = withContext(Dispatchers.IO) { store.page(thread, reach) }
        }
        val messages = page.messages
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
                onOpenContact = { person, edit ->
                    person.contact?.let { uri ->
                        val intent = Intent(if (edit) Intent.ACTION_EDIT else Intent.ACTION_VIEW, Uri.parse(uri))
                            .putExtra("finishActivityOnSaveCompleted", true)
                        runCatching { startActivity(intent) }.onFailure {
                            Toast.makeText(this, "No contacts app could open it.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
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
                isBlocked = { person -> io.github.secnewt.messages.chats.Blocking(this).isBlocked(person.address) },
                onBlock = { person, block ->
                    val blocking = io.github.secnewt.messages.chats.Blocking(this)
                    val ok = if (block) blocking.block(person.address) else blocking.unblock(person.address)
                    if (!ok) Toast.makeText(this, "Android didn't allow that change.", Toast.LENGTH_LONG).show()
                    version++
                },
                onPin = { on ->
                    io.github.secnewt.messages.settings.SettingsStore(this).setPinned(thread, on)
                    version++
                },
                onNotificationSettings = {
                    runCatching { startActivity(io.github.secnewt.messages.notify.Conversations.settings(this, chat)) }.onFailure {
                        Toast.makeText(this, "Android's notification settings didn't open.", Toast.LENGTH_LONG).show()
                    }
                },
                onMute = { on ->
                    io.github.secnewt.messages.settings.SettingsStore(this).setMuted(thread, on)
                    version++
                },
                onPickColor = { person, hue, everyChat ->
                    io.github.secnewt.messages.chats.PersonColorStore(this).pick(person, hue, if (everyChat) null else thread)
                    version++
                },
            )
            return
        }
        BackHandler { openThread = null }
        // One ChatScreen per chat: Undo, Find and the like never carry over to another chat.
        androidx.compose.runtime.key(thread) {
            ChatScreen(
                chat, messages, draft,
                onDraftChange = {
                    draft = it
                    drafts.keep(thread, it)
                },
                onSend = {
                    val text = draft.trim()
                    val attached = attachments
                    if (text.isNotEmpty() || attached.isNotEmpty()) {
                        draft = ""
                        drafts.clear(thread)
                        startDraft = ""
                        attachments = emptyList()
                        // One at a time, in order: a text sent while a video is being made smaller waits for it.
                        sending.execute { send(chat, text, attached) }
                    }
                },
                onBack = { openThread = null },
                time = ::time,
                onDownload = { message -> Thread { retry(message) }.start() },
                onShowOlder = if (page.hasOlder) ({ reach += MessageStore.PAGE }) else null,
                onRetry = { message ->
                    Thread { Outgoing.retry(this, message)?.let(::complain) }.start()
                },
                onAttach = { kind ->
                    when (kind) {
                        io.github.secnewt.messages.ui.AttachKind.MEDIA ->
                            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        io.github.secnewt.messages.ui.AttachKind.CAMERA -> {
                            val target = cameraFile()
                            cameraTarget = target
                            runCatching { takePhoto.launch(target) }.onFailure {
                                Toast.makeText(this, "No camera app could take the photo.", Toast.LENGTH_LONG).show()
                            }
                        }
                        io.github.secnewt.messages.ui.AttachKind.FILE -> pickFile.launch(arrayOf("*/*"))
                        io.github.secnewt.messages.ui.AttachKind.CONTACT -> pickContactCard.launch(null)
                    }
                },
                attached = attachments,
                onRemoveAttachment = { gone -> attachments = attachments - gone },
                onSave = { uri, name, type -> Thread { save(uri, name, type) }.start() },
                onDetails = { showDetails = true },
                onReact = { message, emoji ->
                    // A reaction is an ordinary text in iPhone wording; it folds onto the message here too.
                    io.github.secnewt.messages.reactions.Reactions.reply(message, emoji)?.let { text ->
                        Thread { Outgoing.send(this, chat.people.map { it.address }, text)?.let(::complain) }.start()
                    }
                },
                onDeleteMessage = { message -> Thread { MessageStore(this).deleteMessage(message.id) }.start() },
                onCopyCode = { code ->
                    // The same as the notification's Copy: marked private, cleared in a couple of minutes.
                    sendBroadcast(
                        Intent(this, io.github.secnewt.messages.notify.ActionReceiver::class.java)
                            .setAction(io.github.secnewt.messages.notify.ActionReceiver.COPY_CODE)
                            .putExtra(io.github.secnewt.messages.notify.ActionReceiver.EXTRA_CODE, code),
                    )
                    Toast.makeText(this, "Code copied. It's cleared from the clipboard in 2 minutes.", Toast.LENGTH_LONG).show()
                },
                onForward = { message ->
                    // Forwarding works like sharing from another app: back to the chat list to pick
                    // a chat (or start one), where the words and pictures wait in the message box.
                    shared = message.images.mapIndexed { i, uri ->
                        io.github.secnewt.messages.chats.Attachment(uri, if (message.images.size > 1) "picture ${i + 1}.jpg" else "picture.jpg", "image/jpeg")
                    } + message.files
                    sharedText = message.body
                    openThread = null
                },
                onCallNumber = { number ->
                    runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))) }.onFailure {
                        Toast.makeText(this, "No phone app could call it.", Toast.LENGTH_LONG).show()
                    }
                },
                onTextNumber = { number -> open(listOf(number)) },
                loadDetails = { message -> withContext(Dispatchers.IO) { store.details(message) } },
                onAddNumberToContacts = { number ->
                    runCatching {
                        startActivity(
                            Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
                                .putExtra(ContactsContract.Intents.Insert.PHONE, number),
                        )
                    }.onFailure { Toast.makeText(this, "No contacts app could add it.", Toast.LENGTH_LONG).show() }
                },
            )
        }
    }

    /**
     * Sends from the chat screen. If it can't even start (too big, a file that can't be read),
     * the words and attachments go back in that chat's message box.
     */
    private fun send(chat: Chat, text: String, attachments: List<io.github.secnewt.messages.chats.Attachment> = emptyList()) {
        // Making a video small enough can take a little while; say so rather than seem stuck.
        if (attachments.any { it.type.startsWith("video/") }) complain("Getting the video ready to send…")
        val problem = Outgoing.send(this, chat.people.map { it.address }, text, attachments) ?: return
        complain(problem)
        runOnUiThread {
            if (openThread == chat.threadId) {
                unsent = Unsent(chat.threadId, text, attachments)
            } else {
                // Left the chat already: keep the words as its draft (attachments can't be kept).
                val drafts = io.github.secnewt.messages.chats.Drafts(this)
                drafts.keep(chat.threadId, listOf(text, drafts.draft(chat.threadId).orEmpty()).filter { it.isNotBlank() }.joinToString(" "))
                version++
            }
        }
    }

    private fun complain(text: String) {
        runOnUiThread { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    }

    /** An attachment's name and type, as the app that has it reports them. */
    private fun describe(uri: Uri, fallbackType: String, fallbackName: String): io.github.secnewt.messages.chats.Attachment {
        val type = contentResolver.getType(uri) ?: fallbackType
        val name = runCatching {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: fallbackName
        return io.github.secnewt.messages.chats.Attachment(uri.toString(), name, type)
    }

    /** A new file in cache/camera/ for the camera app to write a photo into. */
    private fun cameraFile(): Uri {
        val file = java.io.File(java.io.File(cacheDir, "camera").apply { mkdirs() }, "photo-${System.currentTimeMillis()}.jpg")
        return androidx.core.content.FileProvider.getUriForFile(this, "$packageName.mmsfiles", file)
    }

    /** A contact as a standard vCard (.vcf), which any phone can open and save. */
    private fun contactCard(contact: Uri): io.github.secnewt.messages.chats.Attachment? = try {
        contentResolver.query(contact, arrayOf(ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use {
            if (!it.moveToFirst()) return@use null
            val card = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, it.getString(0))
            io.github.secnewt.messages.chats.Attachment(card.toString(), (it.getString(1) ?: "contact") + ".vcf", "text/x-vcard")
        }
    } catch (e: Exception) {
        null
    }

    /** Every contact's phone numbers, for New message. Empty without contacts access. */
    private fun contactNumbers(): List<io.github.secnewt.messages.chats.ContactNumber> = try {
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
            ),
            null, null, null,
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val number = c.getString(1) ?: continue
                    val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(resources, c.getInt(2), c.getString(3)).toString()
                    add(io.github.secnewt.messages.chats.ContactNumber(name, number, label))
                }
            }.distinctBy { it.name to it.number.filter(Char::isDigit) }
        }.orEmpty()
    } catch (e: SecurityException) {
        emptyList()
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

    /** Pin, mute, mark read, block or delete several chats at once (from the list). */
    private fun bulk(action: io.github.secnewt.messages.ui.BulkAction, picked: List<Chat>) {
        val settings = io.github.secnewt.messages.settings.SettingsStore(this)
        val store = MessageStore(this)
        when (action) {
            io.github.secnewt.messages.ui.BulkAction.PIN -> picked.forEach { settings.setPinned(it.threadId, true) }
            io.github.secnewt.messages.ui.BulkAction.UNPIN -> picked.forEach { settings.setPinned(it.threadId, false) }
            io.github.secnewt.messages.ui.BulkAction.MUTE -> picked.forEach { settings.setMuted(it.threadId, true) }
            io.github.secnewt.messages.ui.BulkAction.UNMUTE -> picked.forEach { settings.setMuted(it.threadId, false) }
            io.github.secnewt.messages.ui.BulkAction.MARK_READ -> picked.forEach {
                store.markRead(it.threadId)
                Notifier(this).cancel(it.threadId)
            }
            // Archiving puts a chat away read: what's in it has been dealt with.
            io.github.secnewt.messages.ui.BulkAction.ARCHIVE -> picked.forEach {
                store.markRead(it.threadId)
                Notifier(this).cancel(it.threadId)
                settings.setArchived(it.threadId, true)
            }
            io.github.secnewt.messages.ui.BulkAction.UNARCHIVE -> picked.forEach { settings.setArchived(it.threadId, false) }
            io.github.secnewt.messages.ui.BulkAction.BLOCK -> {
                val blocking = io.github.secnewt.messages.chats.Blocking(this)
                val failed = picked.flatMap { it.people }.distinctBy { it.address }.count { !blocking.block(it.address) }
                if (failed > 0) runOnUiThread { Toast.makeText(this, "Android didn't allow blocking $failed of them.", Toast.LENGTH_LONG).show() }
            }
            io.github.secnewt.messages.ui.BulkAction.DELETE -> picked.forEach { delete(it.threadId) }
        }
        runOnUiThread { version++ }
    }

    private fun delete(threadId: Long) {
        val removed = MessageStore(this).deleteChat(threadId)
        io.github.secnewt.messages.chats.Drafts(this).clear(threadId)
        Notifier(this).cancel(threadId)
        io.github.secnewt.messages.notify.Conversations.remove(this, threadId)
        android.util.Log.i("NewtSms", "Deleted a chat ($removed messages)")
    }

    private fun retry(message: Message) {
        // On the SIM it came in on (the carrier only hands it to that line).
        val waiting = MessageStore(this).waitingNotice(message.id) ?: return
        Mms.download(this, waiting.notice, waiting.location, waiting.subscription)
    }

    private fun time(millis: Long): String =
        if (DateUtils.isToday(millis)) DateFormat.getTimeFormat(this).format(Date(millis))
        else DateUtils.formatDateTime(this, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)

    companion object {
        const val EXTRA_THREAD = "thread"
    }
}
