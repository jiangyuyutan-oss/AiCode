# settings — 设置与 Provider 管理模块

管理 AI Provider（多 Key 轮换、每提供商代理）、执行模式切换、主题语言等全部设置。存储双轨：provider 等结构化数据进 Room，其余各设置域独立 DataStore。

## 结构

```
feature/settings/
├── data/
│   ├── local/
│   │   ├── entity/AIProviderEntity.kt      # ai_providers 表
│   │   └── dao/AIProviderDao.kt
│   │   ├── repository/                          # 20+ 个 DataStore 仓库
│   │   │   ├── ExecutionModeRepository.kt       # 本地/远程模式 + 远程连接配置
│   │   │   ├── KeepaliveSettingsRepository.kt
│   │   │   ├── ThemeSettingsRepository.kt / LanguageSettingsRepository.kt
│   │   │   ├── ProxySettingsRepository.kt / LogSettingsRepository.kt
│   │   │   ├── BackgroundSettingsRepository.kt  # 背景图/磨砂 + 玻璃材质 6 键聚合
│   │   │   └── ContainerSettingsRepository.kt / SyncSettingsRepository.kt / ...
│   ├── ExecutionModeHolder.kt               # 模式内存 StateFlow 缓存（委托层分发依据）
│   ├── ProviderKeyRotator.kt                # 多 Key 轮换/冷却
│   ├── ProviderProxyRegistry.kt             # provider 级代理分派
│   └── remote/                              # ModelApiService（单测+批量）/ UpdateCheckService / ContainerImageDownloader
└── presentation/component/
    ├── SettingsScreen.kt                    # 设置入口页
    ├── ProvidersAndLogSection.kt            # Provider 管理（拖拽排序）
    └── McpSettingsSection.kt / ProxySection.kt / SkillsSection.kt / SubAgentsSection.kt
```

## 关键文件

| 文件 | 目的 |
|------|------|
| `data/local/entity/AIProviderEntity.kt` | provider 配置：明文 apiKey + 多 Key 轮换字段 + 每提供商代理字段 + 自定义请求头/脚本参数 |
| `ExecutionModeHolder.kt` | 执行模式的内存 StateFlow，启动时从 repository 读首帧值；`DelegatingCommandEngine` / `DelegatingFileAccess` / `DelegatingTerminalSessionProvider` 同步读取 |
| `ProviderKeyRotator.kt` | 同一 provider 多 Key 自动轮换，失效 Key 冷却 |
| `data/remote/ModelApiService.kt` | 模型连通性测试：`testModel` 发极短 "hi" 请求验证 Key+模型+端点（OpenAI/Anthropic/Gemini 三协议）；`SettingsViewModel.testAllModels` 限并发 4（`Semaphore`）批量测当前 provider 全部模型，`BatchTestState` 聚合 total/done/success，`stopAllModels` 取消 |
| `data/repository/BackgroundSettingsRepository.kt` | 背景图路径/透明度/磨砂强度 + 玻璃材质 6 键（总开关/档位/三区域开关/水波动画），`glassStateFlow` 聚合（半径 = `frost_intensity` × 32，零 Room 迁移） |
| `data/remote/UpdateCheckService.kt` | GitHub Release 更新检查 |

## 依赖

**本模块依赖**:
- Room（`AIProviderEntity` 挂在 `AgentDatabase` 上）
- `core/net/AppProxy` — 全局代理应用

**依赖本模块的**:
- 几乎所有 feature（拿配置与执行模式）——settings 是依赖汇聚点

## 规范

### 代码模式

**新设置域**：每域一个独立 `preferencesDataStore` + `@Singleton` Repository（如 `EditorSettingsRepository`）。避免把所有设置塞进同一个 preferences 文件（写放大与迁移困难）。

**执行模式切换**：改模式必须经 `ExecutionModeRepository` → `ExecutionModeHolder`，保证三个委托层即时感知；不要在各处缓存模式快照。

**模型连通性测试**：单测 `ModelApiService.testModel` 发极短 "hi" 请求验证 Key+模型+端点；批量 `testAllModels(provider)` 限并发 4（`Semaphore.withPermit`）测当前 provider 模型列表全部模型，逐条复用 `_testing`/`_testResults`（每行实时反映），`BatchTestState` 聚合 total/done/success，`stopAllModels` 取消（OkHttp 阻塞调用不响应 cancel，`finally` 清残留 testing 标记兜底）。

**玻璃材质配置**：`BackgroundSettingsRepository` 在既有 `background_prefs` 加 6 个玻璃键 + `glassStateFlow` 聚合（复用 `frost_intensity` × 32 换算半径，零 Room 迁移）；UI 侧 `glassPanel` Modifier 从 `LocalGlassSettings`/`LocalBackdrop` 读配置与壁纸源（见 [core 模块](./core.md)），三区域（侧边栏/输入框/内容面板）接线在 MainActivity 与 AIChatPanel/ChatInputBar。

### 敏感信息

`AIProviderEntity.apiKey` 明文存 Room（本地 App 私有目录）；备份导出走 [backup 模块](./backup.md) 的 AES-GCM 加密。
