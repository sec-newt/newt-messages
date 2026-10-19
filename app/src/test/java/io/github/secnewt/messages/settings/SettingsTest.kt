package io.github.secnewt.messages.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {

    @Test
    fun `speaking follows the mode, the ringer, headphones and Do Not Disturb`() {
        assertFalse(Speaking.shouldSpeak(SpeakMode.OFF, true, ringerOn = true, headphones = true, doNotDisturb = false))
        assertTrue(Speaking.shouldSpeak(SpeakMode.ALWAYS, true, ringerOn = true, headphones = false, doNotDisturb = false))
        assertFalse(Speaking.shouldSpeak(SpeakMode.ALWAYS, true, ringerOn = false, headphones = false, doNotDisturb = false))
        assertTrue(Speaking.shouldSpeak(SpeakMode.ALWAYS, true, ringerOn = false, headphones = true, doNotDisturb = false))
        assertFalse(Speaking.shouldSpeak(SpeakMode.HEADPHONES_ONLY, true, ringerOn = true, headphones = false, doNotDisturb = false))
        assertTrue(Speaking.shouldSpeak(SpeakMode.HEADPHONES_ONLY, true, ringerOn = false, headphones = true, doNotDisturb = false))
        assertFalse(Speaking.shouldSpeak(SpeakMode.ALWAYS, true, ringerOn = true, headphones = true, doNotDisturb = true))
        assertTrue(Speaking.shouldSpeak(SpeakMode.ALWAYS, false, ringerOn = true, headphones = false, doNotDisturb = true))
    }

    @Test
    fun `what gets said`() {
        assertEquals("Message from Becca", Speaking.text("Becca", "+19725550111", null))
        assertEquals("Message from Sam, in Family", Speaking.text("Sam", "+12145550122", "Family"))
        assertEquals("Message from 9 7 2, 5 5 5, 0 1 1 1", Speaking.text(null, "9725550111", null))
    }
}
