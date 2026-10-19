package io.github.secnewt.messages.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsStoreTest {

    private val store = SettingsStore(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `defaults: quiet reactions, not speaking`() {
        assertEquals(MessagesSettings(ReactionNotify.QUIET, SpeakMode.OFF, quietDuringDnd = true), store.settings())
    }

    @Test
    fun `settings and each chat's pin and mute are kept`() {
        val changed = MessagesSettings(ReactionNotify.OFF, SpeakMode.HEADPHONES_ONLY, quietDuringDnd = false)
        store.save(changed)
        assertEquals(changed, store.settings())
        store.setPinned(4, true)
        store.setMuted(5, true)
        assertTrue(store.pinned(4))
        assertFalse(store.pinned(5))
        assertTrue(store.muted(5))
        store.setMuted(5, false)
        assertFalse(store.muted(5))
    }
}
