package com.aicode.feature.agent.domain.tool.semantic

import kotlin.math.ln

/**
 * 进程内存态倒排索引 + TF-IDF 相关性打分。
 *
 * 纯数据结构（JVM 可测、无 I/O）：
 * - `addDocument(docId, contentTerms, pathTerms)`：倒排写入内容词元；[pathTerms]（文件名/路径
 *   片段词元）额外再计一次，等效权重更高 —— 命中文件名的文档靠前，利于“猜文件”。
 * - `score(queryTerms, k)`：TF 归一（1+ln(tf)）× IDF（ln((N+1)/(df+1))+1）逐词累积取 Top-K；
 *   与 query 无交集的文档不返回。
 * - 打分稳定：纯加法、无随机，同输入同输出。
 */
class InvertedIndex {

    // term -> (docId -> tf)
    private val termDocs = HashMap<String, HashMap<String, Int>>()
    private val docIds = LinkedHashSet<String>()

    /** 已索引文档数。 */
    val size: Int get() = docIds.size

    fun addDocument(docId: String, contentTerms: List<String>, pathTerms: List<String> = emptyList()) {
        require(docIds.add(docId)) { "duplicate docId: $docId" }
        for (term in contentTerms) bump(docId, term, 1)
        for (term in pathTerms) bump(docId, term, 1)
    }

    private fun bump(docId: String, term: String, delta: Int) {
        val bucket = termDocs.getOrPut(term) { HashMap() }
        bucket[docId] = (bucket[docId] ?: 0) + delta
    }

    /** 按 [queryTerms] 相关度返回 Top-K（分数降序）；空 query 或索引空返回空列表。 */
    fun score(queryTerms: List<String>, k: Int): List<ScoredDoc> {
        if (queryTerms.isEmpty() || docIds.isEmpty()) return emptyList()
        val n = docIds.size
        val scores = HashMap<String, Double>()
        for (term in queryTerms) {
            val bucket = termDocs[term] ?: continue
            val df = bucket.size
            if (df == 0) continue
            val idf = ln((n + 1.0) / (df + 1.0)) + 1.0
            for ((docId, tf) in bucket) {
                val w = 1.0 + ln(tf.toDouble())
                scores[docId] = (scores[docId] ?: 0.0) + w * idf
            }
        }
        return scores.entries
            .sortedByDescending { it.value }
            .take(k)
            .map { ScoredDoc(it.key, it.value) }
    }

    data class ScoredDoc(val docId: String, val score: Double)
}