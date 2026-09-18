# Requirements Document: god-mode

## Introduction

为 AiCode 引入 **God Mode（甲方-乙方协作模式）**：把「用户 ↔ AI」的关系重构成「投资者/甲方 ↔ 公司/乙方」。

- **用户（甲方 / 投资者）**：只提出需求与目标、提供资源（含模型额度），不再亲自处理权限询问、模式切换等执行层琐事。
- **AI（乙方 / 一家公司）**：由 **CEO / 总汇报员**（主会话）担任最高决策者——解析需求、拆解为子任务、派发给 AI 部门（子代理）、组**评审团**辩论审查、按**双执行风格**产出，最后向甲方汇报；所有权限审批与模式切换由 CEO 代为处理，用户可从执行层抽身。

该模式建立在现有子代理系统（agent 定义 / `task` 派发 / AUTO-TARGET 预授权 / 完成通知回传 / review 评审基座）之上，复用最大化，新增一个 `orchestrate` 编排工具与一组角色资产来打通「分解 → 派发 → 评审 → 裁决 → 汇报」闭环。

## Glossary

- **System**: AiCode 应用（Kotlin + Compose + Hilt + Coroutines）。God Mode 是其中的一种会话模式。
- **甲方（A）**: 会话用户，仅提需求、提供服务与额度，不参与执行层操作。
- **CEO / 总汇报员（C）**: 主会话中的 AI──God Mode 的操盘手，最高决策者与对外（对用户）唯一出口。
- **AI 部门（Dept）**: 独立执行的子代理会话（如 `coder-conservative` / `coder-modern`），各按不同模型/思考强度/工具集/代码风格执行一项子任务。
- **评审团（Jury）**: 一组（≥3）不同性格倾向、只读审查的子代理，对关键产出独立发表意见。
- **评审长（Foreman）**: 汇总评审团意见、给出裁决（放行 / 返工 / 换风格）的子代理。
- **执行风格（Style）**: 代码编写风格档位，控制产出代码遵循现有风格（`conservative`）还是革新风格（`modern`）。
- **编排（Orchestrate）**: CEO 分解需求 → 派发部门 → 等待完成 → 组评审 → 吸收裁决 → 再派发/汇报的循环操作。
- **治理（Governance）**: 权限与模式等「实施层动作」，God Mode 下由 CEO 决策，用户不逐一弹窗。

## Requirements

### R1 进入与退出 God Mode

**User Story:** AS 用户, I want 一键把一个会话切到 God Mode, SO THAT 把执行与权限交托给 CEO，自己只做甲方。

#### Acceptance Criteria

1. 系统 SHALL 在会话模式集（BUILD/PLAN/AUTO/TARGET）之外提供 `GOD` 模式，用户可在会话头/设置手动进入。
2. 系统 SHALL 要求在进入 God Mode 前确认任选其一：`甲方全权交托（权限全放行，仅灾难性命令保留拦截）` 或 `甲方留枢（大权限仍弹窗）`，把用户选择记为本次 God 会话的治理策略。
3. IF 用户退出 God Mode，the system SHALL 清理游 per-session 编排状态并回落 BUILD 模式，不破坏既有消息。

### R2. CEO/总汇报员身份与权限代理

**User Story:** 甲方, I want 把「数据 EA → 切模式、审权限」这类操作交给 CEO, SO THAT 我不再处理执行琐事。

#### Acceptance Criteria

1. God Mode 下会话 role 即 CEO；一次对话中所有对用户的文字输出都应标为来自 CEO，且是「对甲方的汇报」而非「执行细节」。
2. WHILE 治理策略为「甲方留枢」，the system SHALL 在遇到高影响权限请求时优先转向 CEO 决策（CEO 权衡后直接 approve/deny），把 CEO 决策作为提示用户的替代通道。
3. WHNF 治理策略为「甲方全交」，the system 复用 AUTO/TARGET 的预授权语义：策略引擎对全部工具放行（仅灾难性 `rm` 等保留拦截）。
4. 系统 SHALL 允许该会话具备最少权限（ro `readFile`/`list`/`search` + `orchestrate`），保障 CEO 只做决策而不被要求高权限执行；具体执行下发到订阅生成的部门会话。

### R3 需求分解与部门派发

**User Story:** 作为 CEO, I want 支持用户需求分解为多个子任务并派发到多个部门（每个部门可指定不同模型/风格/工具集）, 以便并行推进,并保证我能汇总。

#### Acceptance Criteria

