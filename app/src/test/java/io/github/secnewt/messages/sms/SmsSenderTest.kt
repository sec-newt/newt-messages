package io.github.secnewt.messages.sms

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The number a text goes out to (and is saved with), whether it's new or tried again. */
@RunWith(AndroidJUnit4::class)
class SmsSenderTest {

    @Test
    fun `a number saved with spaces, dashes and brackets goes out plain`() {
        assertEquals("+19725550111", SmsSender.plain("+1 972-555-0111"))
        assertEquals("4695550175", SmsSender.plain("(469) 555-0175"))
        assertEquals("+19725550111", SmsSender.plain("+1.972.555.0111"))
    }

    @Test
    fun `plain numbers, short codes and emails go out as they are`() {
        assertEquals("+19725550111", SmsSender.plain("+19725550111"))
        assertEquals("72975", SmsSender.plain("72975"))
        assertEquals("someone@example.com", SmsSender.plain("someone@example.com"))
    }
}
