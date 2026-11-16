package io.github.secnewt.messages.chats

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ShareOutTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun picture(name: String, bytes: ByteArray): String =
        Uri.fromFile(File(context.filesDir, name).apply { writeBytes(bytes) }).toString()

    // Robolectric can't read the FileProvider's folder list, so the test gives its own link.
    private val link = { file: File -> Uri.parse("content://${context.packageName}.mmsfiles/share/${file.name}") }

    private fun share(from: String, name: String, type: String): Intent? = ShareOut.intent(context, from, name, type, link)

    @Suppress("DEPRECATION")
    private fun Intent.inner(): Intent = getParcelableExtra(Intent.EXTRA_INTENT)!!

    @Test
    fun `a picture opens the share sheet with a copy other apps may read`() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val sheet = share(picture("in.png", bytes), "picture", "image/png")
        assertNotNull(sheet)
        assertEquals(Intent.ACTION_CHOOSER, sheet!!.action)
        val send = sheet.inner()
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("image/png", send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        @Suppress("DEPRECATION")
        val shared = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content://${context.packageName}.mmsfiles/share/picture.png", shared.toString())
        assertEquals(shared, send.clipData!!.getItemAt(0).uri)
        assertArrayEquals(bytes, File(File(context.cacheDir, "share"), "picture.png").readBytes())
    }

    @Test
    fun `only the last shared picture is kept`() {
        share(picture("a.jpg", byteArrayOf(1)), "first", "image/jpeg")
        share(picture("b.jpg", byteArrayOf(2)), "second", "image/jpeg")
        val folder = File(context.cacheDir, "share")
        assertFalse(File(folder, "first.jpg").exists())
        assertEquals(listOf("second.jpg"), folder.list()!!.toList())
    }

    @Test
    fun `a name from the sender can't reach outside the share folder`() {
        share(picture("c.jpg", byteArrayOf(3)), "../../evil", "image/jpeg")
        assertEquals(listOf("evil.jpg"), File(context.cacheDir, "share").list()!!.toList())
    }

    @Test
    fun `a picture that can't be read gives nothing to share`() {
        assertNull(share(Uri.fromFile(File(context.filesDir, "gone.jpg")).toString(), "gone", "image/jpeg"))
    }
}
