package com.aicode.feature.agent.domain.tool.semantic

/**
 * 语义索引的分词器：把代码 / 自然语言文本切成可检索的词元。
 *
 * 策略（纯函数，JVM 可测）：
 * - 以非字母数字字符切分（去空白、符号）；
 * - 下划线 / 中划线 / 点 / 大小写分界再拆 —— `userLoginHandler` → `user`,`login`,`handler`，
 *   `auth_token` → `auth`,`token`；
 * - 全小写归一；
 * - 单个字母/数字也保留（代码里 `i`/`e` 常见，双消歧时有用）；
 * - 连续 CJK 段额外产二元组，让中文注释 / 中文问句可召回（`鉴权`→`鉴`,`权`；也保留整段 `鉴权`）。
 *
 * 该实现刻意轻量：不引入分词库、零网络、纯本地，适合移动端离线索引。
 */
object SemanticTokenizer {

    private val NON_WORD = Regex("[^A-Za-z0-9\\u4e00-\\u9fff]+")
    private val IDENTIFIER_BOUNDARY = Regex("([a-z])([A-Z])|([A-Za-z])([0-9])|([0-9])([A-Za-z])|([A-Za-z0-9])([_-])|([_-])([A-Za-z0-9])")
    private val CJK_RUN = Regex("[\\u4e00-\\u9fff]+")

    fun tokenize(text: String): List<String> {
        val out = mutableListOf<String>()
        for (raw in NON_WORD.split(text)) {
            if (raw.isEmpty()) continue
            appendWord(raw, out)
        }
        return out
    }

    private fun appendWord(raw: String, out: MutableList<String>) {
        // 纯 CJK 段：保留整段 + 二元组 + 单字。
        if (raw.all { it.code in 0x4e00..0x9fff }) {
            if (raw.length == 1) {
                out.add(raw)
                return
            }
            out.add(raw)
            for (i in 0 until raw.length - 1) out.add(raw.substring(i, i + 2))
            // 中文分词很难，这里不再拆单字（避免噪声过大）；整段 + 二元组已能区分大多数意图。
            return
        }

        // 含 ASCII 的混合段：按下划线/中划线先粗切，再按驼峰 / 数字边界细拆。
        for (chunk in raw.split('_', '-')) {
            if (chunk.isEmpty()) continue
            val parts = splitCamel(chunk)
            for (p in parts) {
                if (p.isEmpty()) continue
                out.add(p.lowercase())
            }
        }
    }

    private fun splitCamel(word: String): List<String> {
        if (word.length <= 1) return listOf(word)
        val result = mutableListOf<String>()
        var start = 0
        var i = 1
        while (i < word.length) {
            val cur = word[i]
            val prev = word[i - 1]
            val boundary = (cur.isUpperCase() && prev.isLowerCase()) ||
                (cur.isLetter() != prev.isLetter() && (prev.isDigit() || cur.isDigit())) ||
                (cur.isDigit() && !prev.isDigit())
            if (boundary && i != start) {
                result.add(word.substring(start, i))
                start = i
            }
            i++
        }
        result.add(word.substring(start))
        return result
    }
}