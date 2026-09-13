package com.aicode.feature.agent.domain.command

import javax.inject.Inject

/**
 * /mcp —— 以 Markdown 表格气泡列出 MCP 服务器连接状态、工具数与错误信息。
 */
class McpCommandHandler @Inject constructor() : SlashCommandHandler {
    override val trigger = "/mcp"
    override val label = "查看 MCP"
    override val description = "列出 MCP 服务器连接状态与已注册的工具数"

    override fun execute(context: SlashCommandContext) {
        context.showMcpOverview()
    }
}
