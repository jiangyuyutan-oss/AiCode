---
name: "setup-opencode"
description: "用户要求安装配置 opencode CLI、运行 /setup-opencode、或提到「检测安装 OpenCode CI」「把当前模型配置给 opencode」时使用。检测容器内 opencode 是否可用，未安装则安装，然后读取 App 导出的 provider 配置文件生成 opencode 配置并验证。Use when the user asks to set up opencode CLI in Bentley (the local container), install OpenCode CI, or migrate the current app model provider into opencode config."
---

## OpenCode CLI 安装配置（Setup OpenCode）

在 Bentley（本 app 的 PRoot 容器）内检测并安装 opencode CLI，然后把 App 当前使用的模型 provider 迁移为 opencode 配置。

## 工作流

### 第一步：检测 opencode 是否可用

```bash
opencode --version
```

- 命令存在且输出版本号：跳到第三步（已安装，无需重复安装）。
- 提示 command not found：继续第二步安装。

### 第二步：安装 opencode

按顺序尝试，任一成功即止：

1. 官方安装脚本（首选）：

```bash
curl -fsSL https://opencode.ai/install | bash
```

安装脚本把二进制放到 `$HOME/bin` 或 `$HOME/.opencode/bin`，装完后确认 `ls $HOME/bin/opencode $HOME/.opencode/bin/opencode 2>/dev/null` 找到实际路径；若不在 PATH 里，后续命令用绝对路径调用，并提示用户把它加进 `~/.bashrc` 的 PATH。

2. 官方脚本失败（网络不通等）再试 npm：

```bash
npm i -g opencode-ai@latest
```

3. 两者都失败：报告失败原因（网络代理、磁盘空间等），停止并给出排查建议，不强行继续。

安装完成后再次运行 `opencode --version` 确认可用。

### 第三步：读取 App 导出的 provider 配置

App 已把当前生效的模型 provider 导出到 `~/.aicode/opencode-provider.json`（执行本技能前刚生成）。用 `readFile` 读取，字段：

```json
{
  "providerName": "提供商显示名",
  "providerType": "OPENAI | ANTHROPIC | GEMINI",
  "baseUrl": "接口地址",
  "apiKey": "密钥",
  "model": "模型 id"
}
```

按 providerType 选择 npm 适配包：

- `OPENAI` → `@ai-sdk/openai-compatible`（任意兼容接口通用，baseUrl 通常是 `https://xxx/v1`）
- `ANTHROPIC` → `@ai-sdk/anthropic`
- `GEMINI` → `@ai-sdk/google`

文件不存在或字段缺失时：提示用户先在 App 设置里配置好 provider 并执行 `/setup-opencode`，停止。

### 第四步：生成 opencode 配置

先读现有的 `~/.config/opencode/opencode.json`（存在则解析保留其它字段，仅更新 provider 部分；不存在或解析失败则新建）。

写入/合并配置，示例（占位值用第三步读到的实际值替换，provider id 用 `aicode`）：

```json
{
  "$schema": "https://opencode.ai/config.json",
  "provider": {
    "aicode": {
      "npm": "@ai-sdk/openai-compatible",
      "name": "（providerName）",
      "options": {
        "baseURL": "（baseUrl）",
        "apiKey": "（apiKey）"
      },
      "models": {
        "（model）": { "name": "（model）" }
      }
    }
  },
  "model": "aicode/（model）"
}
```

注意：
- 保持 JSON 合法：无注释、无尾逗号。
- 若原配置已有 `provider.aicode`，整体替换该子对象；其它 provider 与顶层字段原样保留。
- 自定义请求头需求不存在时可忽略 headers。
- **不要把 apiKey 的值打印、复述或写进对话**，只写进配置文件；总结时用「已配置」代替密钥本身。

### 第五步：验证

```bash
opencode run "回复 OK" 2>&1 | tail -20
```

- 输出包含模型回复：报告安装配置成功，附简短总结（安装方式、provider 名、模型、配置文件路径）。
- 报错：读错误信息定位常见问题——baseUrl 缺 `/v1`、key 无效、模型名不存在——修正配置后重试一次；仍失败则报告错误原文与已尝试的修复，停止。

## 边界

- 全程在容器内执行，不涉及宿主 Android 系统。
- opencode 与本 app 是独立工具：此配置只让 opencode CLI 能用同一个 provider，不影响 app 自身对话。
- 不要卸载或升级容器内已有 node/npm 等基础件；安装失败时优先换安装渠道而非改系统。
