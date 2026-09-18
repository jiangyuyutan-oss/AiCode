# Requirements Document: semantic-index

## Introduction

为 AiCode 提供语义代码检索能力：AI agent 可用自然语言（如“服务入口在哪”“登录鉴权怎么实现”）
在工作区内召回相关代码文件，而无需精确记忆文件路径或函数名。本地关键词（TF-IDF）召回，
零模型外发、零网络依赖、离线可用，区别于既有 `search`（rg 精确文本匹配）与 `list`（目录列举）。

## Glossary

- **System**: AiCode Android 应用的 AI 对话 + Agent 工具执行层。
- **semantic_search 工具**: 向 Agent 暴露的自然语言语义检索工具。
- **索引（index）**: 工作区内文本文件的倒排关键词表，支持相关度打分。进程内内存态，不持久化。
- **失效（invalidation）**: 检测到工作区文件变更后，把既有索引标记为陈旧并触发重建。

## Requirements

### Requirement 1: 提供语义检索工具

**User Story:** AS 会话中的 AI, I want 按自然语言召回相关代码文件, SO THAT 不用记住精确路径即能找到实现。

#### Acceptance Criteria

1. 系统 SHALL 为 Agent 注册名为 `semantic_search` 的工具，暴露参数 `query`（自然语言问句、必填）。
2. 工具执行 SHAR 返回按相关度降序、数量有限（默认 ≤ 8）的候选，候选含文件路径与命中片段。
3. WHEN 查询为空或仅空白，系统 SHALL 返回带 `MISSING_QUERY` 错误码的失败结果，不查索引。

### Requirement 2: 索引懒构建

**User Story:** AS 会话中的 AI, I want 无需手动初始化索引, that 首次使用即自动可用。

#### Acceptance Criteria

1. WHEN `semantic_search` 首次被调用, the system SHALL 对当前工作区执行全量索引构建后返回结果。
2. 索引构建 SHALL 跳过二进制文件、`.git`、`build`、`.gradle`、`node_modules` 等非代码目录。
3. 文本文件 SHALL 在可读后按内容纳入索引，读取失败的文件 SHALL 被跳过而不中断整体构建。

### Requirement 3: 索引新鲜度

**User Story:** 工作区代码变化后, I want 检索反映最新内容, that 不返回陈旧命中。

#### Acceptance Criteria

1. 每次查询前 the system SHALL 检测工作区感兴趣文件的 `mtime`/`size` 相对上次构建是否变化。
2. IF 任一被索引文件发生变更, the system SHALL 使既有索引失效并在本次查询前重建。
3. 索引构建 SHALL NOT 阻塞并发（在执行中尝试查询时走串行排队，而非并行构建）。

### Requirement 4: 纯本地运行与隐私

**User Story:** 用户, I want 代码不出本机, that 可离线环境放心使用。

#### Acceptance Criteria

1. 索引构建与检索 SHALL 全部在本进程内完成，不发起任何网络请求，不调用任何远端 embedding。
2. 构建 SHALL NOT 依赖任一 provider API key 或 GOCT 连接状态。
3. IF provider 未配置，THE `semantic_search` 工具 SHALL 依然返回结果。

### Requirement 5: 远程模式行为

**User Story:** 使用远端 SSH 工作区时, I want 语义检索有明确约定, that 不静默失败。

#### Acceptance Criteria

1. IF 当前工作区为远程 SSH 模式, the system SHALL 尝试通过远端文件读取构建索引。
2. IF 远程索引构建因连接中断或权限问题失败, the system SHALL 返回带 `INDEX_UNAVAILABLE` 的错误给 Agent。