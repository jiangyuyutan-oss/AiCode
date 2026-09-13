package com.aicode.feature.agent.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 会话内搜索的两个纯函数（[MessageSearchUtils]）：LIKE 转义（`\`、`%`、`_` 字面量匹配，
 * 配合 DAO 的 ESCAPE '\'）与命中片段截取（前后各 40 字符、上限 120、换行折叠、无命中取开头）。
 */
class MessageSearchHelpersTest {

    // ── escapeLike ───────────────────────────────────────────────────

    @Test
    fun escapeLike_escapesBackslash() {
        assertEquals("\\\\", MessageSearchUtils.escapeLike("\\"))
    }

    @Test
    fun escapeLike_escapesPercent() {
        assertEquals("\\%", MessageSearchUtils.escapeLike("%"))
    }

    @Test
    fun escapeLike_escapesUnderscore() {
        assertEquals("\\_", MessageSearchUtils.escapeLike("_"))
    }

    @Test
    fun escapeLike_combinesAllInOrder() {
        // 先转义反斜杠，再转义 % 与 _，避免二次转义
        assertEquals("a\\\\b\\%c\\_d", MessageSearchUtils.escapeLike("a\\b%c_d"))
    }

    @Test
    fun escapeLike_plainTextUnchanged() {
        assertEquals("hello 世界 123", MessageSearchUtils.escapeLike("hello 世界 123"))
    }

    // ── buildSearchSnippet ──────────────────────────────────────────

    @Test
    fun snippet_hitAtStart_noPrefixEllipsis() {
        val content = "目标在开头" + "x".repeat(100)
        val s = MessageSearchUtils.buildSearchSnippet(content, "目标")
        assertEquals(true, s.startsWith("目标"))
        assertEquals(false, s.startsWith("…"))
    }

    @Test
    fun snippet_hitInMiddle_wrapsBothSides() {
        val content = "a".repeat(100) + "命中" + "b".repeat(100)
        val s = MessageSearchUtils.buildSearchSnippet(content, "命中")
        assertEquals(true, s.startsWith("…"))
        assertEquals(true, s.endsWith("…"))
        assertEquals(true, s.contains("命中"))
    }

    @Test
    fun snippet_caseInsensitiveHit() {
        val s = MessageSearchUtils.buildSearchSnippet("say Hello world", "hello")
        assertEquals(true, s.contains("Hello"))
    }

    @Test
    fun snippet_noHit_takesHead() {
        val content = "z".repeat(200)
        val s = MessageSearchUtils.buildSearchSnippet(content, "不存在")
        assertEquals(120, s.length)
    }

    @Test
    fun snippet_newlinesFoldedToSpaces() {
        val s = MessageSearchUtils.buildSearchSnippet("第一行\n第二行 命中\n第三行", "命中")
        assertEquals(true, s.contains("命中"))
        assertEquals(false, s.contains("\n"))
    }
}
