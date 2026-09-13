package com.aicode.feature.agent.domain.command

/**
 * 斜杠命令处理器接口。每条命令（如 /status、/compress）实现一个此类，
 * 通过 Hilt `@Binds @IntoSet` 自动汇集到 [SlashCommandRegistry]。
 *
 * 新增命令只需：新建一个实现类 + 在 SlashCommandContext 上补对应方法 + ViewModel 实现。
 */
interface SlashCommandHandler {

    /** 触发文本，如 "/status"。必须以 '/' 开头。 */
    val trigger: String

    /** 菜单显示名，如 "会话状态"。 */
    val label: String

    /** 菜单描述。 */
    val description: String

    /**
     * 完全匹配判断：仅当用户发送的文本精确等于 [trigger] 时命中。
     * 子类通常无需重写。
     */
    fun matches(input: String): Boolean = input.trim() == trigger

    /** 命中后执行的操作。 */
    fun execute(context: SlashCommandContext)
}

/**
 * 命令执行上下文：只暴露命令需要的最小能力，避免命令直接持有 ViewModel。
 * 由 AIAgentViewModel 实现，新增命令时在此接口补方法并在 ViewModel 实现。
 */
interface SlashCommandContext {
    fun showSessionStatus()
    fun compactCurrentSession()

    /** 打开模型选择弹窗（等同点输入栏的模型按钮）。 */
    fun requestModelSheet()

    /** 以 Markdown 气泡列出当前会话生效的项目规则（prompts 分层注入内容）。 */
    fun showMemoryOverview()

    /** 以 Markdown 气泡列出已安装技能（名称/描述/启用状态）与内置子代理。 */
    fun showSkillsOverview()

    /** 以 Markdown 气泡列出 MCP 服务器连接状态与工具数。 */
    fun showMcpOverview()

    /** 以 Markdown 气泡列出当前注册可用的工具。 */
    fun showToolsOverview()

    /** 打开当前会话的回退（检查点）选择菜单。 */
    fun requestRewindMenu()

    /**
     * 导出当前生效 provider 配置到容器内 ~/.aicode/opencode-provider.json，
     * 并把 setup-opencode 技能正文作为请求发给 AI 执行（检测/安装 opencode CLI + 生成配置）。
     */
    fun runSetupOpencode()
}
