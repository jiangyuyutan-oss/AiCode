# Requirements Document

## Introduction

长对话里想从某条消息换一条思路探索时，目前只能新建空会话重打上下文，浪费 token 且丢了上文。本功能在消息操作菜单增加「在此分叉新会话」：以该消息为锚点创建新会话，把锚点及之前的所有消息复制为新会话的初始上下文，用户可以接着继续问，原会话不受影响。

## Glossary

- **分叉**: 从现有会话的某条消息处派生出新会话。
- **锚点消息**: 用户长按选择的那条消息；新会话复制它及之前的所有消息。
- **复制**: 原会话消息物理复制到新会话（改 id 与 sessionId，内容不变），原会话消息保持原样。

## Requirements

### Requirement 1: 分叉入口

**User Story:** AS 开发者，I want 长按一条消息选「在此分叉新会话」，so that 从这条消息起开一条新思路而不用重打上文。

#### Acceptance Criteria

1. WHEN 用户在任意消息上长按打开操作菜单，AiCode SHALL 提供「在此分叉新会话」菜单项。
2. WHEN 用户点击该菜单项，AiCode SHALL 关闭菜单、创建新会话并切换当前会话到新会话。

### Requirement 2: 上下文复制

1. WHEN 分叉执行，新会话 SHALL 复制锚点消息及之前所有消息（含锚点），并按原顺序排布。
2. 复制后的消息 SHALL 保持原内容、角色、附件、token 统计、工具调用记录与时间顺序。
3. 锚点之后的消息 SHALL 不被复制。
4. 原会话及其全部消息 SHALL 保持不变。

### Requirement 3: 新会话身份

1. 新会话 SHALL 绑定当前工作区与当前默认模型（沿用 createSession 既有逻辑）。
2. 新会话标题 SHALL 沿用原会话标题，便于识别是从哪条对话分叉而来。
3. 新会话 SHALL 作为独立会话出现在会话列表，不关联原会话的后续消息。

### Requirement 4: 反馈与文案

1. WHEN 分叉成功，AiCode SHALL 展示「已在此处分叉新会话」提示。
2. 菜单文案 SHALL 走双语 strings.xml（`chat_action_fork`）。
