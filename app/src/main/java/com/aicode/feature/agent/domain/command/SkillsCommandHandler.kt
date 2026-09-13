package com.aicode.feature.agent.domain.command

import javax.inject.Inject

/**
 * /skills —— 以 Markdown 表格气泡列出已安装技能与内置子代理，
 * 含启用状态，便于用户了解可用的技能面。
 */
class SkillsCommandHandler @Inject constructor() : SlashCommandHandler {
    override val trigger = "/skills"
    override val label = "查看技能"
    override val description = "列出已安装的技能与内置子代理及其启用状态"

    override fun execute(context: SlashCommandContext) {
        context.showSkillsOverview()
    }
}
