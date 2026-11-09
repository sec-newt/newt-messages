package io.github.secnewt.messages.chats

import io.github.secnewt.messages.sms.DeliveryReports
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendStatusTest {

    private val now = 100 * 60_000L

    @Test
    fun `texts read their status from their box and delivery report`() {
        assertEquals(SendStatus.NONE, SendStatus.ofSms(1, -1, now, now)) // Inbox
        assertEquals(SendStatus.SENT, SendStatus.ofSms(2, -1, now, now))
        assertEquals(SendStatus.SENT, SendStatus.ofSms(2, 32, now, now)) // Still trying to deliver
        assertEquals(SendStatus.DELIVERED, SendStatus.ofSms(2, 0, now, now))
        assertEquals(SendStatus.NOT_DELIVERED, SendStatus.ofSms(2, 64, now, now))
        assertEquals(SendStatus.FAILED, SendStatus.ofSms(5, -1, now, now))
        assertEquals(SendStatus.SENDING, SendStatus.ofSms(4, -1, now - 60_000, now))
        assertEquals(SendStatus.SENDING, SendStatus.ofSms(6, -1, now - 60_000, now)) // Queued
    }

    @Test
    fun `a message stuck sending for over ten minutes shows as failed`() {
        assertEquals(SendStatus.FAILED, SendStatus.ofSms(4, -1, now - 11 * 60_000, now))
        assertEquals(SendStatus.FAILED, SendStatus.ofMms(4, now - 11 * 60_000, now))
        assertEquals(SendStatus.SENDING, SendStatus.ofMms(4, now - 9 * 60_000, now))
    }

    @Test
    fun `picture messages read their status from their box`() {
        assertEquals(SendStatus.NONE, SendStatus.ofMms(1, now, now))
        assertEquals(SendStatus.SENT, SendStatus.ofMms(2, now, now))
        assertEquals(SendStatus.FAILED, SendStatus.ofMms(5, now, now))
    }

    @Test
    fun `only not sent and not delivered count as failed`() {
        assertTrue(SendStatus.FAILED.failed)
        assertTrue(SendStatus.NOT_DELIVERED.failed)
        SendStatus.entries.filterNot { it == SendStatus.FAILED || it == SendStatus.NOT_DELIVERED }.forEach { assertFalse(it.name, it.failed) }
    }

    @Test
    fun `GSM delivery reports`() {
        assertEquals(DeliveryReports.COMPLETE, DeliveryReports.stored(0, "3gpp"))
        assertEquals(DeliveryReports.PENDING, DeliveryReports.stored(0x20, "3gpp"))
        assertEquals(DeliveryReports.FAILED, DeliveryReports.stored(0x40, "3gpp"))
        assertEquals(DeliveryReports.FAILED, DeliveryReports.stored(0x65, "3gpp"))
    }

    @Test
    fun `CDMA delivery reports`() {
        assertEquals(DeliveryReports.COMPLETE, DeliveryReports.stored(2 shl 16, "3gpp2"))
        assertEquals(DeliveryReports.PENDING, DeliveryReports.stored((2 shl 24) or (4 shl 16), "3gpp2"))
        assertEquals(DeliveryReports.FAILED, DeliveryReports.stored((3 shl 24) or (5 shl 16), "3gpp2"))
    }
}
