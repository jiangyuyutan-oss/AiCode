package com.aicode.feature.agent.domain.tool.orchestration

import com.aicode.feature.agent.domain.model.AgentContext
import com.aicode.feature.agent.domain.model.AgentMode
import com.aicode.feature.agent.domain.orchestration.OrchestratorService
import com.aicode.feature.agent.domain.orchestration.OrchestratorService.DeploySpec
import com.aicode.feature.agent.domain.tool.ToolResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrchestrateToolTest {

    private val orchestrator = mockk<OrchestratorService>(relaxed = true)
    private val tool = OrchestrateTool(orchestrator)

    private fun context(mode: AgentMode) = AgentContext(
        currentFile = null,
        selectedCode = null,
        projectRoot = "/workspace",
        language = null,
        sessionId = "root",
        mode = mode
    )

    private fun tasks(vararg prompts: String): JsonElement = buildJsonObject {
        put("tasks", buildJsonArray {
            prompts.forEach { prompt ->
                add(buildJsonObject { put("name", "任务"); put("prompt", prompt) })
            }
        })
    }

    @Test
    fun nonGodMode_returnsNotGodModeError() = runTest {
        val r = tool.executeWithContext(
            mapOf("phase" to JsonPrimitive("decompose"), "tasks" to tasks("做A")),
            context(AgentMode.BUILD)
        )
        assertTrue(r is ToolResult.Error)
        assertEquals("NOT_GOD_MODE", (r as ToolResult.Error).code)
    }

    @Test
    fun unknownPhase_returnsInvalidPhase() = runTest {
        val r = tool.executeWithContext(
            mapOf("phase" to JsonPrimitive("bogus")),
            context(AgentMode.GOD)
        )
        assertTrue(r is ToolResult.Error)
        assertEquals("INVALID_PHASE", (r as ToolResult.Error).code)
    }

    @Test
    fun decompose_missingTasks_returnsError() = runTest {
        val r = tool.executeWithContext(
            mapOf("phase" to JsonPrimitive("decompose")),
            context(AgentMode.GOD)
        )
        assertTrue(r is ToolResult.Error)
        assertEquals("MISSING_TASKS", (r as ToolResult.Error).code)
    }

    @Test
    fun decompose_delegatesToOrchestratorAndReturnsSuccess() = runTest {
        coEvery { orchestrator.parseDeploySpecs(any()) } returns
            listOf(DeploySpec("任务", null, null, null, "做A"))
        coEvery { orchestrator.deploy(any(), any()) } returns
            (listOf(OrchestratorService.Deployed("s1", "任务", null, null, null)) to emptyList())

        val args = mapOf(
            "phase" to JsonPrimitive("decompose"),
            "goal" to JsonPrimitive("整体目标"),
            "tasks" to tasks("做A")
        )
        val r = tool.executeWithContext(args, context(AgentMode.GOD))

        assertTrue(r is ToolResult.Success)
        val data = (r as ToolResult.Success).data.toString()
        coVerify(exactly = 1) { orchestrator.deploy(any(), any()) }
        assertTrue(data.contains("\"deployedCount\":1"))
        assertTrue(data.contains("\"id\":\"s1\""))
    }

    @Test
    fun review_blankDiff_returnsError() = runTest {
        val r = tool.executeWithContext(
            mapOf("phase" to JsonPrimitive("review"), "diffSpec" to JsonPrimitive("  ")),
            context(AgentMode.GOD)
        )
        assertTrue(r is ToolResult.Error)
        assertEquals("MISSING_DIFF", (r as ToolResult.Error).code)
    }
}