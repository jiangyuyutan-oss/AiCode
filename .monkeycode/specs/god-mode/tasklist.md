# 需求实施计划

- [ ] 1. 运行时字段与迁移
  - [ ] 1.1 `ChatSessionEntity.kt` 新增三字段并同步到 `domain/model/ChatSession.kt`（design §§5,8,9；R6-R7）
    - Long? 的 God 预算上限
    - String? 的治理策略 full 或 keep（R1-2）
    - String? 的里程碑报告 JSON（长度受限）
    - 新建可序列化报告 data class `GodReport`，字段为 milestone、summary、changes、depts、jury、budget
  - [ ] 1.2 写迁移文件 `app/src/main/assets/migrations/54_add_god_runtime_fields.sql`（CLAUDE.md 迁移三步）
    - `ALTER TABLE chat_sessions ADD COLUMN`，三列均允许 NULL
    - 禁止改动上游已冻结迁移（v1.10.1 覆盖 8..42）
  - [ ] 1.3 把 `AgentDatabase.kt` 的 `SCHEMA_VERSION` 从 53 递增到 54（编号连续）
  - [ ]* 1.4 为本组字段写单元测试
    - 三字段 null 默认读写往返
    - `GodReport` JSON 编解码往返

- [ ] 2. 共享参数解析器
  - [ ] 2.1 从 `TaskTool.kt` 抽离 provider/model/reasoningEffort 解析与回退，新建 `domain/orchestration/AgentParamsResolver.kt`，`TaskTool` 与 `OrchestratorService` 同用（design §3、References [^1][^2]；R3-3、CP6 不触碰 AUTO/PLAN）
  - [ ]* 2.2 为 `AgentParamsResolver` 写单元测试，覆盖 provider 缺失、model 缺失、effort 越界与回退默认值

- [ ] 3. 编排服务补齐（预算账本、双风格、评审）
  - [ ] 3.1 `OrchestratorService` 实现 `budget()`（design §5；R7-1）
    - 读父会话 `totalInputTokens`/`totalOutputTokens` + 全部子会话 tokens
    - 对比 `godBudgetTokens` 并返回 `exhausted` 布尔
    - 超预算同一 round 只发一次通知（CP4）
  - [ ] 3.2 `deploy` 按 `style` 字段解析 `exec-modern`，其余默认 `exec-conservative`，未带字段也默认保守（design §3/§4；R5-1 至 R5-4）
  - [ ] 3.3 给 `OrchestratorService` 增加 rework 计数器与上限（默认 3），超限回传「返工到上限停止」提示（R4-3）
  - [ ] 3.4 评审两段式收口：`review` 三派意见齐后，CEO 再调 `review(foreman=true)` 汇总三份意见给 `forehead`（design §7；R4-1、R4-2）
  - [ ]* 3.5 为编排服务写单元测试
    - 预算聚合属性：父加子 token 合计
    - 超预算只通知一次属性
    - rework 上限达到即停

- [ ] 4. 编排工具新增 report 阶段
  - [ ] 4.1 `phase` 枚举增加 `report`，把 `GodReport` 追加到 `godReportsJson` 并裁剪最老条目（design §8；R6-1；R8-1）
  - [ ] 4.2 为 report 阶段补参数
    - 必填 `summary`
    - 可选 `changes`
    - 可选 depts
    - 缺必填回 `MISSING_ARGS`
  - [ ] 4.3 保持四阶段只读：`status` 与 `budget` 返回只读能力空集，report 不声明写能力（design §3）
  - [ ]* 4.4 写工具层单元测试
    - 断 report 参数 schema 形态
    - 断非 GOD 调用仍返 `NOT_GOD_MODE`

- [ ] 5. 检查点 - 确保所有测试通过,如有疑问请询问用户

- [ ] 6. GOD 治理策略
  - [ ] 6.1 `ChatInputBar.kt` 切 GOD 时弹「甲方留枢 / 甲方全交」二选一对话框，结果随 `setSessionMode(GOD)` 写入 `godStrategy`（R1-2；UI 变化按 CLAUDE.md 同步文档）
  - [ ] 6.2 `AgentMode.GOD` 的权限路由接入 `ToolPermissionPolicyEngine` 读 `godStrategy`
    - `full` 走 AUTO 放行
    - `keep` 仍对高影响操作弹窗（R2-2、R2-3）
    - 两路都保留 `checkCatastrophicRm` 防护
  - [ ]* 6.3 为策略引擎写单元测试
    - `full` 对 `writeFile` 返 ALLOW
    - `keep` 对 `Bash` 返 ASK

