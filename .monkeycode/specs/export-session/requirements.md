# 单会话导出 Markdown

Feature Name: export-session-markdown
Updated: 2026-09-17

## Introduction

会话长按菜单已有「导出」（tar.gz 备份格式，供导入恢复）。本功能并列增加「导出 Markdown」：生成人类可读的对话文档（用户/AI 正文 + 工具调用记录），SAF 保存为 .md 文件，便于发给同事或存档。

## Glossary

- **备份导出**: 现有 tar.gz JSONL 机器格式，供备份恢复用，本功能保留并与其并列。
- **工具记录**: ROLE=TOOL 的消息行（工具名 + 参数摘要 + 是否失败）。

## Requirements

### Requirement 1: 导出入口

1. WHEN 用户在会话长按菜单点击「导出 Markdown」，AiCode SHALL 弹出 SAF 保存对话框，默认文件名 `aicode-session-<标题>-<时间戳>.md`，MIME 为 `text/markdown`。
2. WHEN 保存完成，AiCode SHALL 展示成功/失败提示（复用现有 chat_export_session_done/failed 文案）。

### Requirement 2: Markdown 内容

1. 文档 SHALL 以一级标题（会话标题）与引用行（导出时间、消息数、token 用量）开头。
2. 导出 SHALL 按时间顺序渲染用户消息（二级标题「用户」）、AI 消息（二级标题「AI」）与工具调用（引用块标注工具名、失败状态与参数摘要）。
3. 工具参数 SHALL 单行化并截断至 300 字符，避免超长命令撑爆文档。
4. 附件 SHALL 以 `[附件: 文件名]` 标注在所属用户消息之后。
5. 内部消息 SHALL 被排除：已压缩消息（isCompacted）、上下文摘要（isContextSummary）、压缩锚点（isCompactionMarker）、后台任务通知（BACKGROUND_NOTIFICATION_PREFIX 开头）。
6. 正文为空的 AI 消息 SHALL 跳过（纯工具调用轮次由工具记录说明）。
7. 思考过程（reasoning）SHALL 排除在导出之外。

### Requirement 3: 一致性

1. 所有用户可见文案 SHALL 走双语 strings.xml（`chat_export_session_markdown`）。
2. 生成器 SHALL 为纯函数（输入实体、输出字符串），附 JVM 单测覆盖头部、附件、工具记录、过滤与截断规则。
