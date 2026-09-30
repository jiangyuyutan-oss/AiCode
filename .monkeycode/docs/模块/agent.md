# agent — AI Agent 核心模块

驱动 LLM 对话循环的最大业务模块：提示词组装 → 多协议 provider 调用 → 工具调用与权限管控 → 回填结果，直至产出最终回复。同时拥有 MCP 扩展、检查点回滚、子代理、上下文压缩、God Mode 编排等全部 Agent 能力。

## 结构

```
feature/agent/
├── data/
│   ├── local/
│   │   ├── database/AgentDatabase.kt   # Room 主库，SCHEMA_VERSION = 53
│   │   ├── entity/                     # 6 个实体：会话/消息/待办/检查点×2/调用记录
│   │   └── dao/                        # 5 个 DAO
│   └── remote/
│       ├── anthropic/                  # AnthropicApi + DTO（Messages API）
│       ├── openai/                     # OpenAIApi（Chat Completions + Responses + Images）
│       ├── gemini/                     # GeminiApi（generateContent + Interactions）
│       └── RemoteSshConnection.kt      # 共享 sshj client（远程后端的总连接）
├── domain/
│   ├── workflow/StatefulAgentWorkflow.kt  # 状态机主循环（AgentEvent 事件流）
│   ├── provider/                       # AIProvider 接口 + 三适配器 + RetryPolicy/Key 轮换
│   ├── tool/                           # AgentTool 基类 + 22 个内置工具 + ToolRegistry
│   │   ├── semantic/                   # 本地 TF-IDF 语义检索（倒排索引内存态，不落盘）
│   │   └── orchestration/OrchestrateTool.kt  # God Mode 编排工具
│   ├── orchestration/OrchestratorService.kt  # CEO 编排域服务（批量派发/评审团/预算聚合）
│   ├── permission/ToolPermissionPolicyEngine.kt  # 授权策略引擎（五模式）
│   ├── mcp/McpManager.kt               # MCP stdio/HTTP 客户端与动态工具注册
│   ├── checkpoint/CheckpointManager.kt # 检查点创建/快照/回滚
│   ├── subagent/                       # AgentDefinition + 事件总线 + 定义源
│   ├── session/                        # SessionUseCase / MessagePersistenceUseCase
│   ├── model/                          # ChatSession（含 AgentMode 五档）/ AgentMessage / AgentContext
│   ├── command/                        # 斜杠命令（multibinding 注册）
│   └── ...（memory / skill / todo / container / prompt）
└── presentation/
    ├── AIAgentViewModel.kt             # 约 2400 行，agent 功能唯一 ViewModel
    └── component/                      # AIChatPanel、消息气泡、Markdown 渲染链、回滚面板、语音听写控制器等
```

## 关键文件

| 文件 | 目的 |
|------|------|
| `domain/workflow/StatefulAgentWorkflow.kt` | 核心状态机：LLM 请求 → 工具循环 → 权限弹窗 → checkpoint，20+ 依赖注入 |
| `domain/tool/AgentTool.kt` | 工具公共接口（见 [接口文档](../INTERFACES.md)） |
| `domain/tool/ToolRegistry.kt` | @Singleton 保序注册表；PLAN 模式拦截交给策略引擎 |
| `domain/permission/ToolPermissionPolicyEngine.kt` | 弹窗前 ALLOW/DENY/ASK 判定 + PLAN 物理拦截 + AUTO/TARGET/GOD 灾难命令防护 |
| `domain/orchestration/OrchestratorService.kt` | God Mode 编排域服务：一批最多 5 个部门子代理、评审团（3 派 + 可选评审长）、父子 token 预算聚合 |
| `domain/tool/orchestration/OrchestrateTool.kt` | `orchestrate` 工具：phase = decompose/review/status/budget，非 GOD 模式报 `NOT_GOD_MODE` |
| `domain/tool/semantic/` | `semantic_search`：TF-IDF 倒排索引（内存态、按工作区文件树快照重建），CJK 双字符分词 |
| `domain/mcp/McpManager.kt` | 订阅 4 信号源自动 reload；stdio 跑在 PRoot 容器内，HTTP 走 Streamable + SSE |
| `domain/checkpoint/CheckpointManager.kt` | 每条消息建节点、改前抓快照、三维回滚 |
| `domain/provider/AIProvider.kt` | 统一 provider 抽象（complete / completeStream） |
| `presentation/AIAgentViewModel.kt` | 多会话并行 UI 状态、权限弹窗回传、子代理事件、会话分叉（forkSessionAt）、Markdown 导出、唤醒锁与保活 |
| `presentation/component/ChatVoiceInputController.kt` | 系统 SpeechRecognizer 语音听写：实时 partial 回填输入框，final 落草稿 |

## 依赖

**本模块依赖**:
- `feature/terminal` — `CommandEngine` 执行 Shell
- `feature/workspace` — `FileAccessProvider` 读写文件
- `feature/settings` — `AIProviderRepository`（模型配置）、`ExecutionModeHolder`（模式）
- `feature/credentials` — 远程 SSH 凭据
- `terminal-emulator` — 后台命令的输出解析

**依赖本模块的**:
- `MainActivity` / `WorkbenchPane` — 聊天面板挂载
- `feature/editor` — 复用 `MarkdownContent` 渲染链

## 规范

### 代码模式

**新工具**：继承 `AgentTool`（流式输出实现 `StreamingAgentTool`），在 `di/AgentModule.kt` 注册，同步更新 `assets/prompts/`。详见[开发者指南](../DEVELOPER_GUIDE.md)。

**授权**：工具调用统一走 `ToolPermissionPolicyEngine`，判定顺序 DENY → 静态不可判定 ASK → 内置白名单 → 已记忆规则 → ASK。AUTO / TARGET / GOD 三种模式同分支放行（不弹窗）但保留灾难性 `rm` 拦截（根目录、通配、家目录、工作区根、系统关键目录集）。

**God Mode 编排**：主会话切到 `AgentMode.GOD` 后成为 CEO，经 `orchestrate` 工具调用 `OrchestratorService` 批量派发部门子代理（exec-conservative / exec-modern 双风格）与只读评审团（tough / pragmatic / optimist + forehead 评审长）。子代理间不直连，完成经 `SubAgentEventBus` 通知父会话；一批上限 5 个，对齐 `SubAgentEventBus.MAX_RUNNING`。

**错误处理**：工具失败返回 `ToolResult.Error` 回传给 LLM（AI 自行重试/改道），进程级异常经 workflow 事件流上报 UI；LLM 网络错误由 `RetryPolicy` 阶梯重试——重试计数跨轮累计，收到内容后不重置（曾有把 attempt 清零导致永远 1/6 无限循环的回归，`RetryPolicyTest` 有守卫用例），Key 失效由 `KeyFailureClassifier` 触发轮换。

### 测试

纯 JVM 单测 + MockK 打桩 + `kotlinx-coroutines-test`（`runTest` 虚拟时间）；数据库迁移测试用 Robolectric + `MigrationTestHelper`（仅 universalDebug 变体）。
