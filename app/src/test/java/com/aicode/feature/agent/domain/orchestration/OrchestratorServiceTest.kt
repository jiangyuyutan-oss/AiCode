package com.aicode.feature.agent.domain.orchestration

import com.aicode.feature.agent.data.local.dao.ChatSessionDao
import com.aicode.feature.agent.data.local.entity.ChatSessionEntity
import com.aicode.feature.agent.domain.model.AgentContext
import com.aicode.feature.agent.domain.model.AgentMode
import com.aicode.feature.agent.domain.orchestration.OrchestratorService.DeploySpec
import com.aicode.feature.agent.domain.session.SessionUseCase
import com.aicode.feature.agent.domain.subagent.AgentDefinition
import com.aicode.feature.agent.domain.subagent.AgentDefinitionEntry
import com.aicode.feature.agent.domain.subagent.AgentDefinitionRepository
import com.aicode.feature.agent.domain.subagent.AgentDefinitionScope
import com.aicode.feature.agent.domain.subagent.SubAgentEvent
import com.aicode.feature.agent.domain.subagent.SubAgentEventBus
import com.aicode.feature.agent.domain.subagent.SubAgentEventType
import com.aicode.feature.settings.domain.model.AIProviderConfig
import com.aicode.feature.settings.domain.model.ProviderType
import com.aicode.feature.settings.domain.repository.AIProviderRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrchestratorServiceTest {

    private val chatSessionDao = mockk<ChatSessionDao>(relaxed = true)
    private val sessionUseCase = mockk<SessionUseCase>(relaxed = true)
    private val agentRepo = mockk<AgentDefinitionRepository>(relaxed = true)
    private val providerRepo = mockk<AIProviderRepository>(relaxed = true)
    private val eventBus = mockk<SubAgentEventBus>(relaxed = true)

    private val dummyProvider = AIProviderConfig(
        id = "prov",
        name = "default",
        type = ProviderType.OPENAI,
        apiKey = "k",
        baseUrl = "https://example.invalid",
        defaultModel = "m",
        selectedModel = "m"
    )

    private fun context(mode: AgentMode = AgentMode.GOD) = AgentContext(
        currentFile = null,
        selectedCode = null,
        projectRoot = "/workspace",
        language = null,
        sessionId = "root",
        mode = mode
    )

    private fun parentEntity() = ChatSessionEntity(
        id = "root",
        title = "root",
        createdAt = 0L,
        updatedAt = 0L,
        mode = AgentMode.GOD.name
    )

    private val activeIds: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())

    private fun buildService(): OrchestratorService {
        every { eventBus.isFull } returns false
        every { eventBus.activeSubSessionIds } returns activeIds
        return OrchestratorService(
            sessionUseCase = sessionUseCase,
            chatSessionDao = chatSessionDao,
            eventBus = eventBus,
            agentDefinitionRepository = agentRepo,
            aiProviderRepository = providerRepo
        )
    }

    private fun task(name: String, prompt: String, agent: String? = null) = buildJsonObject {
        put("name", name)
        if (agent != null) put("agent", agent)
        put("prompt", prompt)
    }

    private fun stubSubSessionCreation() {
        coEvery {
            sessionUseCase.newSubSessionEntity(any(), any(), any(), any(), any(), any(), any())
        } answers { call ->
            val title = call.invocation.args[0] as String
            val parentId = call.invocation.args[1] as String
            val parent = call.invocation.args[2] as ChatSessionEntity
            val subType = call.invocation.args[3] as String
            ChatSessionEntity(
                id = "sub-$title",
                title = title,
                createdAt = 0L,
                updatedAt = 0L,
                parentId = parentId,
                subagentType = subType,
                mode = parent.mode
            )
        }
    }

    private fun stubCommon() {
        coEvery { chatSessionDao.getById("root") } returns parentEntity()
        coEvery { sessionUseCase.getSessionById("root") } returns parentEntity()
        coEvery { providerRepo.getProviderById(any()) } returns null
        every { providerRepo.getAllProviders() } returns flowOf(listOf(dummyProvider))
    }

    // ── parseDeploySpecs ─────────────────────────────────────────

    @Test
    fun parseDeploySpecs_dropsMissingPrompt() {
        val raw = buildJsonArray {
            add(task("A", "第一个任务"))
            add(buildJsonObject { put("name", "缺 prompt"); put("agent", "x") })
            add(task("B", "第二个任务", agent = "coder"))
        }
        val specs = buildService().parseDeploySpecs(raw)
        assertEquals(2, specs.size)
        assertEquals("A", specs[0].name)
        assertEquals("coder", specs[1].agent)
        assertEquals("第一个任务", specs[0].prompt)
    }

    @Test
    fun parseDeploySpecs_nonArrayReturnsEmpty() {
        assertEquals(0, buildService().parseDeploySpecs(JsonPrimitive("oops")).size)
        assertEquals(0, buildService().parseDeploySpecs(null).size)
    }

    @Test
    fun parseDeploySpecs_defaultsNameForNameless() {
        val raw = buildJsonArray { add(buildJsonObject { put("prompt", "只有 prompt") }) }
        val specs = buildService().parseDeploySpecs(raw)
        assertEquals(1, specs.size)
        assertEquals("部门任务", specs[0].name)
    }

    // ── deploy ────────────────────────────────────────────────

    @Test
    fun deploy_withoutSession_returnsError() = runTest {
        val svc = buildService()
        val ctx = context().copy(sessionId = null)
        val (deployed, errors) = svc.deploy(ctx, listOf(DeploySpec("a", null, null, null, "p")))
        assertTrue(deployed.isEmpty())
        assertTrue(errors.any { it.contains("缺少会话上下文") })
    }

    @Test
    fun deploy_emptySpecs_returnsError() = runTest {
        val svc = buildService()
        val (deployed, errors) = svc.deploy(context(), emptyList())
        assertTrue(deployed.isEmpty())
        assertTrue(errors.any { it.contains("没有可派发") })
    }

    @Test
    fun deploy_unknownAgent_reportsAndSkips() = runTest {
        stubCommon()
        every { agentRepo.find("nope") } returns null
        every { agentRepo.listEnabled() } returns emptyList()

        val svc = buildService()
        val (deployed, errors) = svc.deploy(
            context(),
            listOf(DeploySpec("部门X", "nope", null, null, "p"))
        )
        assertTrue(deployed.isEmpty())
        assertTrue(errors.any { it.contains("没有名为「nope」") })
    }

    @Test
    fun deploy_emitsSpawnedAndTracks() = runTest {
        stubCommon()
        stubSubSessionCreation()
        val coderDef = AgentDefinition(name = "coder", description = "d", prompt = "p")
        every { agentRepo.find("coder") } returns coderDef
        every { agentRepo.listEnabled() } returns listOf(
            AgentDefinitionEntry(coderDef, AgentDefinitionScope.GLOBAL)
        )

        val svc = buildService()
        val (deployed, errors) = svc.deploy(
            context(),
            listOf(DeploySpec("任务A", null, null, null, "做A"), DeploySpec("任务B", "coder", null, null, "做B"))
        )

        val captured = mutableListOf<SubAgentEvent>()
        coVerify(exactly = 2) { eventBus.emit(capture(captured)) }

        assertEquals(0, errors.size)
        assertEquals(2, deployed.size)
        assertTrue(captured.all { it.type == SubAgentEventType.SPAWNED })
        assertTrue(captured.all { it.parentSessionId == "root" })
    }

    @Test
    fun deploy_capsAtMaxBatchDepartments() = runTest {
        stubCommon()
        stubSubSessionCreation()

        val svc = buildService()
        val specs = (1..8).map { DeploySpec("任务$it", null, null, null, "prompt$it") }
        val (deployed, errors) = svc.deploy(context(), specs)

        assertEquals(5, deployed.size)
        assertTrue(errors.any { it.contains("一次最多派发") })
        coVerify(exactly = 5) { eventBus.emit(any()) }
    }

    // ── spawnJury ─────────────────────────────────────────────

    @Test
    fun spawnJury_blankDiff_returnsError() = runTest {
        val svc = buildService()
        val (deployed, errors) = svc.spawnJury(context(), "   ", false)
        assertTrue(deployed.isEmpty())
        assertTrue(errors.any { it.contains("diffSpec") })
    }

    @Test
    fun spawnJury_withoutForeman_spawnsThreeReviewers() = runTest {
        stubCommon()
        stubSubSessionCreation()
        every { agentRepo.find(any()) } answers { call ->
            val name = call.invocation.args[0] as String
            if (name.startsWith("review-")) {
                AgentDefinition(name = name, description = "d", prompt = "p")
            } else {
                null
            }
        }
        every { agentRepo.listEnabled() } returns emptyList()

        val svc = buildService()
        val (deployed, errors) = svc.spawnJury(context(), "改动了 foo.kt", false)

        assertEquals(0, errors.size)
        assertEquals(3, deployed.size)
        assertEquals(3, deployed.map { it.agent }.toSet().size)
        coVerify(exactly = 3) { eventBus.emit(any()) }
    }

    @Test
    fun spawnJury_withForeman_spawnsFour() = runTest {
        stubCommon()
        stubSubSessionCreation()
        every { agentRepo.find(any()) } answers { call ->
            AgentDefinition(name = call.invocation.args[0] as String, description = "d", prompt = "p")
        }
        every { agentRepo.listEnabled() } returns emptyList()

        val svc = buildService()
        val (deployed, _) = svc.spawnJury(context(), "改动了 app.kt", includeForeman = true)

        assertEquals(4, deployed.size)
        assertTrue(deployed.any { it.agent == "forehead" })
        coVerify(exactly = 4) { eventBus.emit(any()) }
    }

    // ── budget ────────────────────────────────────────────────

    @Test
    fun budget_aggregatesParentAndSubs() = runTest {
        activeIds.value = setOf("s1")
        coEvery { chatSessionDao.getById("root") } returns
            parentEntity().copy(totalInputTokens = 100, totalOutputTokens = 50)
        coEvery { chatSessionDao.getSubSessionsByParentOnce("root") } returns listOf(
            parentEntity().copy(id = "s1", title = "s1", totalInputTokens = 30, totalOutputTokens = 20),
            parentEntity().copy(id = "s2", title = "s2", totalInputTokens = 5, totalOutputTokens = 0)
        )
        val svc = buildService()
        val b = svc.budget(context())
        assertEquals(135, b["totalInput"]!!.let { (it as JsonPrimitive).content.toInt() })
        assertEquals(70, b["totalOutput"]!!.let { (it as JsonPrimitive).content.toInt() })
        assertEquals(2, b["subCount"]!!.let { (it as JsonPrimitive).content.toInt() })
        assertEquals(1, b["running"]!!.let { (it as JsonPrimitive).content.toInt() })
    }
}