package com.aicode.feature.agent.domain.command

import dagger.Module
import dagger.Binds
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 斜杠命令 multibinding：将每个 [SlashCommandHandler] 实现汇集为 Set，
 * 供 [SlashCommandRegistry] 构造注入。
 *
 * 新增命令时在此追加一行 `@Binds @IntoSet` 绑定即可。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SlashCommandModule {

    @Binds
    @IntoSet
    abstract fun bindStatusCommandHandler(handler: StatusCommandHandler): SlashCommandHandler

    @Binds
    @IntoSet
    abstract fun bindCompressCommandHandler(handler: CompressCommandHandler): SlashCommandHandler

    @Binds
    @IntoSet
    abstract fun bindModelCommandHandler(handler: ModelCommandHandler): SlashCommandHandler

    @Binds
    @IntoSet
    abstract fun bindMemoryCommandHandler(handler: MemoryCommandHandler): SlashCommandHandler

    @Binds
    @IntoSet
    abstract fun bindSkillsCommandHandler(handler: SkillsCommandHandler): SlashCommandHandler

    @Binds
    @IntoSet
    abstract fun bindMcpCommandHandler(handler: McpCommandHandler): SlashCommandHandler

    @Binds
    @IntoSet
    abstract fun bindToolsCommandHandler(handler: ToolsCommandHandler): SlashCommandHandler

    @Binds
    @IntoSet
    abstract fun bindRewindCommandHandler(handler: RewindCommandHandler): SlashCommandHandler

    @Binds
    @IntoSet
    abstract fun bindSetupOpencodeCommandHandler(handler: SetupOpencodeCommandHandler): SlashCommandHandler
}
