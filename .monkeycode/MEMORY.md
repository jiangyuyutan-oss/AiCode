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
  - 上游主仓库：https://github.com/jieapi/AiCode，remote 名 `upstream`（只拉不推）
  - 本工作 fork（唯一推送目标）：https://github.com/jiangyuyutan-oss/AiCode，remote 名 `origin`
  - 同步上游更新的流程：`git fetch upstream main && git merge upstream/main`（历史已于 16d3c9a 嫁接，此后是常规合并）
  - 上游 v* tag 已冻结其迁移文件（如 v1.10.1 冻结 8..42）；本地这些文件必须与上游逐字节一致，新增迁移只能用更大编号
  - `gh` CLI 可用 git credential helper 的 token 认证：`git credential fill` 取 password 后 `gh auth login --with-token`（token 不得出现在任何输出中）
  - 提交/推送/PR/Release 只针对 origin（jiangyuyutan-oss/AiCode）。禁止 `git push upstream`、禁止向 jieapi/AiCode 开 PR

[User Instruction Summary]
- Date: 2026-09-24
- Context: 用户多次强调代码只提交到自己的 fork，不要给上游
- Instructions:
  - 推送目标永远是 origin = https://github.com/jiangyuyutan-oss/AiCode
  - 不要向 jieapi/AiCode 创建或恢复 PR

[User Instruction Summary]
- Date: 2026-09-30
- Context: 用户要求美术风格对齐华为 HarmonyOS 设计规范，并持续优化流畅度
- Instructions:
  - 美术 token 统一放 `core/theme`（Brand/Radius/Motion/AppFontFamily），改动靠语义色体系传导，不散改各组件硬编码
  - HarmonyOS Sans 字体受版权，禁止臆造伪造塞仓库；换字体只改 `AppFontFamily` 一处。已用 fontTools 从可变字体实例化 Latin 5 档静态（light/regular/medium/semibold/bold，各 ~155KB）放 `res/font/`，CJK 走系统字体回退避免 APK 膨胀。许可证要求 prominent notice + LICENSE 随包：完整协议放 `assets/fonts/HarmonyOS_Sans_LICENSE.txt`，「设置→关于」的 HarmonyOS Sans 字体行弹窗展示
  - 结构级导航改造（底部导航/Dock 触碰有白屏史的抽屉与路由）执行前必须先给方案并征得用户确认
