package com.aicode.feature.agent.domain.orchestration

import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.data.local.dao.ChatSessionDao
import com.aicode.feature.agent.domain.model.AgentContext
import com.aicode.feature.agent.domain.model.ReasoningEffort
import com.aicode.feature.agent.domain.session.SessionUseCase
import com.aicode.feature.agent.domain.subagent.AgentDefinition
import com.aicode.feature.agent.domain.subagent.AgentDefinitionRepository
import com.aicode.feature.agent.domain.subagent.SubAgentEvent
import com.aicode.feature.agent.domain.subagent.SubAgentEventBus
import com.aicode.feature.agent.domain.subagent.SubAgentEventType
import com.aicode.feature.settings.domain.repository.AIProviderRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * God Mode 编排器：CEO 经 `orchestrate` 工具调用的域层服务。
 *
 * - [deploy]：把需求拆成多个部门子会话派发（可指定 agent/模型/思考强度），复用现有子代理派发链路。
 * - [spawnJury]：组评审团（review-tough / review-pragmatic / review-optimist 三派 + 可选的评审长 forehead）。
 * - [budget]：聚合父会话 + 全部子会话的 cost，供额度治理。
 *
 * 子代理间不直连，沿用现有单向事件总线：完成经 [SubAgentEventBus] 通知父会话，CEO 用 `task(read)`
 * 拉结论、再以 `orchestrate` 派下一阶段。`deploy` 只负责「一次创建一批」，不做跨轮等待。
 */
