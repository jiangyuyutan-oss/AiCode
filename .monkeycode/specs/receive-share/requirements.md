# Requirements Document

## Introduction

AiCode 当前的主入口仅"从启动器点击进入"，移动端最高频的"在浏览器/IDE 看到一段报错或代码 → 系统分享菜单 → 发给编程助手分析"路径完全缺失（`ACTION_SEND` 0 引用）。本功能为 MainActivity 增加 `text/plain` 与 `image/*` 的分享接收能力，把外部文本或图片注入会话，让 AI 直接分析，补齐移动端主入口。

## Glossary

- **分享来源**: 通过系统分享菜单（`ACTION_SEND`）发起分享的外部 App，如浏览器、代码编辑器、剪贴板工具。
- **冷启动**: AiCode 进程未在前台运行时收到分享 Intent，需由该 Intent 拉起 App。
- **热启动**: AiCode 已在前台运行时收到分享 Intent（经 `onNewIntent` 投递）。
- **会话输入框**: 聊天页底部 `ChatInputBar` 的文本编辑区。
- **注入**: 把分享文本填入会话输入框，待用户确认或编辑后发送，而非自动发出。

## Requirements

### Requirement 1: 接收纯文本分享

**User Story:** AS 开发者，I want 把外部 App 选中的文本/报错通过系统分享菜单发给 AiCode，so that AI 能直接分析这段内容而免手动复制粘贴。

#### Acceptance Criteria

1. WHEN 分享来源以 `ACTION_SEND` 携带 `EXTRA_TEXT` 且 MIME 为 `text/plain` 发起分享，AiCode SHALL 把该文本填入目标会话输入框。
2. WHEN 分享文本超过 20000 字符，AiCode SHALL 仅保留前 20000 字符填入输入框并向用户展示截断提示。
3. IF 分享 Intent 既无 `EXTRA_TEXT` 又无可解析的文本流，AiCode SHALL 展示"未接收到有效内容"提示且不创建会话、不崩溃。

### Requirement 2: 接收图片分享

**User Story:** AS 开发者，I want 把截图或图片分享给 AiCode，so that AI 能识图分析界面或报错画面。

#### Acceptance Criteria

1. WHEN 分享来源以 `ACTION_SEND` 携带 `EXTRA_STREAM` 且 MIME 为 `image/*` 发起分享，AiCode SHALL 走现有附件上传流程把图片加入当前会话输入区待发送。
2. IF 图片 URI 读取失败，AiCode SHALL 展示"图片加载失败"提示且保留已注入的文本内容。

### Requirement 3: 冷启动路由

**User Story:** AS 开发者，I want App 没开时也能从分享菜单直接拉起 AiCode，so that 不必先手动启动再粘贴。

#### Acceptance Criteria

1. WHEN AiCode 进程未在前台时收到分享 Intent，AiCode SHALL 新建一个会话并把分享文本注入该会话输入框，随后导航到聊天页。
2. WHILE 新建会话尚未落库完成，AiCode SHALL 在输入框展示分享文本，避免文本因会话初始化时序丢失。

### Requirement 4: 热启动注入

**User Story:** AS 开发者，I want App 已在前台时分享内容直接进当前会话，so that 能在既有上下文里继续追问。

#### Acceptance Criteria

1. WHILE AiCode 已在前台且当前存在会话，收到分享 Intent，AiCode SHALL 把分享文本追加到当前会话输入框末尾。
2. WHILE AiCode 已在前台但当前无会话（如在设置页），收到分享 Intent，AiCode SHALL 新建会话、注入文本并导航到聊天页。

### Requirement 5: 多次分享去重与合并

**User Story:** AS 开发者，I want 连续分享多段内容能合并而不是互相覆盖，so that 能把报错和代码分两次发进来一起分析。

#### Acceptance Criteria

1. WHEN 2 秒内连续收到多份分享文本，AiCode SHALL 把各段以换行拼接追加到输入框，保留每段内容。
2. WHEN 同一 Intent（相同 `EXTRA_TEXT` 与时间戳）在 5 秒内被重复投递，AiCode SHALL 只注入一次。

### Requirement 6: 分享后由用户确认发送

**User Story:** AS 开发者，I want 分享进来的内容先填入输入框让我编辑确认，so that 我能补一句指令（如"分析这段报错"）再发给 AI。

#### Acceptance Criteria

1. WHEN 分享文本注入输入框后，AiCode SHALL 聚焦输入框并保持待发送状态，由用户点击发送按钮触发。
2. AiCode SHALL NOT 在注入后自动触发发送。

### Requirement 7: 用户可见中文文案

**User Story:** AS 用户，I want 分享相关提示用我的语言显示，so that 截断、加载失败等状态能看懂。

#### Acceptance Criteria

1. WHEN 需展示分享相关提示，AiCode SHALL 从 `values/strings.xml` 与 `values-en/strings.xml` 引用 `stringResource`，不在 `.kt` 中硬编码中文 UI 文案。
2. AiCode SHALL 为分享相关文案使用 `share_` 前缀的语义化英文小写下划线命名。
