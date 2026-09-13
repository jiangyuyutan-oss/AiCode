package com.aicode.feature.agent.domain.tool.mode

import com.aicode.feature.agent.data.local.dao.ChatSessionDao
import com.aicode.feature.agent.data.local.entity.ChatSessionEntity
import com.aicode.feature.agent.domain.model.AgentContext
import com.aicode.feature.agent.domain.model.AgentMode
import com.aicode.feature.agent.domain.model.GoalTerminationReason
import com.aicode.feature.agent.domain.tool.ToolResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CompleteGoalTool] 的分支覆盖：仅 TARGET 模式可用、summary 必需非空、
 * 成功时把会话切回 BUILD 且终止原因记 ACHIEVED。
 */
class CompleteGoalToolTest {

    private val dao = mockk<ChatSessionDao>(relaxed = true)
    private val tool = CompleteGoalTool(dao)

    private fun context(mode: AgentMode) = AgentContext(
        currentFile = null,
        selectedCode = null,
        projectRoot = "/workspace",
        language = null,
        sessionId = "s1",
        mode = mode
    )

    private fun session(mode: String = AgentMode.TARGET.name) = ChatSessionEntity(
        id = "s1",
        title = "t",
        createdAt = 0,
        updatedAt = 0,
        mode = mode,
        goalStatement = "完成单测"
    )

    @Test
    fun notInTargetMode_returnsError() = runTest {
        val r = tool.executeWithContext(
            mapOf("summary" to JsonPrimitive("done")),
            context(AgentMode.BUILD)
        )
        assertTrue(r is ToolResult.Error)
        assertEquals("NOT_IN_TARGET_MODE", (r as ToolResult.Error).code)
    }

    @Test
    fun missingSummary_returnsError() = runTest {
        val r = tool.executeWithContext(emptyMap(), context(AgentMode.TARGET))
        assertTrue(r is ToolResult.Error)
        assertEquals("MISSING_SUMMARY", (r as ToolResult.Error).code)
    }

    @Test
    fun emptySummary_returnsError() = runTest {
        val r = tool.executeWithContext(
            mapOf("summary" to JsonPrimitive("   ")),
            context(AgentMode.TARGET)
        )
        assertTrue(r is ToolResult.Error)
        assertEquals("EMPTY_SUMMARY", (r as ToolResult.Error).code)
    }

    @Test
    fun noSession_returnsError() = runTest {
        val noSession = context(AgentMode.TARGET).let { it.copy(sessionId = null) }
        val r = tool.executeWithContext(
            mapOf("summary" to JsonPrimitive("done")),
            noSession
        )
        assertTrue(r is ToolResult.Error)
        assertEquals("NO_SESSION", (r as ToolResult.Error).code)
    }

    @Test
    fun sessionNotFound_returnsError() = runTest {
        coEvery { dao.getById("s1") } returns null
        val r = tool.executeWithContext(
            mapOf("summary" to JsonPrimitive("done")),
            context(AgentMode.TARGET)
        )
        assertTrue(r is ToolResult.Error)
        assertEquals("SESSION_NOT_FOUND", (r as ToolResult.Error).code)
    }

    @Test
    fun success_updatesSessionToBuildWithAchieved() = runTest {
        coEvery { dao.getById("s1") } returns session()
        val upserted = slot<ChatSessionEntity>()
        coEvery { dao.upsert(capture(upserted)) } just runs

        val r = tool.executeWithContext(
            mapOf("summary" to JsonPrimitive("全部用例通过")),
            context(AgentMode.TARGET)
        )

        assertTrue(r is ToolResult.Success)
        assertEquals(AgentMode.BUILD.name, upserted.captured.mode)
        assertEquals(GoalTerminationReason.ACHIEVED.name, upserted.captured.goalTerminationReason)
        coVerify(exactly = 1) { dao.upsert(any()) }
    }

    @Test
    fun buildPermissionRequest_carriesSummaryInDetails() {
        val request = tool.buildPermissionRequest(
            callId = "c1",
            args = mapOf("summary" to JsonPrimitive("目标达成总结")),
            argsPreview = "preview"
        )
        assertEquals("completeGoal", request.toolName)
        assertEquals("c1", request.id)
        assertTrue(request.details.contains("目标达成总结"))
        assertTrue(request.rememberablePatterns.isEmpty())
    }
}
