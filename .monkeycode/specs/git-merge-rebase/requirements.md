# Requirements Document

## Introduction

Git 页分支操作只有切换/重命名/删除/拉取/推送，缺少合并与变基；冲突时完全无可视化。本功能补齐分支合并/变基入口与冲突横幅，让「整合分支 → 遇冲突 → 处理/中止」在 Git 页内闭环。

## Glossary

- **合并**: `git merge`，把目标分支合入当前分支。
- **变基**: `git rebase`，把当前分支重新垫到目标分支之上。
- **冲突横幅**: 合并/变基产生未解决冲突时，Git 页顶部显示的状态栏。

## Requirements

### Requirement 1: 合并/变基入口

**User Story:** AS 开发者，I want 在分支菜单直接合并或变基其他分支，so that 不必切到终端手敲命令。

1. WHEN 用户点本地非当前分支的操作菜单，AiCode SHALL 提供「合并」动作。
2. WHEN 用户点击「合并」，AiCode SHALL 弹出确认框，展示目标分支与当前分支，并提供「合并」「变基」「取消」三个选项。
3. WHEN 用户选择合并或变基，AiCode SHALL 执行对应 git 命令并刷新分支、状态与历史。

### Requirement 2: 冲突状态可见

1. WHEN 合并/变基因冲突失败，AiCode SHALL 在 Git 页顶部（跨 tab）显示冲突横幅，标题区分合并/变基冲突。
2. 冲突横幅 SHALL 展示未解决冲突文件数量与最多 3 个文件路径。
3. WHEN 仓库处于冲突状态（存在 MERGE_HEAD/REBASE_HEAD），横幅 SHALL 持续显示直至冲突解决或操作中止。

### Requirement 3: 处理与中止

1. WHEN 冲突横幅可见，AiCode SHALL 提供「中止」按钮，按当前状态执行 `git merge --abort` 或 `git rebase --abort`，回到操作前状态。
2. WHEN 冲突横幅可见且当前为变基，AiCode SHALL 另提供「继续变基」按钮，供用户解决冲突并暂存后继续。
3. 冲突解决需手动完成（工作区终端 + `git add`），AiCode SHALL 在横幅中提示该流程。

### Requirement 4: 一致性

1. 所有文案 SHALL 走双语 strings.xml。
2. 合并/变基命令 SHALL 用 `-c core.editor=true`，避免冲突解决提交打开交互编辑器阻塞。