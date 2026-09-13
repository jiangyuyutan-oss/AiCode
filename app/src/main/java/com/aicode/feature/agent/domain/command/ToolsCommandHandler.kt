package com.aicode.feature.agent.domain.command

import javax.inject.Inject

/**
 * /tools —— 以 Markdown 表格气泡列出当前注册可用的工具清单，
 * 含各工具的权限策略（ALWAYS/ASK/DENY）。
 */
class ToolsCommandHandler @Inject constructor() : SlashCommandHandler {
    override val trigger = "/tools"
    override val label = "查看工具"
    override val description = "列出当前注册可用的工具及权限策略"

    override fun execute(context: SlashCommandContext) {
        context.showToolsOverview()
    }
}
