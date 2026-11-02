package io.github.secnewt.messages.chats

import org.junit.Assert.assertEquals
import org.junit.Test

class SharingTest {

    private fun file(type: String, name: String = "f") = Attachment("content://x/$name", name, type)

    @Test
    fun `the banner says what's being shared`() {
        assertEquals("Send a picture to…", Sharing.label(listOf(file("image/png")), ""))
        assertEquals("Send 3 pictures to…", Sharing.label(List(3) { file("image/jpeg") }, "look"))
        assertEquals("Send a video to…", Sharing.label(listOf(file("video/mp4")), ""))
        assertEquals("Send card.vcf to…", Sharing.label(listOf(file("text/x-vcard", "card.vcf")), ""))
        assertEquals("Send 2 files to…", Sharing.label(listOf(file("image/png"), file("video/mp4")), ""))
        assertEquals("Send these words to…", Sharing.label(emptyList(), "https://example.com"))
    }
}
