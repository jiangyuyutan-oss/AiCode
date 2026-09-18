package com.aicode.feature.agent.domain.tool.mode

import com.aicode.feature.agent.data.local.dao.ChatSessionDao
import com.aicode.feature.agent.data.local.entity.ChatSessionEntity
import com.aicode.feature.agent.domain.model.AgentContext
import com.aicode.feature.agent.domain.model.AgentMode
import com.aicode.feature.agent.domain.model.GoalTerminationReason
import com.aicode.feature.agent.domain.tool.ToolResult
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SwitchModeTool] 的 TARGET 相关分支：切 TARGET 必须带 goal、AUTO 只能切 PLAN 退出、
 * 进入 TARGET 重置步数/失败计数、从 TARGET 切出记录 INTERRUPTED。
 */
class SwitchModeToolTest {

    private val dao = mockk<ChatSessionDao>(relaxed = true)
    private val tool = SwitchModeTool(dao)

    private fun context(mode: AgentMode) = AgentContext(
        currentFile = null,
        selectedCode = null,
        projectRoot = "/workspace",
        language = null,
        sessionId = "s1",
        mode = mode
    )

    private fun session(mode: String) = ChatSessionEntity(
        id = "s1",
        title = "t",
        createdAt = 0,
        updatedAt = 0,
        mode = mode,
        goalStatement = "旧目标",
        goalStepCount = 12,
        goalFailCount = 3
    )

    private fun args(
        mode: String,
        reason: String = "r",
        goal: String? = null
    ): Map<String, JsonElement> = buildMap {
        put("mode", JsonPrimitive(mode))
        put("reason", JsonPrimitive(reason))
        if (goal != null) put("goal", JsonPrimitive(goal))
    }

    @Test
    fun missingMode_returnsError() = runTest {
        val r = tool.executeWithContext(mapOf("reason" to JsonPrimitive("r")), context(AgentMode.BUILD))
        assertTrue(r is ToolResult.Error)
        assertEquals("MISSING_MODE", (r as ToolResult.Error).code)
    }

    @Test
    fun invalidMode_returnsError() = runTest {
        val r = tool.executeWithContext(args("WHATEVER"), context(AgentMode.BUILD))
        assertTrue(r is ToolResult.Error)
        assertEquals("INVALID_MODE", (r as ToolResult.Error).code)
    }

    @Test
    fun autoMode_isRejectedAsTarget() = runTest {
        val r = tool.executeWithContext(args("AUTO"), context(AgentMode.BUILD))
        assertTrue(r is ToolResult.Error)
        assertEquals("AUTO_MODE_MANUAL_ONLY", (r as ToolResult.Error).code)
    }

    @Test
    fun fromAuto_canOnlyExitToPlan() = runTest {
        val r = tool.executeWithContext(args("BUILD"), context(AgentMode.AUTO))
        assertTrue(r is ToolResult.Error)
        assertEquals("AUTO_EXIT_PLAN_ONLY", (r as ToolResult.Error).code)
    }

    @Test
    fun fromAuto_toPlan_succeeds() = runTest {
        coEvery { dao.getById("s1") } returns session(AgentMode.AUTO.name)
        val r = tool.executeWithContext(args("PLAN"), context(AgentMode.AUTO))
        assertTrue(r is ToolResult.Success)
    }

    @Test
    fun toTarget_requiresGoal() = runTest {
        val r = tool.executeWithContext(args("TARGET", goal = null), context(AgentMode.BUILD))
        assertTrue(r is ToolResult.Error)
        assertEquals("MISSING_GOAL", (r as ToolResult.Error).code)
    }

    @Test
    fun sameMode_returnsSuccessWithoutDbWrite() = runTest {
        val r = tool.executeWithContext(args("BUILD"), context(AgentMode.BUILD))
        assertTrue(r is ToolResult.Success)
        io.mockk.coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun toGod_succeedsAndPersistsMode() = runTest {
        coEvery { dao.getById("s1") } returns session(AgentMode.BUILD.name)
        val upserted = slot<ChatSessionEntity>()
        coEvery { dao.upsert(capture(upserted)) } just runs

        val r = tool.executeWithContext(args("GOD"), context(AgentMode.BUILD))

        assertTrue(r is ToolResult.Success)
        assertEquals(AgentMode.GOD.name, upserted.captured.mode)
    }

    @Test
    fun fromGod_toBuild_succeeds() = runTest {
        coEvery { dao.getById("s1") } returns session(AgentMode.GOD.name)
        val upserted = slot<ChatSessionEntity>()
        coEvery { dao.upsert(capture(upserted)) } just runs

        val r = tool.executeWithContext(args("BUILD"), context(AgentMode.GOD))

        assertTrue(r is ToolResult.Success)
        assertEquals(AgentMode.BUILD.name, upserted.captured.mode)
    }

    @Test
    fun success_toTarget_resetsCountersAndSavesGoal() = runTest {
        coEvery { dao.getById("s1") } returns session(AgentMode.BUILD.name)
        val upserted = slot<ChatSessionEntity>()
        coEvery { dao.upsert(capture(upserted)) } just runs

        val r = tool.executeWithContext(args("TARGET", goal = "跑通全部单测"), context(AgentMode.BUILD))

        assertTrue(r is ToolResult.Success)
        assertEquals(AgentMode.TARGET.name, upserted.captured.mode)
        assertEquals("跑通全部单测", upserted.captured.goalStatement)
        assertEquals(0, upserted.captured.goalStepCount)
        assertEquals(0, upserted.captured.goalFailCount)
    }

    @Test
    fun fromTarget_recordsInterrupted() = runTest {
        coEvery { dao.getById("s1") } returns session(AgentMode.TARGET.name)
        val upserted = slot<ChatSessionEntity>()
        coEvery { dao.upsert(capture(upserted)) } just runs

        val r = tool.executeWithContext(args("BUILD"), context(AgentMode.TARGET))

        assertTrue(r is ToolResult.Success)
        assertEquals(AgentMode.BUILD.name, upserted.captured.mode)
        assertEquals(GoalTerminationReason.INTERRUPTED.name, upserted.captured.goalTerminationReason)
        // 中断切出不清零：步数/失败计数保留供检查点回滚参考
        assertEquals(12, upserted.captured.goalStepCount)
        assertEquals(3, upserted.captured.goalFailCount)
    }
}
