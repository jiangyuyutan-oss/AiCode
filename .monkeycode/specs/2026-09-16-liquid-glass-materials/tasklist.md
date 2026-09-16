# 玻璃材质系统实施计划

Feature Name: liquid-glass-materials
Updated: 2026-09-16

依赖顺序：M1 → M2 → M3 → M4 → M5 → M6 串行推进；每个里程碑末尾有编译/测试检查点，通过后才进入下一里程碑。

```text
[TODOLIST]
T1   app/build.gradle.kts 引入 io.github.kyant0:backdrop:2.0.1 并同步验证
T2   新建 core/ui/glass/GlassMode.kt（枚举 + GlassSettings + LocalGlassSettings）
T3   BackgroundSettingsRepository 新增 6 键 Flow/setter + glassStateFlow 聚合
T4   SettingsViewModel 新增 glassState 聚合与节流写入方法
T5   数据层单元测试（键默认值/容错解析/半径换算）
T6   GlassPanel.kt glassPanel Modifier（三档效果链 + onDrawSurface 染色 + API33 门槛）
T7   WaterWaveShader.kt 水波 AGSL（静态水纹 + 时间驱动动画 + 构造异常回退）
T8   M3 冒烟编译 ./gradlew :app:assembleUniversalDebug
T9   MainActivity 壁纸层 Modifier.backdrop() 源标记 + Backdrop scope + CompositionLocalProvider
T10  侧边栏容器接线 glassPanel（区域开关短路）
T11  输入框容器接线 glassPanel（区域开关短路）
T12  内容面板容器接线 glassPanel（区域开关短路）
T13  M4 编译验证 ./gradlew :app:assembleUniversalDebug
T14  BackgroundImageSheet 扩展：总开关 + 档位 SegmentedTabs + 半径滑条升级
T15  BackgroundImageSheet 扩展：三区域开关 + 水波动画开关（water 档显示）+ API<33 禁用提示
T16  双语 strings.xml 新增玻璃设置文案（中英同步）
T17  M5 编译验证 ./gradlew :app:assembleUniversalDebug
T18  水波动画生命周期停帧（不可见/后台无动画帧）
T19  全量单测 ./gradlew :app:testUniversalDebugUnitTest
T20  真机验证包准备（assembleUniversalDebug APK + 验证清单：三档/三区域/动画/半径/深浅主题/重启保留/API26-30 现状一致）
T21  文档同步 docs-site/docs/（背景设置文档更新玻璃材质章节 + config.ts 侧栏核对）
T22  prompts 同步检查（设置项变化核对 assets/prompts/ 对应文件）
T23  汇报 + 提交（Conventional Commits，逐里程碑分批 commit）
```

## 里程碑

| 里程碑 | 任务 | 检查点 |
| --- | --- | --- |
| M1 基础 | T1-T2 | T1 依赖解析成功（库兼容性检查点：minSdk 21 与项目 26 无冲突，Compose BOM 2026.01 兼容） |
| M2 数据层 | T3-T5 | T5 单测通过 |
| M3 渲染核心 | T6-T8 | T8 冒烟编译通过 |
| M4 三区域接线 | T9-T13 | T13 编译通过 |
| M5 设置 UI | T14-T17 | T17 编译通过 + 双语键齐备 |
| M6 收尾 | T18-T23 | T19 全量单测通过；T20 真机验证通过后才进入 T21 |

## 关键约束提醒

- 冒烟编译只用 `:app:assembleUniversalDebug`，禁用聚合任务
- 提交信息遵循 Conventional Commits（scope 建议 `ui` / `settings` / `core`）
- API < 33 路径行为必须与现状完全一致（回归红线）
- 用户可见文案禁用 .kt 硬编码中文，一律双语 strings.xml
