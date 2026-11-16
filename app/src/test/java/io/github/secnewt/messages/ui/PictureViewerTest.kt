package io.github.secnewt.messages.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.secnewt.ui.NewtTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The full-screen picture's row of buttons: an icon with its word under each. */
@RunWith(AndroidJUnit4::class)
class PictureViewerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `every button has its word, is big enough and does its job`() {
        val pressed = mutableListOf<String>()
        composeRule.setContent {
            NewtTheme(darkTheme = true) {
                PictureViewer(
                    "content://mms/part/1",
                    onClose = { pressed += "Close" },
                    onSave = { pressed += "Save" },
                    onShare = { pressed += "Share" },
                    onForward = { pressed += "Forward" },
                )
            }
        }
        for (word in listOf("Forward", "Share", "Save", "Close")) {
            // One clickable button per word, so a screen reader hears each once.
            composeRule.onAllNodes(hasText(word) and hasClickAction(), useUnmergedTree = false).assertCountEquals(1)
            composeRule.onNodeWithText(word).assertHeightIsAtLeast(56.dp).performClick()
        }
        assertEquals(listOf("Forward", "Share", "Save", "Close"), pressed)
    }

    @Test
    fun `without Share or Save, only what can be done is shown`() {
        composeRule.setContent {
            NewtTheme(darkTheme = true) { PictureViewer("content://mms/part/1", onClose = {}) }
        }
        composeRule.onAllNodes(hasText("Share")).assertCountEquals(0)
        composeRule.onAllNodes(hasText("Save")).assertCountEquals(0)
        composeRule.onNodeWithText("Close").assertHeightIsAtLeast(56.dp)
    }
}
