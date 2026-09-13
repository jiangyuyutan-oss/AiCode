package com.aicode.feature.agent.domain.command

import javax.inject.Inject

/**
 * /setup-opencode —— 让 AI 自动完成 opencode CLI 的检测安装与模型配置迁移：
 * App 先把当前生效 provider 导出到容器内 ~/.aicode/opencode-provider.json，
 * 再把 setup-opencode 技能正文作为请求发给 AI，由其在容器内执行安装与配置。
 */
class SetupOpencodeCommandHandler @Inject constructor() : SlashCommandHandler {
    override val trigger = "/setup-opencode"
    override val label = "配置 OpenCode"
    override val description = "检测/安装 opencode CLI 并把当前模型配置迁移为 opencode 配置"

    override fun execute(context: SlashCommandContext) {
        context.runSetupOpencode()
    }
}