- [ ] 7. CEO 最小工具集与退出 GOD
  - [ ] 7.1 `AIAgentViewModel.kt` 在 `mode == GOD && parentId == null` 时裁剪工具到 `readFile/list/search/task` + `orchestrate`（R2-4；design §1 边界）
  - [ ] 7.2 `SwitchModeTool.kt` 允许 `GOD -> BUILD`，切回时清 `godStrategy` 并保留 `godReportsJson`（R1-3）
  - [ ]* 7.3 为 GOD 工具集裁剪写单元测试，断言 `writeFile` 与 `editFile` 不在可见列表

- [ ] 8. 预算超限中断
  - [ ] 8.1 主循环每次 `LlmResponse` 记账后调用 `budget()`；`exhausted` 时注入一条 `BudgetExhausted` 通知（design §5；R7-2；CP4）
  - [ ] 8.2 GOD 会话收到 `BudgetExhausted` 后自动 `switchMode(BUILD, reason=预算耗尽)` 并汇报用户，保留既有 `godReportsJson`（R7-2）
  - [ ]* 8.3 写预算中断测试，断言一轮只注入一次

- [ ] 9. 检查点 - 确保所有测试通过,如有疑问请询问用户

- [ ] 10. War Room 与来源气泡
  - [ ] 10.1 新建 `presentation/component/GodWarRoomPanel.kt`，仅 `GOD` 渲染，用 `subSessionsByParent` + `budget()`，列表按 RUNNING/COMPLETED/FAILED 显示状态、模型、产出摘要、`godReportsJson` 的 jury 裁决、预算条（design §6；R6-1）
  - [ ] 10.2 在 `AIAgentViewModel.kt` 的 `messagesState` 组装层把 `subagentType`/`AgentDefinition.name` 映射到 `AgentUIMessage.sender`，渲染 CEO 气泡 / 部门与评审卡片（R6-2；design §8）
  - [ ] 10.3 `AgentUiModels.kt` 给 `AgentUIMessage` 加 `sender: String? = null` 字段（普通会话行为不变，CP6）
  - [ ] 10.4 所有新文案进入 `values/strings.xml` 与 `values-en/strings.xml`，代码零中文硬编码（R8-3）
  - [ ]* 10.5 写 War Room 映射层单元测试，覆盖 sender 非空与模式回落 `sender == null`

- [ ] 11. 评审角色复用与只读断言
  - [ ] 11.1 复核 `review-tough/review-pragmatic/review-optimist/forehead` 四个角色文件，确保 `tools` 白名单只读（CP2；R4-4）
  - [ ] 11.2 新增 `app/src/main/assets/prompts/84-report-protocol.md`（或并入 `83-god-mode.md`），写明 report 阶段用法与 JSON 示例（R8-1）
  - [ ]* 11.3 写角色工具白名单校验单元测试，断言评审 agent 不含 `writeFile/editFile/Bash`

- [ ] 12. 资产与文档同步（CLAUDE.md 硬规则）
  - [ ] 12.1 更新 `docs-site/docs/guide/modes.md`，补 GOD 治理策略、预算中断、report 协议、War Room 说明（R8-2）
  - [ ] 12.2 同步 `.vitepress/config.ts` 侧栏与 `docs-site/docs/guide/overview.md` 索引
  - [ ] 12.3 更新 `docs-site/docs/guide/subagent.md`，写入 style 与 jury 说明（R8-2）

- [ ] 13. 回归守卫
  - [ ] 13.1 回归 `OrchestratorServiceTest.kt` / `OrchestrateTest.kt` / `SwitchModeToolTest.kt` 既有 GOD 用例并补齐（CP3）
  - [ ] 13.2 冒烟全量：`./gradlew :app:assembleUniversalDebug`（CLAUDE.md 构建验证）
  - [ ] 13.3 跑迁移对账与单测：`python3 scripts/check_migrations.py`、`./gradlew :app:testUniversalDebugUnitTest`（CP 最终关卡）
