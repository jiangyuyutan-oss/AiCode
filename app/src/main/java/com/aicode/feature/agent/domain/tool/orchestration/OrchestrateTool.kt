package com.aicode.feature.agent.domain.tool.orchestration

import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.model.AgentContext
import com.aicode.feature.agent.domain.orchestration.OrchestratorService
import com.aicode.feature.agent.domain.tool.AbstractContextualTool
import com.aicode.feature.agent.domain.tool.ParameterType
import com.aicode.feature.agent.domain.tool.ToolCapability
import com.aicode.feature.agent.domain.tool.ToolParameter
import com.aicode.feature.agent.domain.tool.ToolPermissionPolicy
import com.aicode.feature.agent.domain.tool.ToolResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * God Mode 编排工具 `orchestrate`（仅 GOD 模式下对 CEO 可见，普通会话不注入）。
 *
 * - `phase=decompose`（默认）：把需求拆成多个部门子任务，一次派发（可指定 agent/模型/思考强度）。
 * - `phase=review`：派 3 派评审员（可选加评审长）对某改动范围只读评审。
 * - `phase=status`：列出全部子部门/评审会话状态。
 * - `phase=budget`：汇总本场协作的额度消耗。
 *
 * 取消一致性：工具本身不阻塞等待子代理完成；CEO 收完成通知后 `task(read)` 拉结论，再据此编排下一阶段。
 * 只允许在 GOD 模式、且仅对主会话（无 agentDefinition 绑定的根会话）使用。
 */
