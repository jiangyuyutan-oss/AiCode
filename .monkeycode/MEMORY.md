# User Instruction Memory

This file records user instructions, preferences, and teachings for reference in future interactions.

## Format

[User Instruction Summary]
- Date: [YYYY-MM-DD]
- Context: [Mentioned scenario or time]
- Instructions:
  - [Content]

## Deduplication Strategy
- Before adding a new entry, check for similar or identical instructions.
- If a duplicate is found, skip the new entry or merge it with the existing one.

## Entries

[仓库同步关系]
- Date: 2026-09-12
- Context: 用户要求把上游 jieapi/AiCode v1.10.1 的最新源码同步进本仓库
- Category: Operations & Deployment
- Instructions:
  - 上游主仓库（用户自己的，带正式 v* 发版 tag）：https://github.com/jieapi/AiCode，remote 名 `upstream`
  - 本工作 fork（推送目标）：https://github.com/jiangyuyutan-oss/AiCode，remote 名 `origin`
  - 同步上游更新的流程：`git fetch upstream main && git merge upstream/main`（历史已于 16d3c9a 嫁接，此后是常规合并）
  - 上游 v* tag 已冻结其迁移文件（如 v1.10.1 冻结 8..42）；本地这些文件必须与上游逐字节一致，新增迁移只能用更大编号
  - `gh` CLI 可用 git credential helper 的 token 认证：`git credential fill` 取 password 后 `gh auth login --with-token`（token 不得出现在任何输出中）
