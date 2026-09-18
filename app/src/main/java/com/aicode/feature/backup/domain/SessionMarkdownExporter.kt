package com.aicode.feature.backup.domain

import com.aicode.feature.agent.data.local.entity.AgentMessageEntity
import com.aicode.feature.agent.presentation.BACKGROUND_NOTIFICATION_PREFIX
import com.aicode.feature.agent.presentation.MessageRole
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 工具参数在 Markdown 中的截断上限，避免超长命令/输出撑爆文档。 */
internal const val EXPORT_TOOL_ARGS_MAX_CHARS = 300

/**
 * 单会话 Markdown 导出器：把会话消息渲染为人类可读的对话文档。
 *
 * 纯函数，便于 JVM 单测；与 [com.aicode.feature.backup.data.BackupManagerImpl] 的
 * tar.gz 备份导出并列——本格式面向阅读与分享，备份格式面向导入恢复。
 *
 * 排除项：已压缩消息、上下文摘要、压缩锚点、后台任务通知、AI 思考过程（reasoning）、
 * 正文为空的 AI 消息（纯工具调用轮次由工具记录说明）。
 */
object SessionMarkdownExporter {

    private val timeFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

    fun build(
        title: String,
        messages: List<AgentMessageEntity>,
        exportedAt: Long = System.currentTimeMillis(),
    ): String {
        val visible = messages.filter { isExportable(it) }
        return buildString {
            appendLine("# $title")
            appendLine()
            append("> 导出自 AiCode · ${timeFormat.format(Instant.ofEpochMilli(exportedAt))}")
            append(" · ${visible.size} 条消息")
            val inputTokens = visible.sumOf { it.inputTokens }
            val outputTokens = visible.sumOf { it.outputTokens }
            if (inputTokens > 0 || outputTokens > 0) {
                append(" · 输入 $inputTokens tokens · 输出 $outputTokens tokens")
            }
            appendLine()
            visible.forEach { message ->
                appendLine()
                appendMessage(message)
            }
        }
    }

    private fun isExportable(message: AgentMessageEntity): Boolean = when {
        message.isCompacted || message.isContextSummary || message.isCompactionMarker -> false
        message.role == MessageRole.USER.name &&
            message.content.startsWith(BACKGROUND_NOTIFICATION_PREFIX) -> false
        else -> true
    }

    private fun StringBuilder.appendMessage(message: AgentMessageEntity) {
        when (MessageRole.valueOf(message.role)) {
            MessageRole.USER -> {
                appendLine("## 用户")
                appendLine()
                appendLine(message.content.trim())
                message.exportedAttachments().forEach { attachment ->
                    appendLine()
                    appendLine("[附件: ${attachment.fileName.ifBlank { "file" }}]")
                }
            }

            MessageRole.ASSISTANT -> {
                if (message.content.isBlank()) return
                appendLine("## AI")
                appendLine()
                appendLine(message.content.trim())
            }

            MessageRole.TOOL -> appendTool(message)
        }
    }

    private fun StringBuilder.appendTool(message: AgentMessageEntity) {
        appendLine("> 工具 `${message.toolName ?: "unknown"}`${if (message.isError) " · 失败" else ""}")
        val args = message.toolArgs?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        if (args.isNotEmpty()) {
            appendLine(">")
            appendLine("> ${args.take(EXPORT_TOOL_ARGS_MAX_CHARS)}")
        }
    }
}
