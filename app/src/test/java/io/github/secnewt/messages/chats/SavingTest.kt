package io.github.secnewt.messages.chats

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SavingTest {

    @Test
    fun `sent file names can't reach other folders`() {
        assertEquals("passwd.vcf", Saving.fileName("../../etc/passwd", "text/x-vcard"))
        assertEquals("bulletin.pdf", Saving.fileName("bulletin.pdf", "application/pdf"))
        assertEquals("hidden.jpg", Saving.fileName(".hidden", "image/jpeg"))
        assertEquals("attachment.jpg", Saving.fileName("", "image/jpeg"))
        assertEquals("Mom_s card.vcf", Saving.fileName("Mom's card", "text/x-vcard"))
    }
}
