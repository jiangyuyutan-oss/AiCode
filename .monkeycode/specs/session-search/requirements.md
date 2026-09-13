# Requirements Document

## Introduction

会话内搜索：在会话列表按标题过滤会话，并在当前会话内按全文搜索历史消息、点击跳转定位并高亮，解决「会话和消息多了以后找回之前的内容只能靠翻」的痛点。

## Glossary

- **会话列表**: AI 页侧边栏「会话页」中的会话条目列表。
- **消息全文搜索**: 在当前会话的全部已落库历史消息（用户/助手/工具结果）中按内容包含匹配。
- **跳转定位**: 滚动消息列表使目标消息进入视口并高亮显示。

## Requirements

### Requirement 1: 会话标题过滤

**User Story:** AS 用户，I want 在会话列表按标题过滤会话，so that 快速找到目标会话。

#### Acceptance Criteria

1. WHEN 用户在会话列表搜索框输入文本，系统 SHALL 按标题不区分大小写包含匹配过滤会话条目。
2. WHEN 用户清空搜索框文本，系统 SHALL 显示全部会话。
3. WHILE 搜索框有文本，系统 SHALL 对新建会话等列表操作保持正常（过滤仅作用于显示）。

### Requirement 2: 消息全文搜索

**User Story:** AS 用户，I want 在当前会话内搜索历史消息，so that 找回之前的内容。

#### Acceptance Criteria

1. WHEN 用户提交搜索查询，系统 SHALL 在当前会话全部已落库消息中按内容不区分大小写包含匹配，返回至多 50 条结果。
2. WHEN 返回搜索结果，系统 SHALL 每条结果展示角色、匹配片段与相对时间。
3. WHILE AI 正在流式输出，系统 SHALL 允许搜索已落库历史消息。
4. IF 查询命中空字符转义（`%`、`_`），系统 SHALL 按字面量匹配。

### Requirement 3: 跳转定位与高亮

**User Story:** AS 用户，I want 点击搜索结果跳转到对应消息，so that 看到完整上下文。

#### Acceptance Criteria

1. WHEN 用户点击某条搜索结果，系统 SHALL 滚动消息列表使该消息进入视口并高亮显示。
2. IF 目标消息未在当前已加载分页中，系统 SHALL 扩大消息加载范围直至包含该消息后跳转。
3. WHEN 高亮显示超过 5 秒或用户滚动列表，系统 SHALL 清除高亮。

### Requirement 4: 空态与无结果

**User Story:** AS 用户，I want 搜索无结果时得到明确反馈，so that 知道如何调整查询。

#### Acceptance Criteria

1. IF 搜索查询无命中，系统 SHALL 显示无结果提示。
2. IF 当前会话无历史消息，系统 SHALL 显示空会话提示且搜索入口不可用。