class OrchestrateTool @Inject constructor(
    private val orchestrator: OrchestratorService
) : AbstractContextualTool() {

    private companion object {
        const val TAG = "Orchestrate"
    }

    override val name = "orchestrate"
    override val permissionPolicy = ToolPermissionPolicy.ASK
    override val capabilities = setOf(ToolCapability.MODIFY_SESSION_STATE)

    override fun effectiveCapabilities(args: Map<String, JsonElement>): Set<ToolCapability> {
        val phase = args["phase"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: "decompose"
        return when (phase) {
            "status", "budget" -> emptySet()
            else -> setOf(ToolCapability.MODIFY_SESSION_STATE)
        }
    }

    override val description = "God Mode 编排（乙方公司运营）：把需求拆成多个部门子任务派发（decompose）、组评审团审查改动（review）、查看各子会话状态（status）、汇总本场额度消耗（budget）。部门/评审可指定不同 agent 名（确定其专属提示词/模型/工具集）。仅 GOD 模式下可用。"

    override val parameters: Map<String, ToolParameter> = mapOf(
        "phase" to ToolParameter(
            name = "phase",
            type = ParameterType.STRING,
            description = "操作类型：decompose（默认，拆分任务派发部门）/ review（组评审团审查改动）/ status（列出子会话状态）/ budget（汇总额度）",
            required = false,
            enum = listOf("decompose", "review", "status", "budget")
        ),
        "goal" to ToolParameter(
            name = "goal",
            type = ParameterType.STRING,
            description = "decompose 时的一段需求/目标描述，供派发部门理解全局上下文",
            required = false
        ),
        "tasks" to ToolParameter(
            name = "tasks",
            type = ParameterType.ARRAY,
            description = "decompose 时拆出的子任务数组，每项 {name, agent?, model?, reasoningEffort?, prompt}。prompt 非必需会丢弃该项。",
            required = false
        ),
        "diffSpec" to ToolParameter(
            name = "diffSpec",
            type = ParameterType.STRING,
            description = "review 时的改动范围（文件清单 / 未提交 diff 摘要），喂给评审团",
            required = false
        ),
        "foreman" to ToolParameter(
            name = "foreman",
            type = ParameterType.STRING,
            description = "review 时是否同时派评审长汇总（true/false，默认 false：先派 3 名评审，CEO 收齐意见后再带全文派评审长）",
            required = false
        )
    )

    override suspend fun executeWithContext(args: Map<String, JsonElement>, context: AgentContext): ToolResult {
        return try {
            if (context.mode != com.aicode.feature.agent.domain.model.AgentMode.GOD) {
                return ToolResult.Error("orchestrate 仅在 God Mode 下可用。请先切换为 GOD 模式。", "NOT_GOD_MODE")
            }
            val phase = args["phase"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase() ?: "decompose"
            when (phase) {
                "decompose" -> decompose(args, context)
                "review" -> review(args, context)
                "status" -> status(context)
                "budget" -> budget(context)
                else -> ToolResult.Error("未知 phase: $phase，支持 decompose / review / status / budget", "INVALID_PHASE")
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "orchestrate 异常", e)
            ToolResult.Error(e.message ?: "编排失败", "ORCHESTRATE_ERROR")
        }
    }

    private suspend fun decompose(args: Map<String, JsonElement>, context: AgentContext): ToolResult {
        val goal = args["goal"]?.jsonPrimitive?.contentOrNull?.trim()
        val tasks = args["tasks"]?.let { orchestrator.parseDeploySpecs(it) } ?: emptyList()
        if (tasks.isEmpty()) {
            return ToolResult.Error("需要提供 tasks（子任务数组，含每项 prompt）", "MISSING_TASKS")
        }
        // goal 若给了但 tasks 没显含，把它追加到首个任务或整体说明——不重新包装，直接在结果说明。
        if (goal != null) {
            FileLogger.i(TAG, "decompose goal=$goal tasks=${tasks.size}")
        }
        val (deployed, errors) = orchestrator.deploy(context, tasks)
        return ToolResult.Success(
            buildJsonObject {
                put("goal", goal ?: "")
                put("deployed", buildJsonArray {
                    deployed.forEach { d ->
                        add(buildJsonObject {
                            put("id", d.id)
                            put("title", d.title)
                            put("agent", d.agent ?: "")
                            put("model", d.model ?: "")
                            put("reasoningEffort", d.reasoningEffort ?: "")
                            put("state", "running")
                        })
                    }
                })
                put("deployedCount", deployed.size)
                put("errors", buildJsonArray { errors.forEach { add(JsonPrimitive(it)) } })
                put("message", if (deployed.isEmpty()) "未派发任何部门（见 errors）" else "已派发 ${deployed.size} 个部门，完成后会通知。可用 task(action=\"read\", id=...) 读结果。")
            }
        )
    }

    private suspend fun review(args: Map<String, JsonElement>, context: AgentContext): ToolResult {
        val diffSpec = args["diffSpec"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
        if (diffSpec.isEmpty()) {
            return ToolResult.Error("review 需要提供 diffSpec（改动范围）", "MISSING_DIFF")
        }
        val includeForeman = args["foreman"]?.jsonPrimitive?.contentOrNull
            ?.let { it.equals("true", ignoreCase = true) } == true
        val (deployed, errors) = orchestrator.spawnJury(context, diffSpec, includeForeman)
        return ToolResult.Success(
            buildJsonObject {
                put("review", buildJsonArray {
                    deployed.forEach { d ->
                        add(buildJsonObject {
                            put("id", d.id)
                            put("title", d.title)
                            put("agent", d.agent ?: "")
                            put("state", "running")
                        })
                    }
                })
                put("count", deployed.size)
                put("errors", buildJsonArray { errors.forEach { add(JsonPrimitive(it)) } })
                put("message", if (deployed.isEmpty()) "评审团未成立（见 errors）" else "已成立评审团（${deployed.size} 名），完成后会通知。CEO 可收齐意见后用 orchestrate(review, foreman=true) 派评审长裁决。")
            }
        )
    }

    private suspend fun status(context: AgentContext): ToolResult {
        val subs = context.sessionId?.let {
            runCatching { orchestrator.listSubSessions(it) }.getOrElse { emptyList() }
        } ?: emptyList()
        return ToolResult.Success(
            buildJsonObject {
                put("subagents", buildJsonArray {
                    subs.forEach { s ->
                        add(buildJsonObject {
                            put("id", s.id)
                            put("title", s.title)
                            put("state", s.state.name)
                        })
                    }
                })
                put("count", subs.size)
            }
        )
    }

    private suspend fun budget(context: AgentContext): ToolResult {
        val b = orchestrator.budget(context)
        return ToolResult.Success(b)
    }
}