@Singleton
class OrchestratorService @Inject constructor(
    private val sessionUseCase: SessionUseCase,
    private val chatSessionDao: ChatSessionDao,
    private val eventBus: SubAgentEventBus,
    private val agentDefinitionRepository: AgentDefinitionRepository,
    private val aiProviderRepository: AIProviderRepository
) {

    private companion object {
        const val TAG = "Orchestrator"
        /** 一次派发的最大部门数（对齐子代理并发上限）。 */
        const val MAX_BATCH_DEPARTMENTS = 5
    }

    data class DeploySpec(
        val name: String,
        val agent: String?,
        val model: String?,
        val reasoningEffort: String?,
        val prompt: String
    )

    data class Deployed(
        val id: String,
        val title: String,
        val agent: String?,
        val model: String?,
        val reasoningEffort: String?
    )

    /**
     * 把 `tasks` 参数（JsonArray）解析为 [DeploySpec] 列表。缺 prompt 的条目被丢弃。
     * 纯函数，脱离 Android 可单测。
     */
    fun parseDeploySpecs(raw: JsonElement?): List<DeploySpec> {
        val arr = raw as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val prompt = obj["prompt"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (prompt.isEmpty()) return@mapNotNull null
            DeploySpec(
                name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim()?.take(60)
                    ?.ifEmpty { "部门任务" } ?: "部门任务",
                agent = obj["agent"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() },
                model = obj["model"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() },
                reasoningEffort = obj["reasoningEffort"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() },
                prompt = prompt
            )
        }
    }

    /**
     * 派发一批部门子会话。返回 (成功派发列表, 错误列表)。
     * agent 名匹配不上或超并发上限的记录为错误并跳过，不阻塞其余派发。
     */
    suspend fun deploy(context: AgentContext, specs: List<DeploySpec>): Pair<List<Deployed>, List<String>> {
        val parentSessionId = context.sessionId
            ?: return emptyList<Deployed>() to listOf("缺少会话上下文，无法派发")
        val parentSession = sessionUseCase.getSessionById(parentSessionId)
            ?: return emptyList<Deployed>() to listOf("当前会话不存在")

        val errors = mutableListOf<String>()
        val deployed = mutableListOf<Deployed>()
        if (specs.isEmpty()) return emptyList<Deployed>() to listOf("没有可派发的子任务（tasks 为空或均缺 prompt）")

        val effective = specs.take(MAX_BATCH_DEPARTMENTS)
        if (specs.size > MAX_BATCH_DEPARTMENTS) {
            errors.add("一次最多派发 $MAX_BATCH_DEPARTMENTS 个部门，已忽略其后的（共 ${specs.size} 个任务）")
        }

        for (spec in effective) {
            if (eventBus.isFull) {
                errors.add("已到子代理并发上限（${SubAgentEventBus.MAX_RUNNING}），中止后续派发：${spec.name}")
                break
            }
            // 指定 agent 名时必须能找到定义：先精确匹配，找不到即报错跳过（不静默出门）。
            val definition = if (spec.agent == null) null else agentDefinitionRepository.find(spec.agent)
            if (spec.agent != null && definition == null) {
                val available = agentDefinitionRepository.listEnabled().map { it.definition.name }
                errors.add("没有名为「${spec.agent}」的部门/评审定义，跳过「${spec.name}」（可用：${available.joinToString(", ").ifBlank { "（无）" }}）")
                continue
            }

            val model = spec.model ?: definition?.model
            val rawEffort = spec.reasoningEffort ?: definition?.reasoningEffort
            val effortName = rawEffort?.let { e ->
                ReasoningEffort.entries.firstOrNull { it.apiValue == e }?.name
            }
            val providerId = definition?.providerId?.let { resolveProviderId(it) }

            val sub = sessionUseCase.newSubSessionEntity(
                title = spec.name,
                parentId = parentSessionId,
                parent = parentSession,
                subagentType = definition?.name ?: "dept",
                providerId = providerId,
                model = model,
                reasoningEffort = effortName
            )
            sessionUseCase.upsertSession(sub)
            eventBus.emit(
                SubAgentEvent(
                    subSessionId = sub.id,
                    parentSessionId = parentSessionId,
                    type = SubAgentEventType.SPAWNED,
                    detail = spec.prompt
                )
            )
            deployed += Deployed(sub.id, spec.name, definition?.name, model, effortName)
            FileLogger.i(TAG, "God 派发部门: name=${spec.name} session=${sub.id} agent=${definition?.name ?: "-"}")
        }
        return deployed to errors
    }

    /**
     * 组评审团：派 3 派评审员（严苛/务实/建设）只读审查 [diffSpec]。
     * [includeForeman] 为 true 时再追加一名评审长（forehead）汇总裁决。
     * 返回派发的部署结果；即 CEO 可先派 3 派评审、收齐意见后再带意见全文二次 orchestrate(review) 派评审长。
     */
    suspend fun spawnJury(
        context: AgentContext,
        diffSpec: String,
        includeForeman: Boolean
    ): Pair<List<Deployed>, List<String>> {
        if (diffSpec.isBlank()) return emptyList<Deployed>() to listOf("评审需要提供 diffSpec（改动范围）")
        val specs = mutableListOf(
            DeploySpec("评审·严苛", "review-tough", null, null, "请对以下改动做严苛评审（优先找必炸问题与硬约束违规），只读不改：\n$diffSpec"),
            DeploySpec("评审·务实", "review-pragmatic", null, null, "请从可维护性/测试/性价比对以下改动做务实评审，只读不改：\n$diffSpec"),
            DeploySpec("评审·建设", "review-optimist", null, null, "请从建设性视角（亮点+风险+值得跟进，勿放过硬错）评审以下改动，只读不改：\n$diffSpec")
        )
        if (includeForeman) {
            specs += DeploySpec("评审·裁决", "forehead", null, null, "请基于评审团意见（见指令）给出结构化裁决，只读不改。\n审查范围：$diffSpec")
        }
        return deploy(context, specs)
    }

    /** 汇总父会话 + 全部子会话的 cost，产出额度视图（JSON 便于工具回填）。 */
    suspend fun budget(context: AgentContext): JsonObject {
        val rootId = context.sessionId
        val subs = if (rootId != null) chatSessionDao.getSubSessionsByParentOnce(rootId) else emptyList()
        val parent = if (rootId != null)
            runCatching { chatSessionDao.getById(rootId) }.getOrNull()
        else null
        val parentIn = parent?.totalInputTokens ?: 0
        val parentOut = parent?.totalOutputTokens ?: 0

        val totalIn = parentIn + subs.sumOf { it.totalInputTokens }
        val totalOut = parentOut + subs.sumOf { it.totalOutputTokens }
        val depts = JsonArray(subs.map { s ->
            JsonObject(mapOf(
                "id" to JsonPrimitive(s.id),
                "title" to JsonPrimitive(s.title),
                "input" to JsonPrimitive(s.totalInputTokens),
                "output" to JsonPrimitive(s.totalOutputTokens)
            ))
        })
        return JsonObject(mapOf(
            "parentInput" to JsonPrimitive(parentIn),
            "parentOutput" to JsonPrimitive(parentOut),
            "totalInput" to JsonPrimitive(totalIn),
            "totalOutput" to JsonPrimitive(totalOut),
            "totalTokens" to JsonPrimitive(totalIn + totalOut),
            "subCount" to JsonPrimitive(subs.size),
            "running" to JsonPrimitive(eventBus.activeSubSessionIds.value.size),
            "maxConcurrent" to JsonPrimitive(SubAgentEventBus.MAX_RUNNING),
            "depts" to depts
        ))
    }

    /** 列出主会话的全部子会话及在线状态。 */
    suspend fun listSubSessions(parentId: String): List<SubSessionInfo> {
        val subs = chatSessionDao.getSubSessionsByParentOnce(parentId)
        val active = eventBus.activeSubSessionIds.value
        return subs.map { s ->
            SubSessionInfo(s.id, s.title, s.subagentType ?: "dept", if (s.id in active) SubSessionInfo.State.RUNNING else SubSessionInfo.State.COMPLETED)
        }
    }

    private suspend fun resolveProviderId(raw: String): String? {
        aiProviderRepository.getProviderById(raw)?.let { return it.id }
        val all = aiProviderRepository.getAllProviders().first()
        return all.firstOrNull { it.name.equals(raw, ignoreCase = true) }?.id
    }
}

/** God 编排中一个子会话的简要视图（用于 status）。 */
data class SubSessionInfo(
    val id: String,
    val title: String,
    val type: String,
    val state: State
) {
    enum class State { RUNNING, COMPLETED }
}