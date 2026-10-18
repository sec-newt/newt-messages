package io.github.secnewt.messages.chats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodesTest {

    @Test
    fun `common sign-in texts`() {
        assertEquals("482913", Codes.find("Your verification code is 482913. Don't share it."))
        assertEquals("482913", Codes.find("482913 is your Google verification code"))
        assertEquals("5521", Codes.find("Chase: Your one-time code is 5521"))
        assertEquals("123456", Codes.find("Use code 123-456 to sign in"))
        assertEquals("88231044", Codes.find("Your login PIN: 88231044"))
    }

    @Test
    fun `ordinary texts have no code`() {
        assertNull(Codes.find("See you at 7"))
        assertNull(Codes.find("Call me at 972 555 0111"))
        assertNull(Codes.find("Read the code of conduct 2026"))
        assertNull(Codes.find("Your order 12345678 shipped"))
    }
}
