# Requirements Document

## Introduction

通知快捷操作：AI 等待工具权限批准或计划批准且 App 在后台时，发出带快捷操作按钮的通知，用户在通知上直接批准/拒绝，减少回 App 打断，配合 AUTO/TARGET 全自动执行流。

## Glossary

- **权限请求**: AI 调用需授权工具时产生的 `PendingToolPermission`，App 内以权限面板展示并等待用户选择。
- **计划批准**: PLAN 模式下 AI 申请切换到 BUILD 时产生的 `PlanApprovalRequest`，App 内以计划审批面板展示。
- **后台**: App 进程存活但不在前台（`ProcessLifecycleOwner` 未处于 STARTED 及以上）。

## Requirements

### Requirement 1: 权限等待通知

**User Story:** AS 用户，I want AI 等待权限时收到通知，so that 我在通知上直接处理而不必回 App。

#### Acceptance Criteria

1. WHEN AI 产生权限请求且 App 处于后台，系统 SHALL 发出含「批准」「拒绝」快捷操作按钮的通知。
2. WHEN 通知展示，系统 SHALL 正文展示工具名与权限摘要。
3. WHILE App 处于前台，系统 SHALL 以 App 内权限面板处理权限请求（不发快捷操作通知）。

### Requirement 2: 通知上执行权限选择

**User Story:** AS 用户，I want 点通知按钮直接批准/拒绝，so that 一键解决等待。

#### Acceptance Criteria

1. WHEN 用户点击通知上的「批准」，系统 SHALL 以批准选择解决该权限请求并取消通知。
2. WHEN 用户点击通知上的「拒绝」，系统 SHALL 以拒绝选择解决该权限请求并取消通知。
3. IF 通知到达时对应权限请求已被解决（App 内处理过），系统 SHALL 忽略该操作且状态保持一致。

### Requirement 3: 计划批准通知

**User Story:** AS 用户，I want AI 申请切到 BUILD 时收到通知，so that 及时批准开始实施。

#### Acceptance Criteria

1. WHEN AI 产生计划批准请求且 App 处于后台，系统 SHALL 发出含「批准」快捷操作按钮的通知。
2. WHEN 用户点击通知上的「批准」，系统 SHALL 批准计划并切换到 BUILD 模式，同时取消通知。
3. WHILE App 处于前台，系统 SHALL 以 App 内计划审批面板处理（反馈/拒绝仍回 App 内完成）。

### Requirement 4: 状态一致与清理

**User Story:** AS 用户，I want 通知与 App 内状态保持一致，so that 处理过的请求不再重复弹出。

#### Acceptance Criteria

1. WHEN 权限或计划请求已在任意入口被解决，系统 SHALL 取消对应通知。
2. WHEN 新的权限/计划请求替换旧请求（同会话串行），系统 SHALL 更新通知内容并复用同一通知 id。