1. 系统 SHALL 提供 `orchestrate` 工具给 CEO，支持一次请求内描述「目标、拆分子任务、各自期望的 agent 名/模型/风格」，并创建一批对应部门会话（占 concurrent 预算）。
2. 每个部门会话 SHALL 是独立子会话，继承「governance」但工具集按 AgentDefinition 裁剪；部门间在 CEO 明确转达前不可直接互读结果（保持单向）。
3. CEO SHALL 能显式指定某个子部门使用不同的 provider/model/reasoningEffort（与 `task` 的 `agent=` 复用解析逻辑）。
4. IF 某部门被派发时所用风格/模型不可用（provider 未配置/模型缺失），the system SHALL 返回明确错误并提示 CEO 改派而非静默回退。

### R4 评审团（Jury）辩论与裁决

**用户故事:** 作为甲方, I want 每次核心改动经过评审团多立场审查, SO THAT 大改不踩雷。

#### Acceptance Criteria

1. 系统 SHALL 提供 `orchestrate(phase=review)`，由 CEO 对「已产生的改动集」（diff/文件清单）成立 3 名评审员子代理（`review-tough` 严苛型、`review-pragmatic` 务实型、`review-optimist` 建设型），各自只读审查并产出问题清单。
2. 系统 SHALL 成立评审长（`forehead`）子代理，读三份评论后给出裁决：`approved`（放行）/ `revork`（返工，带必须修项）/ `switch_style`（换风格重跑）。
3. WHNF 裁决为 `approved`, CEO SHALL 进入最终对甲方汇报；IF `rework`/`switch_style`, CEO SHALL 据裁决再派发部门执行后重新评审（循环有上限，防死循环）。
4. 评审全程只读：评审团角色不持有 `writeFile`/`editFile` 权限，仅 `readFile`/`list`/`search`/`readExtern` 类只读工具。

### R5 双执行风格

**User Story:** 开发风格提供新旧双轨, 由 CEO 按需求/甲方偏好取舍。

#### Acceptance Criteria

1. 系统 SHALL 提供两个部门角色：`exec-conservative`（遵循现有代码风格、最小 diff、保持项目惯例）与 `exec-modern`（用革新/现代化的格式与最佳实践重写）。两者共享同一任务描述但风格指引不同。
2. CEO 可选择「单风格执行」或「并行双风格执行 + 对比」交由用户/评审们取舍。
3. IF 并行双风格执行，the system SHALL 保证两部门从同一基线、同一目标文档开始，产物放到各自会话以供对比。
4. 风格差异来自各自 AgentDefinition.prompt（角色头 + 风格指引），不新增全局「风格参数」，以减少对 AgentDefinition 解析器与表单的破坏。

### R6 对甲方汇报与交互界面

**User Story:** 作为甲方, 我要随时看到整场「公司运作」的直观视图（CEO/各部门/评审团状态、裁决、各自结论、消耗）, 以便理解和指导.

#### Acceptance Criteria

1. God Mode 会话 UI SHALL 提供「战情室（War Room）」视图，列出各部门/评审团子会话的卡片：状态（running/finished/failed）、所属模型、产出摘要、裁决结果。
2. 系统 SHALL 在消息流中用品牌标识区分来源：CEO 主气泡、部门/评审团作为可展开卡片，不混用普通 AI 气泡的样式。

### R7 资源（额度）治理

**User Story:** 作为甲方投资者, 我关心整场协作的资源用法（消耗 / 预算 / 健康）。

#### Acceptance Criteria

1. God mode SHALL 聚合父会话 + 所有子部门/评审会话的 cost（input/output/cached tokens）汇总为该场协作的额度消耗（会话维度）。
2. WHNF 会话余额耗尽（预算回调功）, the system SHALL 自动中断编排，把「触达预算、已产出成果位置、可恢复方式」消息给 CEO，CEO 汇报用户并将模式回落到 BUILD。
3. 预算默认 0（不限额）；用户可在会话设置无条件设定限额。

### R8 资产同步与文档

1. 新增 `exec-conservative`, `exec-modern`, `review-tough`, `review-pragmatic`, `review-optimist`, `forehead` 等角色定义 SHALL 放到 `app/src/main/assets/agents/`（首次启动按现有逻辑释放到 `~/.aicode/agents/`）。
2. `60-tools-and-paths.md` 的「子代理工具」节/「模式」文档 SHALL 增补 `orchestrate` 工具、风格与评审团说明；`docs-site/docs/guide/subagent.md` 与应用模式文档同步。
3. 模式选择 UI、战情室字符串均在 `values/strings.xml` 与 `values-en/strings.xml` 双语提供。