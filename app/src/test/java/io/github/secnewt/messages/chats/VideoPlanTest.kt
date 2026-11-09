package io.github.secnewt.messages.chats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlanTest {

    @Test
    fun `a short clip fits a 1 MB limit at a watchable size`() {
        val plan = VideoPlan.plan(durationMillis = 10_000, maxBytes = 1_000_000)!!
        assertEquals(480, plan.height)
        // Video plus sound stays under the limit.
        assertTrue((plan.videoBitsPerSecond + VideoPlan.AUDIO_BITS_PER_SECOND) / 8 * 10 < 1_000_000)
    }

    @Test
    fun `longer clips get smaller pictures`() {
        assertEquals(240, VideoPlan.plan(60_000, 1_000_000)!!.height)
    }

    @Test
    fun `too long to fit at any watchable quality`() {
        assertNull(VideoPlan.plan(10 * 60_000, 300_000))
        assertNull(VideoPlan.plan(0, 300_000))
        assertTrue(VideoPlan.longestSeconds(300_000) in 20..30)
    }
}
