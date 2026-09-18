# 容器内 Web 服务预览 — 技术设计

Feature Name: container-preview
Updated: 2026-09-17

## Design

### TerminalViewModel

新增公开 `isRemoteMode(): Boolean`（复用私有 `isRemote()`），供 UI 判断本地/远程。

### TerminalScreen

顶栏 actions 在设置按钮旁新增 Monitor 图标预览按钮；远程模式点击 Toast 提示，本地模式置 `showPreviewDialog = true` 渲染 [ContainerPreviewDialog]。

### ContainerPreviewDialog（新组件）

- 输入态：`AlertDialog` + `AppTextField`（数字过滤、≤5 位、1..65535 校验），确认后切浏览态。
- 浏览态：`Dialog(usePlatformDefaultWidth=false, decorFitsSystemWindows=false)` 全屏，顶栏显示 URL + 关闭/重载/外部打开，`AndroidView(WebView)` 占满剩余空间；WebView 启用 JS 与 DOM storage、支持缩放，factory 建一次、update 加载 URL。
- 外部打开：`ACTION_VIEW` intent + `FLAG_ACTIVITY_NEW_TASK`，`runCatching` 兜底。

## References

- `app/src/main/java/com/aicode/feature/terminal/presentation/TerminalViewModel.kt:46` — isRemote
- `app/src/main/java/com/aicode/feature/terminal/presentation/component/TerminalScreen.kt:124` — 顶栏 actions
- `app/src/main/java/com/aicode/feature/terminal/presentation/component/ContainerPreviewDialog.kt` — 预览对话框