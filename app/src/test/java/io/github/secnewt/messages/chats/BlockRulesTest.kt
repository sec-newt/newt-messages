package io.github.secnewt.messages.chats

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockRulesTest {

    @Test
    fun `a number starting with a blocked prefix is blocked, however it's written`() {
        val rules = listOf("800555", "469555012")
        assertTrue(BlockRules.blocks(rules, "+1 (800) 555-0199"))
        assertTrue(BlockRules.blocks(rules, "8005550199"))
        assertTrue(BlockRules.blocks(rules, "+14695550123"))
        assertFalse(BlockRules.blocks(rules, "+19725550111"))
    }

    @Test
    fun `no rules, hidden numbers and named senders are never blocked`() {
        assertFalse(BlockRules.blocks(emptyList(), "+18005550199"))
        assertFalse(BlockRules.blocks(listOf("800"), null))
        assertFalse(BlockRules.blocks(listOf("800"), "AMAZON"))
        assertFalse(BlockRules.blocks(listOf("", "abc"), "+18005550199"))
    }
}
