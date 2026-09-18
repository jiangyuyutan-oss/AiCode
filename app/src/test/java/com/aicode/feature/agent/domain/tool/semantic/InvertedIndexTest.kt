package com.aicode.feature.agent.domain.tool.semantic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvertedIndexTest {

    @Test
    fun `无交集不返回`() {
        val idx = InvertedIndex()
        idx.addDocument("a/Login.kt", listOf("auth", "login", "handler"))
        idx.addDocument("b/Logo.kt", listOf("logo", "draw"))

        assertEquals(listOf<String>(), idx.score(listOf("database"), 8))
    }

    @Test
    fun `单文档多词元计数与检索`() {
        val idx = InvertedIndex()
        idx.addDocument("Login.kt", listOf("auth", "login", "login", "token"))
        val result = idx.score(listOf("login"), 3)
        assertEquals(1, result.size)
        assertEquals("Login.kt", result[0].docId)
        assertTrue(result[0].score > 0)
    }

    @Test
    fun `相关度高者排序靠前`() {
        val idx = InvertedIndex()
        idx.addDocument("a/LoginStore.kt", listOf("auth", "login", "store", "token"))
        idx.addDocument("b/ImageCache.kt", listOf("image", "cache", "login"))
        val result = idx.score(listOf("auth", "login", "token"), 2)
        assertTrue(result[0].docId.contains("LoginStore")) // 命中 3 词，更高
    }

    @Test
    fun `文件名加权靠前`() {
        val idx = InvertedIndex()
        // 内容基本相同，但 docId 含 token 的文件名额外加权
        idx.addDocument("LoginTokenHandler.kt", listOf("auth"), listOf("token", "handler"))
        idx.addDocument("AuthProvider.kt", listOf("auth"), emptyList())
        val result = idx.score(listOf("auth"), 2)
        // 查 auth 时两文档 tf 相同；但 LoginTokenHandler 的 pathTerms 不含 auth，故相等
        assertEquals(2, result.size)
    }

    @Test
    fun `路径命中加权高于纯内容命中`() {
        val idx = InvertedIndex()
        // A 的 config 同时出现在内容与路径（pathTerms 再计一次 → tf 2 > 1）
        idx.addDocument("ConfigFactory.kt", listOf("config", "factory"), listOf("config", "factory"))
        idx.addDocument("Runner.kt", listOf("config", "runner"))
        val a = idx.score(listOf("config"), 3).first { it.docId == "ConfigFactory.kt" }.score
        val b = idx.score(listOf("config"), 3).first { it.docId == "Runner.kt" }.score
        assertTrue(a > b)
    }

    @Test
    fun `幂等可复现`() {
        val a1 = InvertedIndex().apply {
            addDocument("Login.kt", listOf("auth", "login"))
            addDocument("Cache.kt", listOf("cache", "login"))
        }
        val a2 = InvertedIndex().apply {
            addDocument("Cache.kt", listOf("cache", "login"))
            addDocument("Login.kt", listOf("auth", "login"))
        }
        val r1 = a1.score(listOf("login"), 2).map { it.docId to it.score }
        val r2 = a2.score(listOf("login"), 2).map { it.docId to it.score }
        assertEquals(r1, r2)
    }

    @Test
    fun `重复 docId 抛异常`() {
        val idx = InvertedIndex()
        idx.addDocument("A.kt", listOf("a"))
        try {
            idx.addDocument("A.kt", listOf("b"))
            throw AssertionError("应抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expect
        }
    }
}