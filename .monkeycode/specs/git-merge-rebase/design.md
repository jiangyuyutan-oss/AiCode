# Git 可视化合并 / 变基

Feature Name: git-merge-rebase
Updated: 2026-09-17

## Introduction

Git 页此前只有 push/pull/分支管理，合并与变基只能在终端手敲命令，冲突状态也没有可视化提示。本功能在分支操作菜单增加「合并」入口（可合并或变基），并新增跨 tab 冲突横幅：执行后处于冲突状态时顶部提示冲突文件列表，提供「中止」与「继续变基」操作。

## Requirements

1. WHEN 用户点本地分支操作菜单的「合并」，AiCode SHALL 弹出确认对话框展示合并目标，并提供「合并」「变基」「取消」三个选择。
2. WHEN 用户确认，AiCode SHALL 执行 `git merge <branch>` 或 `git rebase <branch>`，成功后刷新分支与状态。
3. WHEN 合并/变基产生冲突（退出码非零），AiCode SHALL 在 Git 页顶部显示冲突横幅，标题区分合并/变基，列出冲突文件数量与前几个路径。
4. WHEN 冲突横幅可见，AiCode SHALL 提供「中止」按钮（执行 `git merge --abort` / `git rebase --abort`）；变基时另提供「继续变基」按钮（执行 `git rebase --continue`）。
5. 合并/变基命令 SHALL 以 `true` 作为 editor，避免冲突解决提交时卡在交互编辑器。
6. 相关文案 SHALL 走双语 strings.xml（`git_action_*` / `git_*_conflict_*` / `git_merge_confirm_message`）。

## Design

### GitRepository

- `merge(branch)` / `rebase(branch)`：`gitChecked("-c","core.editor=true", ...)`，冲突时抛 `GitCommandFailureException`（携带 git 输出）。
- `abortMerge()` / `abortRebase()` / `continueRebase()`（后者同样 `core.editor=true`）。
- `isMerging()` / `isRebasing()`：`git rev-parse -q --verify MERGE_HEAD / REBASE_HEAD` 非空判断。
- `mergeConflicts()`：解析 `git status --porcelain=v1` 中两列状态码均为字母（U/A/D）的冲突行 → 纯函数 `mergeConflictsFromPorcelain`（单测覆盖 UU/AA/DD/UD/AU/UA/DU、重命名取新路径、引号路径、非冲突行排除）。

### GitViewModel

- `GitUiState` 新增 `isMerging` / `isRebasing` / `conflicts`；`RepoSnapshot` 并发拉取对应仓库状态，refresh 与 runAction 刷新时同步写入。
- `merge(branch)` / `rebase(branch)` / `abortOperation()`（按状态自动选 abort 目标）/ `continueRebase()`。

### UI

- `BranchesTab`：本地非当前分支行新增 Merge 动作 → `pendingMerge` 确认对话框（合并/变基/取消）。
- `GitScreen`：Pager 上方按 `isMerging || isRebasing` 渲染 `MergeConflictBanner`（errorContainer 底色、冲突文件列表、中止按钮、变基时继续按钮）。

## References

- `app/src/main/java/com/aicode/feature/git/domain/GitRepository.kt` — merge/rebase/冲突解析
- `app/src/main/java/com/aicode/feature/git/presentation/GitViewModel.kt` — 冲突状态与方法
- `app/src/main/java/com/aicode/feature/git/presentation/component/GitBranchesTab.kt` — Merge 动作与确认框
- `app/src/main/java/com/aicode/feature/git/presentation/component/GitScreen.kt` — 冲突横幅
