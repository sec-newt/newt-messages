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
        assertEquals("Message from Becca: Can you grab milk?", Speaking.text("Becca", "+19725550111", null, "Can you grab milk?"))
        assertEquals("Message from 2 6 2 9 6 6: a sign-in code", Speaking.text(null, "262966", null, "482913 is your verification code"))
        assertEquals("Message from Becca", Speaking.text("Becca", "+19725550111", null, null))
    }

    @Test
    fun `an archived chat comes back when someone writes`() {
        assertEquals(false, Archive.isArchived(null, 100))
        assertEquals(true, Archive.isArchived(archivedAt = 200, lastTimeMillis = 100))
        assertEquals(true, Archive.isArchived(archivedAt = 200, lastTimeMillis = 200))
        assertEquals(false, Archive.isArchived(archivedAt = 200, lastTimeMillis = 300))
    }

    @Test
    fun `archived chats aren't in the tabs`() {
        val mom = io.github.secnewt.messages.chats.Chat(2, listOf(io.github.secnewt.messages.chats.Person("+19725550144", "Mom")), "", 1, archived = true)
        assertEquals(emptyList<Any>(), io.github.secnewt.messages.chats.ChatSorting.inTab(listOf(mom), io.github.secnewt.messages.chats.ChatTab.PEOPLE))
    }
}
