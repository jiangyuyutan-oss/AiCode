# core — 跨模块基础设施

无业务依赖的底层能力：数据库迁移加载、全局代理、主题、通用 Compose 组件、日志与工具类。全部 feature 与入口共同依赖。

## 结构

```
core/
├── db/
│   ├── MigrationLoader.kt      # 扫 assets/migrations 生成 Room 文件式迁移
│   └── SqlScriptSplitter.kt    # 状态机按语句切分 SQL
├── net/
│   └── AppProxy.kt             # 全局 HTTP 代理接入点
├── theme/
│   ├── AIEditorTheme.kt        # Compose 主题入口
│   └── AppThemePreset.kt       # 多套主题预设配色
├── ui/                         # 通用 Compose 组件库
│   ├── FloatingTabBar.kt / SegmentedTabs.kt / SplitHandle.kt
│   ├── SwipeToDeleteRow.kt / AppSwitch.kt / AppTextField.kt
│   ├── ImageViewer.kt / ImageDecode.kt
│   ├── ImeInset.kt / PageMotion.kt / WindowSize.kt
│   └── glass/                  # 玻璃材质系统（三档磨砂/水玻璃/液体玻璃）
│       ├── GlassMode.kt        # 枚举 + GlassSettings + LocalGlassSettings/LocalBackdrop
│       ├── GlassPanel.kt       # glassPanel Modifier（三档效果链 + 染色 + API33 门槛）
│       └── WaterWaveShader.kt  # 水波 AGSL（runtimeShaderEffect 经平台 RenderEffect 链入）
└── util/
    ├── FileLogger.kt           # 落盘日志（按天分文件、等级阈值、7 天/5MB 清理）
    ├── AILogger.kt             # AI 请求/响应逐会话完整日志
    ├── GitIgnoreMatcher.kt     # gitignore 风格路径段匹配
    ├── LineDiff.kt / LanguageRegistry.kt / ErrorUtils.kt / MediaRedactor.kt
```

## 关键文件

| 文件 | 目的 |
|------|------|
| `db/MigrationLoader.kt` | 扫描 `assets/migrations/{version}_{desc}.sql` 生成 Room `FileMigration`（`version-1 → version`）；整段包事务、失败整体回滚，成功记入 `migration_history` 表 |
| `db/SqlScriptSplitter.kt` | 状态机切分 SQL：识别 `--` / `/* */` 注释、单双引号/反引号字面量（含 `''` 转义）、`[...]` 标识符——字符串里的 `;` 不误切 |
| `net/AppProxy.kt` | `applyGlobal` 在 `attachBaseContext` 设 JVM 全局 `ProxySelector` 与 `Authenticator`（OkHttp 407 显式认证）；容器内命令经 `proxyEnv` 注入 HTTP(S)_PROXY；支持 provider 级代理按 host 分派（配合 settings 的 `ProviderProxyRegistry`） |
| `util/FileLogger.kt` | 外部私有目录按天分文件、单线程串行、等级阈值、自动清理 |
| `ui/glass/GlassPanel.kt` | 玻璃面板 `Modifier.glassPanel(shape, area)`：按 `LocalGlassSettings` 档位组装效果链（磨砂 blur / 水玻璃 blur+AGSL 水波 / 液体玻璃 vibrancy+blur+lens 色散），`onDrawSurface` 主题色染色 alpha≥0.3，API<33 透传；backdrop 源从 `LocalBackdrop` 读（MainActivity 标记壁纸层） |

## 依赖

**本模块依赖**:
- Room（MigrationLoader 实现 `RoomDatabase.Callback` 风格迁移钩子）
- 无任何 feature 依赖（分层底线）

**依赖本模块的**:
- 全部 feature 与入口

## 规范

### 代码模式

**迁移加载**：文件式迁移的切分正确性由 `SqlScriptSplitter` 的状态机保证；新增语 法特性（如存储过程）前先评估切分器兼容性。迁移整体流程见[开发者指南](../DEVELOPER_GUIDE.md)。

**组件库**：`core/ui` 只放跨 feature 复用的无业务组件；带业务状态的组件放各自 feature 的 `presentation/component/`。

### 分层约束

`core/` 不得 import 任何 `feature/` 包——出现即分层破坏，应把代码下沉为更底层能力或移入对应 feature。
