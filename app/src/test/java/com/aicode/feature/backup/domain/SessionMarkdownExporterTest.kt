package com.aicode.feature.backup.domain

import com.aicode.feature.agent.data.local.entity.AgentMessageEntity
import com.aicode.feature.agent.presentation.AgentAttachment
import com.aicode.feature.agent.presentation.BACKGROUND_NOTIFICATION_PREFIX
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMarkdownExporterTest {

    private fun message(
        id: String,
        role: String,
        content: String,
        toolName: String? = null,
        toolArgs: String? = null,
        isError: Boolean = false,
        isCompacted: Boolean = false,
        isContextSummary: Boolean = false,
        isCompactionMarker: Boolean = false,
        attachmentsJson: String? = null,
    ) = AgentMessageEntity(
        id = id,
        sessionId = "s",
        role = role,
        content = content,
        timestamp = 1_700_000_000_000L,
        toolName = toolName,
        toolArgs = toolArgs,
        isError = isError,
        isCompacted = isCompacted,
        isContextSummary = isContextSummary,
        isCompactionMarker = isCompactionMarker,
        attachmentsJson = attachmentsJson,
    )

    @Test
    fun header_containsTitleTimeAndCount() {
        val md = SessionMarkdownExporter.build("My Session", emptyList(), exportedAt = 1_700_000_000_000L)
        assertTrue(md.startsWith("# My Session\n"))
        assertTrue(md.contains("导出自 AiCode"))
        assertTrue(md.contains("2023-11-14"))
        assertTrue(md.contains("0 条消息"))
    }

    @Test
    fun userAndAssistant_renderedInOrder() {
        val md = SessionMarkdownExporter.build(
            "t",
            listOf(
                message("u", "USER", "fix the bug"),
                message("a", "ASSISTANT", "done"),
            )
        )
        assertTrue(md.contains("## 用户\n\nfix the bug"))
        assertTrue(md.contains("## AI\n\ndone"))
        assertTrue(md.indexOf("## 用户") < md.indexOf("## AI"))
    }

    @Test
    fun toolLine_containsNameErrorAndArgs() {
        val md = SessionMarkdownExporter.build(
            "t",
            listOf(
                message("a", "ASSISTANT", "let me run it"),
                message("x", "TOOL", "ok", toolName = "executeCommand", toolArgs = "{\n  \"cmd\": \"ls\"\n}", isError = true),
            )
        )
        assertTrue(md.contains("> 工具 `executeCommand` · 失败"))
        assertTrue(md.contains("> { \"cmd\": \"ls\" }"))
    }

    @Test
    fun attachment_markedAfterUserMessage() {
        val json = Json.encodeToString(listOf(AgentAttachment("pic.png", "", "", "image/png", 10, true)))
        val md = SessionMarkdownExporter.build(
            "t",
            listOf(
                message("u", "USER", "see image", attachmentsJson = json),
            )
        )
        assertTrue(md.contains("[附件: pic.png]"))
    }

    @Test
    fun internalMessages_filtered() {
        val md = SessionMarkdownExporter.build(
            "t",
            listOf(
                message("c", "USER", "compacted content", isCompacted = true),
                message("s", "USER", "summary", isContextSummary = true),
                message("m", "USER", "marker", isCompactionMarker = true),
                message("b", "USER", "$BACKGROUND_NOTIFICATION_PREFIX done"),
                message("a", "ASSISTANT", "visible"),
            )
        )
        assertFalse(md.contains("compacted"))
        assertFalse(md.contains("summary"))
        assertFalse(md.contains("marker"))
        assertFalse(md.contains(BACKGROUND_NOTIFICATION_PREFIX))
        assertTrue(md.contains("visible"))
    }

    @Test
    fun blankAssistantContent_skipped() {
        val md = SessionMarkdownExporter.build(
            "t",
            listOf(
                message("a", "ASSISTANT", "   "),
                message("x", "TOOL", "ok", toolName = "run"),
                message("u", "USER", "hello"),
            )
        )
        assertFalse(md.contains("## AI\n\n\n> 工具"))
        assertTrue(md.contains("> 工具 `run`"))
        assertTrue(md.contains("## 用户"))
    }

    @Test
    fun tokenTotals_inHeader() {
        val md = SessionMarkdownExporter.build(
            "t",
            listOf(
                AgentMessageEntity("a", "s", "ASSISTANT", "hi", 1L, inputTokens = 100, outputTokens = 50),
                AgentMessageEntity("b", "s", "ASSISTANT", "yo", 2L, inputTokens = 200, outputTokens = 25),
            )
        )
        assertTrue(md.contains("输入 300 tokens · 输出 75 tokens"))
    }
}
