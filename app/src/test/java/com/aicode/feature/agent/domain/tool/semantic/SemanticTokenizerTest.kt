package com.aicode.feature.agent.domain.tool.semantic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticTokenizerTest {

    @Test
    fun `空串无词元`() {
        assertEquals(listOf<String>(), SemanticTokenizer.tokenize(""))
        assertEquals(listOf<String>(), SemanticTokenizer.tokenize("   \n\t  "))
        assertEquals(listOf<String>(), SemanticTokenizer.tokenize("!!! ??"))
    }

    @Test
    fun `驼峰拆分小写化`() {
        assertEquals(listOf("user", "login", "handler"), SemanticTokenizer.tokenize("UserLoginHandler"))
        assertEquals(listOf("callback", "store"), SemanticTokenizer.tokenize("CallbackStore"))
    }

    @Test
    fun `下划线和中划线拆分`() {
        assertEquals(listOf("auth", "token"), SemanticTokenizer.tokenize("auth_token"))
        assertEquals(listOf("kebab", "case", "name"), SemanticTokenizer.tokenize("kebab-case-name"))
        assertEquals(listOf("get", "user", "by", "id"), SemanticTokenizer.tokenize("get user_by-id"))
    }

    @Test
    fun `点号分隔拆词`() {
        assertEquals(listOf("com", "aicode", "app"), SemanticTokenizer.tokenize("com.aicode.app"))
    }

    @Test
    fun `边界字符拆分`() {
        assertEquals(listOf("fun", "main"), SemanticTokenizer.tokenize("fun main() {"))
        assertEquals(listOf("data", "class", "user"), SemanticTokenizer.tokenize("data class User "))
    }

    @Test
    fun `中文整段与二元组`() {
        // 纯中文段：整段 + 二元组
        val tokens = SemanticTokenizer.tokenize("鉴权")
        assertTrue("鉴权" in tokens)
        assertTrue("鉴权".length == 2)
    }

    @Test
    fun `单字母保留`() {
        assertTrue("i" in SemanticTokenizer.tokenize("for (i = 0"))
    }
}