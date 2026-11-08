package io.github.secnewt.messages.mms

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupRecipientsTest {

    // Digits only, last ten: "+1 (972) 555-0100" and "9725550100" are the same number.
    private val same = { a: String, b: String -> a.filter(Char::isDigit).takeLast(10) == b.filter(Char::isDigit).takeLast(10) }

    private val me = "+19725550100"
    private val jerry = "9725550199"
    private val shilo = "+12545550121"
    private val landon = "+12545550120"

    private fun pick(from: String, to: List<String>, cc: List<String> = emptyList(), mine: List<String> = listOf(me)) =
        GroupRecipients.pick(listOf(from), to, cc, mine, same)

    @Test
    fun `everyone in To, minus me`() {
        assertEquals(setOf(shilo, jerry, landon), pick(shilo, listOf(me, jerry, landon)))
    }

    @Test
    fun `one person in To and the rest in Cc keeps everyone`() {
        // The bug: Android assumed a lone "To" was me and dropped Jerry, so this went to Shilo and Landon's group.
        assertEquals(setOf(shilo, jerry, landon), pick(shilo, listOf(jerry), cc = listOf(me, landon)))
    }

    @Test
    fun `me alone in To with the rest in Cc`() {
        assertEquals(setOf(shilo, jerry, landon), pick(shilo, listOf(me), cc = listOf(jerry, landon)))
    }

    @Test
    fun `a one-to-one picture message is just the sender`() {
        assertEquals(setOf(shilo), pick(shilo, listOf(me)))
    }

    @Test
    fun `my number written differently is still me`() {
        assertEquals(setOf(shilo, landon), pick(shilo, listOf("(972) 555-0100", landon)))
    }

    @Test
    fun `the sender listed again in To isn't counted twice`() {
        assertEquals(setOf(shilo, landon), pick(shilo, listOf(me, "2545550121", landon)))
    }

    @Test
    fun `any of my SIMs' numbers is me`() {
        assertEquals(setOf(shilo, landon), pick(shilo, listOf("+14695550111", landon), mine = listOf(me, "+14695550111")))
    }

    @Test
    fun `without my number, a lone To is still taken to be me`() {
        assertEquals(setOf(shilo), pick(shilo, listOf(me), mine = emptyList()))
        assertEquals(setOf(shilo, landon), pick(shilo, listOf(me), cc = listOf(landon), mine = listOf("")))
    }
}
