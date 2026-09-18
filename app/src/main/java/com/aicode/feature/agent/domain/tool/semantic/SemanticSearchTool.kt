package com.aicode.feature.agent.domain.tool.semantic

import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.tool.AgentTool
import com.aicode.feature.agent.domain.tool.ParameterType
import com.aicode.feature.agent.domain.tool.ToolCapability
import com.aicode.feature.agent.domain.tool.ToolParameter
import com.aicode.feature.agent.domain.tool.ToolPermissionPolicy
import com.aicode.feature.agent.domain.tool.ToolResult
import com.aicode.feature.workspace.domain.FileAccessProvider
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.round
import javax.inject.Inject

/**
 * 本地关键词（TF-IDF）语义检索工具。
 *
 * 用自然语言召回工作区内相关代码文件，容忍词面差异，区别于 `search`（rg 精确匹配）与
 * `list`（目录枚举）。纯本地：不调网络、不用远端 embedding，离线可用。远程工作区经
 * [FileAccessProvider] 的 SFTP 后端读取。
 */
class SemanticSearchTool @Inject constructor(
    private val indexer: SemanticIndexer,
    private val fileAccess: FileAccessProvider
) : AgentTool() {

    private companion object {
        const val TAG = "SemanticSearch"
        const val DEFAULT_TOP_K = 8
        const val SNIPPET_LINES = 6
        const val SNIPPET_MAX_CHARS = 240
    }

    override val name = "semantic_search"
    override val description = "按自然语言（如“…鉴权在哪实现”“加载配置的工具”）召回工作区内最相关的代码文件，按相关度降序返回路径与命中片段。"
    override val permissionPolicy = ToolPermissionPolicy.AUTO_APPROVE
    override val capabilities = setOf(ToolCapability.READ_WORKSPACE)

    override val parameters = mapOf(
        "query" to ToolParameter(
            name = "query",
            type = ParameterType.STRING,
            description = "自然语言检索问句或关键词，表达想要找的代码 / 配置意图。",
            required = true
        )
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        return try {
            val query = args["query"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (query.isEmpty()) return ToolResult.Error("缺少检索 query", "MISSING_QUERY")

            val queryTerms = SemanticTokenizer.tokenize(query)
            if (queryTerms.isEmpty()) return ToolResult.Error("query 无可检索词元", "EMPTY_QUERY")

            val startedAt = System.currentTimeMillis()
            val readable = try {
                indexer.ensureIndex()
            } catch (e: SemanticIndexUnavailableException) {
                FileLogger.w(TAG, "语义索引不可用: ${e.message}")
                return ToolResult.Error("语义索引暂不可用: ${e.message}", "INDEX_UNAVAILABLE")
            }

            val ranked = readable.index.score(queryTerms, DEFAULT_TOP_K)
            val rankedWithSnippets = ranked.map { doc ->
                JsonObject(mapOf(
                    "path" to JsonPrimitive(doc.docId),
                    "score" to JsonPrimitive(round(doc.score * 1000.0) / 1000.0),
                    "snippet" to JsonPrimitive(readSnippet(doc.docId))
                ))
            }

            val content = if (ranked.isEmpty()) {
                "未找到与“$query”相关的结果"
            } else {
                resultsText(ranked, readable.indexedFiles)
            }

            ToolResult.Success(JsonObject(mapOf(
                "content" to JsonPrimitive(content),
                "results" to JsonArray(rankedWithSnippets),
                "top_k" to JsonPrimitive(ranked.size),
                "indexed_files" to JsonPrimitive(readable.indexedFiles),
                "truncated" to JsonPrimitive(readable.truncated),
                "elapsed_ms" to JsonPrimitive(System.currentTimeMillis() - startedAt),
                "backend" to JsonPrimitive("local-tfidf")
            )))
        } catch (e: Exception) {
            FileLogger.e(TAG, "semantic_search 异常", e)
            ToolResult.Error(e.message ?: "语义检索失败", "SEARCH_ERROR")
        }
    }

    private fun resultsText(ranked: List<InvertedIndex.ScoredDoc>, indexedFiles: Int): String {
        val sb = StringBuilder("在 $indexedFiles 个文件中召回 ${ranked.size} 个最相关候选：\n")
        ranked.forEachIndexed { i, doc ->
            val line = "${i + 1}. ${doc.docId}  (score ${round(doc.score * 1000.0) / 1000.0})"
            sb.append(line)
            if (i != ranked.lastIndex) sb.append('\n')
        }
        return sb.toString()
    }

    private fun readSnippet(displayPath: String): String {
        return try {
            val head = fileAccess.readFile(displayPath)
                .lineSequence()
                .take(SNIPPET_LINES)
                .joinToString("\n")
            if (head.length > SNIPPET_MAX_CHARS) head.take(SNIPPET_MAX_CHARS) + "…" else head
        } catch (e: Exception) {
            ""
        }
    }
}