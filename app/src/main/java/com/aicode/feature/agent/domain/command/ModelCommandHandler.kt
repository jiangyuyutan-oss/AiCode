package com.aicode.feature.agent.domain.command

import javax.inject.Inject

/**
 * /model —— 打开模型选择弹窗（等同点输入栏的模型按钮）。
 */
class ModelCommandHandler @Inject constructor() : SlashCommandHandler {
    override val trigger = "/model"
    override val label = "切换模型"
    override val description = "打开模型选择弹窗，切换当前会话使用的模型"

    override fun execute(context: SlashCommandContext) {
        context.requestModelSheet()
    }
}
