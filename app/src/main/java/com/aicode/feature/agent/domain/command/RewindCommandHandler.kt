package com.aicode.feature.agent.domain.command

import javax.inject.Inject

/**
 * /rewind —— 打开当前会话的回退（检查点）选择菜单，
 * 把对话与代码回滚到之前某个检查点。
 */
class RewindCommandHandler @Inject constructor() : SlashCommandHandler {
    override val trigger = "/rewind"
    override val label = "回退会话"
    override val description = "打开检查点选择菜单，回退对话与代码"

    override fun execute(context: SlashCommandContext) {
        context.requestRewindMenu()
    }
}
