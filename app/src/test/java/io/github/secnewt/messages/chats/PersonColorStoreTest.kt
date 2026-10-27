package io.github.secnewt.messages.chats

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PersonColorStoreTest {

    private val store = PersonColorStore(ApplicationProvider.getApplicationContext<Context>())
    private val becca = Person("+19725550111", "Becca")
    private val key = ChatColors.key(becca.address)

    @Test
    fun `a pick for one chat stays in that chat`() {
        store.pick(becca, PersonHue.PINK, threadId = 4)
        assertEquals(PersonHue.PINK, store.colors(4, listOf(becca))[key])
        assertTrue(store.picked(5, listOf(becca)).isEmpty())
    }

    @Test
    fun `a pick for every chat shows everywhere, and one chat's pick wins over it`() {
        store.pick(becca, PersonHue.LIME, threadId = null)
        assertEquals(PersonHue.LIME, store.picked(9, listOf(becca))[key])
        store.pick(becca, PersonHue.PINK, threadId = 4)
        assertEquals(PersonHue.PINK, store.picked(4, listOf(becca))[key])
        assertEquals(PersonHue.LIME, store.picked(9, listOf(becca))[key])
    }

    @Test
    fun `picking for every chat replaces one-chat picks, and automatic clears them`() {
        store.pick(becca, PersonHue.PINK, threadId = 4)
        store.pick(becca, PersonHue.LIME, threadId = null)
        assertEquals(PersonHue.LIME, store.picked(4, listOf(becca))[key])
        store.pick(becca, null, threadId = null)
        assertTrue(store.picked(4, listOf(becca)).isEmpty())
    }

    @Test
    fun `the same number written another way finds the pick`() {
        store.pick(becca, PersonHue.PEACH, threadId = 4)
        assertEquals(PersonHue.PEACH, store.picked(4, listOf(Person("972-555-0111")))[key])
    }
}
