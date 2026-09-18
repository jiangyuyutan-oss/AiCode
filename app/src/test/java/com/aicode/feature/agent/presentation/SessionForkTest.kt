package com.aicode.feature.agent.presentation

import com.aicode.feature.agent.data.local.entity.AgentMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionForkTest {

    private fun msg(id: String, role: String = "USER", content: String = "c", ts: Long = 0L) =
        AgentMessageEntity(
            id = id,
            sessionId = "src",
            role = role,
            content = content,
            timestamp = ts,
            toolName = null,
            toolArgs = null,
            inputTokens = 10,
            outputTokens = 5,
        )

    @Test
    fun fork_copiesThroughAnchorInclusive() {
        val source = listOf(msg("a", content = "c-a", ts = 1), msg("b", content = "c-b", ts = 2), msg("c", content = "c-c", ts = 3))
        val forked = buildForkedMessages(source, "b", "new")
        assertEquals(2, forked.size)
        assertEquals("c-a", forked[0].content)
        assertEquals("c-b", forked[1].content)
    }

    @Test
    fun fork_afterAnchorExcluded() {
        val source = listOf(msg("a", content = "c-a", ts = 1), msg("b", content = "c-b", ts = 2), msg("c", content = "c-c", ts = 3))
        val forked = buildForkedMessages(source, "a", "new")
        assertEquals(1, forked.size)
        assertEquals("c-a", forked[0].content)
    }

    @Test
    fun fork_generatesNewIdsAndSession() {
        val source = listOf(msg("a", ts = 1), msg("b", ts = 2))
        val forked = buildForkedMessages(source, "b", "new-session")
        forked.forEach { entity ->
            assertNotEquals("src", entity.sessionId)
            assertEquals("new-session", entity.sessionId)
            assertNotEquals(entity.id, "a")
        }
        // id 唯一
        assertNotEquals(forked[0].id, forked[1].id)
    }

    @Test
    fun fork_preservesFieldsAndOrder() {
        val source = listOf(
            msg("a", role = "USER", content = "hello", ts = 5),
            AgentMessageEntity(
                id = "t", sessionId = "src", role = "ASSISTANT", content = "ok",
                timestamp = 6, toolName = "run", toolArgs = "{\"cmd\":\"ls\"}", isError = true,
                inputTokens = 3, outputTokens = 4,
            ),
        )
        val forked = buildForkedMessages(source, "t", "new")
        assertEquals(2, forked.size)
        val tool = forked[1]
        assertEquals("ASSISTANT", tool.role)
        assertEquals("ok", tool.content)
        assertEquals("run", tool.toolName)
        assertEquals("{\"cmd\":\"ls\"}", tool.toolArgs)
        assertTrue(tool.isError)
        assertEquals(3, tool.inputTokens)
        assertEquals(4, tool.outputTokens)
        assertEquals(5, forked[0].timestamp)
        assertEquals(6, forked[1].timestamp)
    }

    @Test
    fun fork_anchorNotFound_returnsEmpty() {
        val source = listOf(msg("a"), msg("b"))
        assertTrue(buildForkedMessages(source, "nope", "new").isEmpty())
    }

    @Test
    fun fork_emptySource_returnsEmpty() {
        assertTrue(buildForkedMessages(emptyList(), "a", "new").isEmpty())
    }
}
