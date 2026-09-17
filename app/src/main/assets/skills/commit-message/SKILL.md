---
name: "commit-message"
description: "用户提交代码、写 commit message、准备 push 时主动使用：按 Conventional Commits 规范生成提交信息。用户说「提交」「commit」「写个提交信息」「帮我 push」时触发。Use when the user commits or asks for a commit message - generate Conventional Commits format (type(scope): subject)."
---
## 规范提交信息（Conventional Commits）

把「想到啥写啥」的提交变成可追溯、可生成 changelog 的规范提交。

## 何时触发

- 用户说「提交」「commit」「写个提交信息」「帮我 push」
- 改完一组相关改动准备 commit 时

## 工作流

1. 用 `search`/`readFile` 或 git diff 看清本次改了什么（哪些文件、增删了什么），不要凭印象写。
2. 判定 `type`：
   - `feat` 新功能 / `fix` 修 bug / `refactor` 重构 / `docs` 文档 / `style` 格式 / `chore` 杂项 / `ci` / `build` / `perf` 性能 / `test` 测试
3. `scope` 可选，用功能模块：`agent | settings | terminal | workspace | git | ui | mcp | db | core | docs | build | deps`。
4. `subject` 一行简述，中英文均可，句末不加句号，用祈使句（"修复…" 而非"修复了…"）。
5. 正文（空行隔开）：说清改了什么、为什么改，关联 issue/PR。
6. 若破坏兼容或迁移有影响，在正文标 `BREAKING CHANGE:`。

## 产出

- 一条规范 commit message，直接给用户复制或代为执行 `git commit`。
- 若改动跨多个 `type`/`scope`，建议拆成多个提交而不是塞一条。
- 提醒用户：仅紧急情况才 `git commit --no-verify` 跳过 commit-msg 钩子。
- 提交前提醒跑冒烟编译（改了编译型代码）与迁移对账（改了 schema）。
