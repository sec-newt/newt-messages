package io.github.secnewt.messages.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.secnewt.test.newtScreenshot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots (light, dark, and 200% text) and accessibility checks, using the Newt
 * library's newtScreenshot(). Copy this pattern for every new screen; the screenshots
 * land in CI's "screenshots" artifact for review.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class WelcomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun welcomeDark() {
        composeRule.newtScreenshot("welcome-dark", dark = true) { WelcomeScreen() }
        composeRule.onNodeWithText("Make it my messaging app").assertIsDisplayed()
    }

    @Test
    fun welcomeLight() = composeRule.newtScreenshot("welcome-light", dark = false) { WelcomeScreen() }

    @Test
    fun welcomeLargeText() = composeRule.newtScreenshot("welcome-200pct", dark = true, fontScale = 2f) { WelcomeScreen() }

    @Test
    fun welcomeIsNumberedStepsWithTheButtonOnTheFirst() {
        var made = 0
        composeRule.setContent { io.github.secnewt.ui.NewtTheme(darkTheme = true) { WelcomeScreen(onMakeDefault = { made++ }) } }
        composeRule.onNodeWithContentDescription("Step 1 of 3, Make Newt Messages your messaging app, current").assertExists()
        composeRule.onNodeWithContentDescription("Step 2 of 3, Allow notifications, not yet").assertExists()
        composeRule.onNodeWithText("Make it my messaging app").performClick()
        org.junit.Assert.assertEquals(1, made)
    }
}
