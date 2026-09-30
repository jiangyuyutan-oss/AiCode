# git — Git 可视化模块

基于容器内命令行 `git`（非 JGit）的可视化 Git 面板：状态、分支、提交历史（含泳道拓扑图）、差异、标签、暂存/回退、分支合并/变基与冲突横幅、凭据配置。

## 结构

```
feature/git/
├── domain/
│   ├── GitRepository.kt          # 全部 git 子命令聚合（status/branches/log/commit/push/pull...）
│   ├── GitGraphBuilder.kt        # 纯 Kotlin 泳道拓扑布局（供 Canvas 绘制）
│   ├── GitErrorMessage.kt        # git 英文 stderr → 中文友好提示
│   ├── GitCommandFailureException.kt
│   └── model/                    # GitModels.kt / GitGraphModels.kt 领域模型
└── presentation/
    ├── GitViewModel.kt
    └── component/
        ├── GitScreen.kt / GitStatusTab.kt / GitBranchesTab.kt / GitLogTab.kt
        └── DiffViewer.kt         # 语法高亮 diff 视图
```

## 关键文件

| 文件 | 目的 |
|------|------|
| `domain/GitRepository.kt` | 经 `CommandEngine.runCommandSyncUnbounded` 执行 git（cwd=当前工作区），逐参数 shell 转义拼 `/bin/sh -c`；统一带 `-c core.quotepath=false` 防中文路径乱码。读命令 `git()`，写命令 `gitChecked()`（非零退出抛 `GitCommandFailureException` 携带 git 输出）。合并/变基：`merge` / `rebase` / `abortMerge` / `abortRebase` / `continueRebase` 一律带 `-c core.editor=true` 防交互式提交编辑器挂起；`isMerging` / `isRebasing` 用 `rev-parse --verify MERGE_HEAD` / `REBASE_HEAD` 探测；冲突文件由纯函数 `mergeConflictsFromPorcelain` 解析 `status --porcelain=v1`（两列同属 `UAD` 即未合并） |
| `domain/GitGraphBuilder.kt` | 解析 `git log`（0x1f 分隔字段）做泳道布局，输出 `GitGraph` 领域模型 |
| `domain/GitErrorMessage.kt` | stderr 模式匹配转译（冲突/无凭据/网络错误等） |
| `presentation/component/GitBranchesTab.kt` | 分支右键菜单的 Merge 动作（非当前分支）弹一个「合并 / 变基 / 取消」确认框 |
| `presentation/component/GitScreen.kt` | 合并/变基进行中且非加载态时，tabs 上方渲染 `MergeConflictBanner`：冲突文件数 + 前 3 条路径（列表为空回退终端提示），恒有「中止」，变基时另有「继续变基」 |

## 依赖

**本模块依赖**:
- `feature/agent` — `CommandEngine`（容器内执行）
- `feature/settings` — 执行模式
- `feature/credentials` — 凭据（`credential.helper=store` + 自定义 helper 注入，不经 agent 工具链/权限引擎）

**依赖本模块的**:
- `MainActivity` / `WorkbenchPane` — Git 面板挂载

## 规范

### 代码模式

**命令执行**：一律走 `CommandEngine`，保持本地容器/远程 SSH 一致；拼命令必须逐参数转义，禁止拼接用户输入。

**错误处理**：写命令失败抛 `GitCommandFailureException`，UI 层经 `GitErrorMessage` 转中文提示展示。

### 与 AI 的边界

Git 面板的凭据配置直接读写凭据仓库，不经 AI 工具链与授权弹窗；AI 侧的 git 操作则走 `Bash` 工具受权限体系管控。
