package io.github.secnewt.messages.notify

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.secnewt.messages.chats.Chat
import io.github.secnewt.messages.chats.Person
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ConversationsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val system = context.getSystemService(NotificationManager::class.java)

    @Test
    fun `sound and alerts gets the chat its own channel, so the shared one isn't changed`() {
        val chat = Chat(42, listOf(Person("+19725550111", "Becca")), "hi", 0)
        Conversations.settings(context, chat)
        val own = system.notificationChannels.single { it.conversationId == "thread-42" }
        assertEquals(Notifier.CHANNEL_MESSAGES, own.parentChannelId)
        // Opening it again doesn't make a second one.
        Conversations.settings(context, chat)
        assertEquals(1, system.notificationChannels.count { it.conversationId == "thread-42" })
    }
}
