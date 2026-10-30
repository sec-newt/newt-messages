package io.github.secnewt.messages.chats

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DraftsTest {

    private val drafts = Drafts(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `each chat keeps its own draft until it's cleared`() {
        drafts.keep(4, "See you at ")
        drafts.keep(5, "Thanks!")
        assertEquals("See you at ", drafts.draft(4))
        assertEquals("Thanks!", drafts.draft(5))
        drafts.clear(4)
        assertNull(drafts.draft(4))
        assertEquals("Thanks!", drafts.draft(5))
    }

    @Test
    fun `only spaces is no draft`() {
        drafts.keep(6, "Hello")
        drafts.keep(6, "   ")
        assertNull(drafts.draft(6))
    }
}
