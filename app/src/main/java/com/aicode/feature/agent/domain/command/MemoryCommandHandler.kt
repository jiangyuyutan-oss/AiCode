package com.aicode.feature.agent.domain.command

import javax.inject.Inject

/**
 * /memory —— 以 Markdown 表格气泡列出当前生效的项目规则（提示词分层注入内容），
 * 让用户知道 AI 正在遵守哪些规则文件。
 */
class MemoryCommandHandler @Inject constructor() : SlashCommandHandler {
    override val trigger = "/memory"
    override val label = "查看规则"
    override val description = "列出当前会话生效的项目规则与提示词文件"

    override fun execute(context: SlashCommandContext) {
        context.showMemoryOverview()
    }
}
