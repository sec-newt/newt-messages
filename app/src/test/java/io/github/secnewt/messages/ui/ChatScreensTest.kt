package io.github.secnewt.messages.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.secnewt.messages.chats.Chat
import io.github.secnewt.messages.chats.ChatTab
import io.github.secnewt.test.newtScreenshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots (light, dark, 200% text) and accessibility checks for the chat list and a chat. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ChatScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun list(tab: ChatTab) = @androidx.compose.runtime.Composable {
        ChatListScreen(SampleChats.chats, tab, onTab = {}, onOpen = {}, time = SampleChats.time)
    }

    @Test fun peopleDark() = composeRule.newtScreenshot("chats-people-dark", dark = true, content = list(ChatTab.PEOPLE))
    @Test fun peopleLight() = composeRule.newtScreenshot("chats-people-light", dark = false, content = list(ChatTab.PEOPLE))
    @Test fun peopleLargeText() = composeRule.newtScreenshot("chats-people-200pct", dark = true, fontScale = 2f, content = list(ChatTab.PEOPLE))
    @Test
    fun peopleWithNewMessageButton() = composeRule.newtScreenshot("chats-people-new-dark", dark = true) {
        ChatListScreen(SampleChats.chats, ChatTab.PEOPLE, onTab = {}, onOpen = {}, time = SampleChats.time, onNewChat = {})
    }

    @Test fun groupsDark() = composeRule.newtScreenshot("chats-groups-dark", dark = true, content = list(ChatTab.GROUPS))
    @Test fun codesLight() = composeRule.newtScreenshot("chats-codes-light", dark = false, content = list(ChatTab.CODES))

    @Test
    fun emptyCodesTab() = composeRule.newtScreenshot("chats-codes-empty-dark", dark = true) {
        ChatListScreen(SampleChats.chats.take(2), ChatTab.CODES, onTab = {}, onOpen = {}, time = SampleChats.time)
    }

    private val family = @androidx.compose.runtime.Composable {
        ChatScreen(SampleChats.family, SampleChats.familyMessages, draft = "", onDraftChange = {}, onSend = {}, onBack = {}, time = SampleChats.time)
    }

    @Test fun groupChatDark() = composeRule.newtScreenshot("chat-group-dark", dark = true, content = family)
    @Test fun groupChatLight() = composeRule.newtScreenshot("chat-group-light", dark = false, content = family)
    @Test fun groupChatLargeText() = composeRule.newtScreenshot("chat-group-200pct", dark = false, fontScale = 2f, content = family)

    @Test
    fun tappingAChatOpensIt() {
        var opened: Chat? = null
        composeRule.setContent {
            io.github.secnewt.ui.NewtTheme(darkTheme = true) {
                ChatListScreen(SampleChats.chats, ChatTab.GROUPS, onTab = {}, onOpen = { opened = it }, time = SampleChats.time)
            }
        }
        composeRule.onNodeWithContentDescription("Family, 1 unread", substring = true).assertIsDisplayed().performClick()
        assertEquals(4L, opened?.threadId)
    }

    @Test
    fun aMessageThatDidntDownloadCanBeRetried() {
        var retried: Long? = null
        composeRule.setContent {
            io.github.secnewt.ui.NewtTheme(darkTheme = true) {
                ChatScreen(
                    SampleChats.family, SampleChats.familyMessages, draft = "", onDraftChange = {}, onSend = {}, onBack = {},
                    time = SampleChats.time, onDownload = { retried = it.id },
                )
            }
        }
        composeRule.onNodeWithText("Download").assertIsDisplayed().performClick()
        assertEquals(-6L, retried)
    }

    @Test
    fun attachingAPicture() = composeRule.newtScreenshot("chat-attached-dark", dark = true) {
        ChatScreen(
            SampleChats.family, SampleChats.familyMessages, draft = "Look at this", onDraftChange = {}, onSend = {}, onBack = {},
            time = SampleChats.time, onAttach = {}, attached = "photo.jpg, bulletin.pdf",
        )
    }

    @Test
    fun deletingAChatAsksFirst() {
        var deleted = false
        composeRule.setContent { io.github.secnewt.ui.NewtTheme(darkTheme = true) { Details(onDelete = { deleted = true }) } }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Delete this chat"))
        composeRule.onNodeWithText("Delete this chat").performClick()
        composeRule.onNodeWithText("Delete this chat?").assertIsDisplayed()
        assertEquals(false, deleted)
        composeRule.onNodeWithText("Delete").performClick()
        assertEquals(true, deleted)
    }

    @androidx.compose.runtime.Composable
    private fun Details(
        chat: Chat = SampleChats.family.copy(people = SampleChats.family.people + io.github.secnewt.messages.chats.Person("+14695550199")),
        onDelete: () -> Unit = {},
        onNewMembers: (List<String>) -> Unit = {},
        onAddToContacts: (io.github.secnewt.messages.chats.Person) -> Unit = {},
        onPickColor: (io.github.secnewt.messages.chats.Person, io.github.secnewt.messages.chats.PersonHue?, Boolean) -> Unit = { _, _, _ -> },
    ) = ChatDetailsScreen(
        chat, onBack = {}, onText = {}, onCall = {}, onAddToContacts = onAddToContacts, onRename = {},
        onNewMembers = onNewMembers, onPickContact = {}, onDelete = onDelete, onPickColor = onPickColor,
    )

    // Pastor Dave's and Sam's names pick the same teal; the chat gives them different colors.
    private val church = @androidx.compose.runtime.Composable {
        val chat = SampleChats.chats[4]
        val messages = listOf(
            io.github.secnewt.messages.chats.Message(1, "Small group moved to Thursday", 7 * SampleChats.HOUR, SampleChats.pastor),
            io.github.secnewt.messages.chats.Message(2, "Works for me", 8 * SampleChats.HOUR, SampleChats.sam),
            io.github.secnewt.messages.chats.Message(3, "I'll bring snacks", 9 * SampleChats.HOUR, SampleChats.becca),
        )
        ChatScreen(chat, messages, "", {}, {}, {}, SampleChats.time)
    }
    @Test fun clashingColorsAreToldApartDark() = composeRule.newtScreenshot("chat-church-dark", dark = true, content = church)
    @Test fun clashingColorsAreToldApartLight() = composeRule.newtScreenshot("chat-church-light", dark = false, content = church)

    private val picker = @androidx.compose.runtime.Composable {
        androidx.compose.material3.Surface {
            ColorPicker(
                SampleChats.chats[4], SampleChats.pastor, onPick = { _, _ -> },
                androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(24.dp),
            )
        }
    }
    @Test fun colorPickerDark() = composeRule.newtScreenshot("color-picker-dark", dark = true, content = picker)
    @Test fun colorPickerLight() = composeRule.newtScreenshot("color-picker-light", dark = false, content = picker)
    @Test fun colorPickerLargeText() = composeRule.newtScreenshot("color-picker-200pct", dark = false, fontScale = 2f, content = picker)

    @Test
    fun pickingAColorForJustThisGroup() {
        var picked: Triple<String, io.github.secnewt.messages.chats.PersonHue?, Boolean>? = null
        composeRule.setContent {
            io.github.secnewt.ui.NewtTheme(darkTheme = true) {
                Details(chat = SampleChats.chats[4], onPickColor = { person, hue, every -> picked = Triple(person.shownName, hue, every) })
            }
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Change Pastor Dave's color", substring = true))
        composeRule.onNodeWithContentDescription("Change Pastor Dave's color", substring = true).performClick()
        // Sam keeps the teal both names pick, and the picker says so.
        composeRule.onNodeWithText("Sam has this here").assertExists()
        composeRule.onNodeWithText("Pink").performScrollTo().performClick()
        assertEquals(Triple("Pastor Dave", io.github.secnewt.messages.chats.PersonHue.PINK, false), picked)
    }

    @Test fun groupDetailsDark() = composeRule.newtScreenshot("group-details-dark", dark = true) { Details() }
    @Test fun groupDetailsLargeText() = composeRule.newtScreenshot("group-details-200pct", dark = false, fontScale = 2f) { Details() }

    @Test
    fun removingSomeoneStartsANewGroup() {
        var members: List<String>? = null
        composeRule.setContent { io.github.secnewt.ui.NewtTheme(darkTheme = true) { Details(onNewMembers = { members = it }) } }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Remove Sam from the group"))
        composeRule.onNodeWithText("Remove Sam from the group").performClick()
        composeRule.onNodeWithText("Start new group").performClick()
        assertEquals(listOf("+19725550111", "+19725550144", "+14695550199"), members)
    }

    @Test
    fun unknownNumbersCanBeAddedToContacts() {
        var added: String? = null
        composeRule.setContent { io.github.secnewt.ui.NewtTheme(darkTheme = true) { Details(onAddToContacts = { added = it.address }) } }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Add to contacts"))
        composeRule.onNodeWithText("Add to contacts").performClick()
        assertEquals("+14695550199", added)
    }

    @Test
    fun linksShowUnderlined() = composeRule.newtScreenshot("chat-links-dark", dark = true) {
        ChatScreen(
            SampleChats.chats[1],
            listOf(io.github.secnewt.messages.chats.Message(1, "Service times are on firstbaptist.church/visit or email office@church.org", 9 * SampleChats.HOUR, SampleChats.mom)),
            draft = "", onDraftChange = {}, onSend = {}, onBack = {}, time = SampleChats.time, onDetails = {},
        )
    }

    @Test
    fun savingAFileAndRenamingAGroup() {
        var saved: String? = null
        composeRule.setContent {
            io.github.secnewt.ui.NewtTheme(darkTheme = true) {
                ChatScreen(
                    SampleChats.family, SampleChats.familyMessages, draft = "", onDraftChange = {}, onSend = {}, onBack = {},
                    time = SampleChats.time, onSave = { _, name, _ -> saved = name }, onDetails = {},
                )
            }
        }
        composeRule.onNodeWithText("Save").performClick()
        assertEquals("bulletin.pdf", saved)
        composeRule.onNodeWithContentDescription("Group details").assertIsDisplayed()
    }

    // The rename dialog is tested on the emulator (scripts/emulator-sms-test.sh): under
    // Robolectric a text field inside a dialog never lets Compose go idle.

    @Test
    fun groupWithAFileDark() = composeRule.newtScreenshot("chat-group-file-dark", dark = true) {
        ChatScreen(
            SampleChats.family, SampleChats.familyMessages, draft = "", onDraftChange = {}, onSend = {}, onBack = {},
            time = SampleChats.time, onSave = { _, _, _ -> }, onDetails = {},
        )
    }

    private val contacts = listOf(
        io.github.secnewt.messages.chats.ContactNumber("Becca", "+19725550111", "Mobile"),
        io.github.secnewt.messages.chats.ContactNumber("Mom", "+19725550144", "Home"),
        io.github.secnewt.messages.chats.ContactNumber("Pastor Dave", "+14695550133", "Work"),
    )

    @Test fun newChatDark() = composeRule.newtScreenshot("new-chat-dark", dark = true) { NewChatScreen(contacts, onBack = {}, onStart = {}) }
    @Test fun newChatLargeText() = composeRule.newtScreenshot("new-chat-200pct", dark = false, fontScale = 2f) { NewChatScreen(contacts, onBack = {}, onStart = {}) }

    @Test
    fun pickingTwoPeopleStartsAGroup() {
        var started: List<String>? = null
        composeRule.setContent { io.github.secnewt.ui.NewtTheme(darkTheme = true) { NewChatScreen(contacts, onBack = {}, onStart = { started = it }) } }
        composeRule.onNodeWithText("Becca").performClick()
        composeRule.onNodeWithText("Text Becca").assertIsDisplayed()
        composeRule.onNodeWithText("Mom").performClick()
        composeRule.onNodeWithText("Start a group of 2").performClick()
        assertEquals(listOf("+19725550111", "+19725550144"), started)
    }

    @Test
    fun typingANumberAddsIt() {
        var started: List<String>? = null
        composeRule.setContent { io.github.secnewt.ui.NewtTheme(darkTheme = true) { NewChatScreen(contacts, onBack = {}, onStart = { started = it }) } }
        composeRule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("214 555 0199")
        composeRule.onNodeWithText("Add 214 555 0199").performClick()
        composeRule.onNodeWithText("Text 214 555 0199").performClick()
        assertEquals(listOf("214 555 0199"), started)
    }

    @Test
    fun longPressToReact() {
        var reacted: Pair<Long, String>? = null
        composeRule.setContent {
            io.github.secnewt.ui.NewtTheme(darkTheme = true) {
                ChatScreen(
                    SampleChats.family, SampleChats.familyMessages, draft = "", onDraftChange = {}, onSend = {}, onBack = {},
                    time = SampleChats.time, onReact = { m, e -> reacted = m.id to e }, onDeleteMessage = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Becca: Thank you both!", substring = true)
            .performTouchInput { longClick(androidx.compose.ui.geometry.Offset(80f, center.y)) }
        composeRule.onNodeWithContentDescription("React 🙏").performClick()
        assertEquals(4L to "🙏", reacted)
    }

    @Test
    fun deletingOneMessageAsksFirst() {
        var deleted: Long? = null
        composeRule.setContent {
            io.github.secnewt.ui.NewtTheme(darkTheme = true) {
                ChatScreen(
                    SampleChats.family, SampleChats.familyMessages, draft = "", onDraftChange = {}, onSend = {}, onBack = {},
                    time = SampleChats.time, onReact = { _, _ -> }, onDeleteMessage = { deleted = it.id },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Becca: Thank you both!", substring = true)
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
            .first { it.label == "Message options" }.action()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete this message").performClick()
        assertEquals(null, deleted)
        composeRule.onNodeWithText("Delete").performClick()
        assertEquals(4L, deleted)
    }

    @Test fun settingsDark() = composeRule.newtScreenshot("settings-dark", dark = true) {
        SettingsScreen(io.github.secnewt.messages.settings.MessagesSettings(), onChange = {}, onBack = {})
    }
    @Test fun settingsLargeText() = composeRule.newtScreenshot("settings-200pct", dark = false, fontScale = 2f) {
        SettingsScreen(io.github.secnewt.messages.settings.MessagesSettings(), onChange = {}, onBack = {})
    }

    @Test
    fun choosingNotToNotifyForReactions() {
        var changed: io.github.secnewt.messages.settings.MessagesSettings? = null
        composeRule.setContent {
            io.github.secnewt.ui.NewtTheme(darkTheme = true) {
                SettingsScreen(io.github.secnewt.messages.settings.MessagesSettings(), onChange = { changed = it }, onBack = {})
            }
        }
        composeRule.onNodeWithText("Don't notify").performClick()
        assertEquals(io.github.secnewt.messages.settings.ReactionNotify.OFF, changed?.reactions)
    }

    @Test
    fun pinningAndMutingAChat() {
        var pinned: Boolean? = null
        var muted: Boolean? = null
        composeRule.setContent {
            io.github.secnewt.ui.NewtTheme(darkTheme = true) {
                ChatDetailsScreen(
                    SampleChats.family, onBack = {}, onText = {}, onCall = {}, onAddToContacts = {}, onRename = {},
                    onNewMembers = {}, onPickContact = {}, onDelete = {}, onPin = { pinned = it }, onMute = { muted = it },
                )
            }
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Pin to top"))
        composeRule.onNodeWithText("Pin to top").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Mute this chat"))
        composeRule.onNodeWithText("Mute this chat").performClick()
        assertEquals(true, pinned)
        assertEquals(true, muted)
    }

    @Test
    fun searchResults() = composeRule.newtScreenshot("search-dark", dark = true) {
        ChatListScreen(
            SampleChats.chats, ChatTab.PEOPLE, onTab = {}, onOpen = {}, time = SampleChats.time,
            query = "becca", onQuery = {},
            hits = listOf(io.github.secnewt.messages.chats.SearchHit(4, "Becca said the brisket is ready at six", 11 * SampleChats.HOUR)),
        )
    }

    @Test
    fun blockingAMemberAsksFirst() {
        var blocked: Pair<String, Boolean>? = null
        composeRule.setContent {
            io.github.secnewt.ui.NewtTheme(darkTheme = true) {
                ChatDetailsScreen(
                    SampleChats.family, onBack = {}, onText = {}, onCall = {}, onAddToContacts = {}, onRename = {},
                    onNewMembers = {}, onPickContact = {}, onDelete = {}, onBlock = { p, b -> blocked = p.address to b },
                )
            }
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Block Sam"))
        composeRule.onNodeWithText("Block Sam").performClick()
        composeRule.onNodeWithText("Block Sam?").assertIsDisplayed()
        composeRule.onNodeWithText("Block").performClick()
        assertEquals("+12145550122" to true, blocked)
    }

    @Test
    fun longPressPicksChatsForBulkActions() {
        var did: Pair<BulkAction, List<Long>>? = null
        composeRule.setContent {
            io.github.secnewt.ui.NewtTheme(darkTheme = true) {
                ChatListScreen(
                    SampleChats.chats, ChatTab.PEOPLE, onTab = {}, onOpen = {}, time = SampleChats.time,
                    onBulk = { action, picked -> did = action to picked.map { it.threadId } },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Becca, pinned, 2 unread", substring = true).performTouchInput { longClick() }
        composeRule.onNodeWithText("1 chat picked").assertIsDisplayed()
        // While picking, a tap adds another chat instead of opening it.
        composeRule.onNodeWithContentDescription("Mom.", substring = true).performClick()
        composeRule.onNodeWithText("2 chats picked").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Mute").performClick()
        assertEquals(BulkAction.MUTE to listOf(1L, 2L), did)
    }

    @Test
    fun deletingPickedChatsAsksFirst() {
        var did: BulkAction? = null
        composeRule.setContent {
            io.github.secnewt.ui.NewtTheme(darkTheme = true) {
                ChatListScreen(SampleChats.chats, ChatTab.PEOPLE, onTab = {}, onOpen = {}, time = SampleChats.time, onBulk = { a, _ -> did = a })
            }
        }
        composeRule.onNodeWithContentDescription("Mom.", substring = true).performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("Delete").performClick()
        assertEquals(null, did)
        composeRule.onNodeWithText("Delete this chat?").assertIsDisplayed()
        composeRule.onAllNodesWithText("Delete").onLast().performClick()
        assertEquals(BulkAction.DELETE, did)
    }

    // The bar shown while chats are picked: icons, with Pin lit because every picked chat is pinned.
    @Test fun selectionBarDark() = composeRule.newtScreenshot("selection-bar-dark", dark = true) {
        SelectionBar(listOf(SampleChats.chats[0]), onAction = {}, onCancel = {})
    }
    @Test fun selectionBarLargeText() = composeRule.newtScreenshot("selection-bar-200pct", dark = false, fontScale = 2f) {
        SelectionBar(listOf(SampleChats.chats[0], SampleChats.chats[1]), onAction = {}, onCancel = {})
    }

    @Test
    fun pickingLargeText() = composeRule.newtScreenshot("chats-picking-200pct", dark = false, fontScale = 2f) {
        ChatListScreen(SampleChats.chats, ChatTab.PEOPLE, onTab = {}, onOpen = {}, time = SampleChats.time, onBulk = { _, _ -> })
    }

    @Test
    fun tappingAReactionShowsWhoReacted() {
        composeRule.setContent {
            io.github.secnewt.ui.NewtTheme(darkTheme = true) {
                ChatScreen(SampleChats.family, SampleChats.familyMessages, draft = "", onDraftChange = {}, onSend = {}, onBack = {}, time = SampleChats.time)
            }
        }
        // Tapping the chip and the screen reader's action open the same list.
        composeRule.onNodeWithContentDescription("You: I can do the brisket", substring = true)
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
            .first { it.label == "See who reacted" }.action()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Reactions").assertIsDisplayed()
        composeRule.onNodeWithText("Mom, Becca").assertIsDisplayed()
    }

    @Test
    fun savedContactsCanBeViewedAndEdited() {
        var opened: Pair<String, Boolean>? = null
        val withContact = SampleChats.family.copy(
            people = SampleChats.family.people.map { if (it.name == "Becca") it.copy(contact = "content://contacts/1") else it },
        )
        composeRule.setContent {
            io.github.secnewt.ui.NewtTheme(darkTheme = true) {
                ChatDetailsScreen(
                    withContact, onBack = {}, onText = {}, onCall = {}, onAddToContacts = {}, onRename = {},
                    onNewMembers = {}, onPickContact = {}, onDelete = {}, onOpenContact = { p, e -> opened = p.name.orEmpty() to e },
                )
            }
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Edit contact"))
        composeRule.onNodeWithContentDescription("Edit Becca's contact").performClick()
        assertEquals("Becca" to true, opened)
    }

    @Test
    fun theAttachMenuOffersEveryKind() {
        var chosen: AttachKind? = null
        composeRule.setContent {
            io.github.secnewt.ui.NewtTheme(darkTheme = true) {
                ChatScreen(
                    SampleChats.family, SampleChats.familyMessages, draft = "", onDraftChange = {}, onSend = {}, onBack = {},
                    time = SampleChats.time, onAttach = { chosen = it },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Attach").performClick()
        listOf("Photo or video", "Take a photo", "File").forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        composeRule.onNodeWithText("Contact card").performClick()
        assertEquals(AttachKind.CONTACT, chosen)
    }

    @Test
    fun aNewMessageScrollsIntoView() {
        var messages by androidx.compose.runtime.mutableStateOf(
            (1L..40L).map { io.github.secnewt.messages.chats.Message(it, "Message $it", it * 60_000, SampleChats.becca) },
        )
        composeRule.setContent {
            io.github.secnewt.ui.NewtTheme(darkTheme = true) {
                ChatScreen(SampleChats.chats[0], messages, draft = "", onDraftChange = {}, onSend = {}, onBack = {}, time = SampleChats.time)
            }
        }
        composeRule.onNodeWithContentDescription("Becca: Message 40", substring = true).assertIsDisplayed()
        messages = messages + io.github.secnewt.messages.chats.Message(41, "Just arrived", 41 * 60_000, SampleChats.becca)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Becca: Just arrived", substring = true).assertIsDisplayed()
    }

    @Test
    fun reactionsAreReadOutWithTheMessage() {
        composeRule.setContent { io.github.secnewt.ui.NewtTheme(darkTheme = true) { family() } }
        composeRule.onNodeWithContentDescription("Reactions: ❤️ from Mom, ❤️ from Becca, 😂 from Sam", substring = true).assertIsDisplayed()
    }
}
