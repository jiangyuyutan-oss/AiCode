package com.aicode.feature.agent.presentation

/** 会话内消息搜索的纯函数：LIKE 转义与命中片段截取。 */
internal object MessageSearchUtils {

    /** 搜索片段：命中词前后保留的上下文字符数。 */
    const val SNIPPET_CONTEXT_CHARS = 40

    /** 搜索片段总长上限。 */
    const val SNIPPET_MAX_CHARS = 120

    /** LIKE 转义：`\`、`%`、`_` 按字面量匹配（配合 DAO 查询的 ESCAPE '\'）。 */
    fun escapeLike(query: String): String =
        query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /** 截取命中词附近的上下文片段（前后各保留 [SNIPPET_CONTEXT_CHARS] 字符）。 */
    fun buildSearchSnippet(content: String, query: String): String {
        val clean = content.replace("\n", " ")
        val index = clean.indexOf(query, ignoreCase = true)
        if (index < 0) return clean.take(SNIPPET_MAX_CHARS)
        val start = (index - SNIPPET_CONTEXT_CHARS).coerceAtLeast(0)
        val end = (index + query.length + SNIPPET_CONTEXT_CHARS)
            .coerceAtMost(clean.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < clean.length) "…" else ""
        return (prefix + clean.substring(start, end) + suffix).take(SNIPPET_MAX_CHARS)
    }
}
