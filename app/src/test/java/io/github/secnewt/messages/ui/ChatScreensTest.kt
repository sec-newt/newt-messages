package io.github.secnewt.messages.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
            time = SampleChats.time, onAttach = {}, attached = "1 picture",
        )
    }

    @Test
    fun deletingAChatAsksFirst() {
        var deleted = false
        composeRule.setContent {
            io.github.secnewt.ui.NewtTheme(darkTheme = true) {
                ChatScreen(
                    SampleChats.family, SampleChats.familyMessages, draft = "", onDraftChange = {}, onSend = {}, onBack = {},
                    time = SampleChats.time, onDelete = { deleted = true },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Delete this chat").performClick()
        composeRule.onNodeWithText("Delete this chat?").assertIsDisplayed()
        assertEquals(false, deleted)
        composeRule.onNodeWithText("Delete").performClick()
        assertEquals(true, deleted)
    }

    @Test
    fun linksShowUnderlined() = composeRule.newtScreenshot("chat-links-dark", dark = true) {
        ChatScreen(
            SampleChats.chats[1],
            listOf(io.github.secnewt.messages.chats.Message(1, "Service times are on firstbaptist.church/visit or email office@church.org", 9 * SampleChats.HOUR, SampleChats.mom)),
            draft = "", onDraftChange = {}, onSend = {}, onBack = {}, time = SampleChats.time, onDelete = {},
        )
    }

    @Test
    fun savingAFileAndRenamingAGroup() {
        var saved: String? = null
        var renamed: String? = null
        composeRule.setContent {
            io.github.secnewt.ui.NewtTheme(darkTheme = true) {
                ChatScreen(
                    SampleChats.family, SampleChats.familyMessages, draft = "", onDraftChange = {}, onSend = {}, onBack = {},
                    time = SampleChats.time, onSave = { _, name, _ -> saved = name }, onRename = { renamed = it },
                )
            }
        }
        composeRule.onNodeWithText("Save").performClick()
        assertEquals("bulletin.pdf", saved)
        // Opens the rename dialog with the current name, and saving passes it back. The name
        // field's blinking cursor never stops, so the test clock is stepped by hand from here.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithContentDescription("Rename this group").performClick()
        composeRule.mainClock.advanceTimeBy(2000)
        composeRule.onNodeWithText("Name this group").assertExists()
        composeRule.onNodeWithText("Save name").performClick()
        composeRule.mainClock.advanceTimeBy(500)
        assertEquals("Family", renamed)
    }

    @Test
    fun groupWithAFileDark() = composeRule.newtScreenshot("chat-group-file-dark", dark = true) {
        ChatScreen(
            SampleChats.family, SampleChats.familyMessages, draft = "", onDraftChange = {}, onSend = {}, onBack = {},
            time = SampleChats.time, onSave = { _, _, _ -> }, onRename = {}, onDelete = {},
        )
    }

    @Test
    fun reactionsAreReadOutWithTheMessage() {
        composeRule.setContent { io.github.secnewt.ui.NewtTheme(darkTheme = true) { family() } }
        composeRule.onNodeWithContentDescription("Reactions: ❤️ from Mom, ❤️ from Becca, 😂 from Sam", substring = true).assertIsDisplayed()
    }
}
