package io.github.secnewt.messages.mms

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.secnewt.messages.mms.pdu.AcknowledgeInd
import io.github.secnewt.messages.mms.pdu.CharacterSets
import io.github.secnewt.messages.mms.pdu.EncodedStringValue
import io.github.secnewt.messages.mms.pdu.NotificationInd
import io.github.secnewt.messages.mms.pdu.PduBody
import io.github.secnewt.messages.mms.pdu.PduComposer
import io.github.secnewt.messages.mms.pdu.PduHeaders
import io.github.secnewt.messages.mms.pdu.PduParser
import io.github.secnewt.messages.mms.pdu.PduPart
import io.github.secnewt.messages.mms.pdu.RetrieveConf
import io.github.secnewt.messages.mms.pdu.SendReq
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The copied MMS code reads and writes messages the way carriers send them. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MmsPduTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun bytes(vararg parts: Any): ByteArray = parts.flatMap { p ->
        when (p) {
            is Int -> listOf(p.toByte())
            is String -> (p.toByteArray(Charsets.US_ASCII) + 0).toList()
            else -> error("unexpected")
        }
    }.toByteArray()

    /** What a carrier pushes when a picture message is waiting (an m-notification-ind). */
    private val notice = bytes(
        0x8C, 0x82, // message type: notification
        0x98, "T1", // transaction id
        0x8D, 0x92, // MMS version 1.2
        0x89, 0x15, 0x80, "+15550111/TYPE=PLMN", // from (value length 21, address present)
        0x8A, 0x80, // class: personal
        0x8E, 0x03, 0x01, 0x00, 0x00, // size: 65536
        0x88, 0x05, 0x81, 0x03, 0x09, 0x3A, 0x80, // expiry: in 7 days
        0x83, "http://mmsc.example/abc", // where to fetch it
    )

    @Test
    fun `a carrier's notice is read`() {
        val parsed = PduParser(notice, true).parse()
        assertTrue("parsed as ${parsed?.javaClass}", parsed is NotificationInd)
        parsed as NotificationInd
        assertEquals("http://mmsc.example/abc", String(parsed.contentLocation))
        assertArrayEquals("T1".toByteArray(), parsed.transactionId)
        assertEquals("+15550111/TYPE=PLMN", parsed.from.string)
    }

    private fun request(): SendReq = SendReq().apply {
        setTo(arrayOf(EncodedStringValue("+19725550111"), EncodedStringValue("+12145550122")))
        setDate(1_800_000_000L)
        setBody(
            PduBody().apply {
                addPart(
                    PduPart().apply {
                        setCharset(CharacterSets.UTF_8)
                        setContentType("text/plain".toByteArray())
                        setContentLocation("text.txt".toByteArray())
                        setContentId("<text>".toByteArray())
                        setData("Brisket at 6 🍖".toByteArray(Charsets.UTF_8))
                    },
                )
                addPart(
                    PduPart().apply {
                        setContentType("image/jpeg".toByteArray())
                        setContentLocation("image0.jpg".toByteArray())
                        setContentId("<image0>".toByteArray())
                        setData(ByteArray(2000) { (it % 251).toByte() })
                    },
                )
            },
        )
    }

    @Test
    fun `a group picture message we send can be read back`() {
        val sent = PduComposer(context, request()).make()
        assertNotNull(sent)
        val parsed = PduParser(sent, true).parse() as SendReq
        assertEquals(listOf("+19725550111", "+12145550122"), parsed.to.map { it.string })
        assertEquals(2, parsed.body.partsNum)
        assertEquals("Brisket at 6 🍖", String(parsed.body.getPart(0).data, Charsets.UTF_8))
        assertEquals("image/jpeg", String(parsed.body.getPart(1).contentType))
        assertEquals(2000, parsed.body.getPart(1).data.size)
    }

    @Test
    fun `a downloaded message's words and picture are read`() {
        // A retrieved message has the same layout as a sent one, with a different type byte.
        val retrieved = PduComposer(context, request()).make()!!.copyOf()
        assertEquals(PduHeaders.MESSAGE_TYPE, retrieved[0].toInt() and 0xFF)
        retrieved[1] = PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF.toByte()
        val parsed = PduParser(retrieved, true).parse()
        assertTrue("parsed as ${parsed?.javaClass}", parsed is RetrieveConf)
        parsed as RetrieveConf
        assertEquals(2, parsed.body.partsNum)
        assertEquals("Brisket at 6 🍖", String(parsed.body.getPart(0).data, Charsets.UTF_8))
    }

    @Test
    fun `the 'got it' acknowledgement is built`() {
        val ack = PduComposer(context, AcknowledgeInd(PduHeaders.CURRENT_MMS_VERSION, "T1".toByteArray())).make()
        assertNotNull(ack)
        val parsed = PduParser(ack, true).parse() as AcknowledgeInd
        assertArrayEquals("T1".toByteArray(), parsed.transactionId)
    }
}
