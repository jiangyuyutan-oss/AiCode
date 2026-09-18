# Requirements Document

## Introduction

AI 在容器里跑起 Web 服务后，手机上没法访问容器内 localhost 端口，全栈开发闭环断在最后一步。PRoot 容器与宿主共享网络命名空间，容器内绑定端口的服务在 App 内可直接以 `127.0.0.1:<port>` 访问——本功能用内嵌 WebView 提供预览。

## Requirements

1. WHEN 用户处于本地容器模式并点击终端页顶栏「预览」按钮，AiCode SHALL 弹出端口输入对话框。
2. WHEN 用户输入合法端口（1-65535）并确认，AiCode SHALL 以全屏内嵌 WebView 打开 `http://127.0.0.1:<port>`。
3. 预览页 SHALL 提供关闭、重载，以及转系统浏览器打开的入口。
4. WHEN 用户处于远程 SSH 模式点击「预览」，AiCode SHALL 提示「预览仅适用于本地容器模式」且不打开对话框。
5. 端口输入 SHALL 仅允许数字并限制 5 位，非法端口禁用确认按钮。
6. 相关文案 SHALL 走双语 strings.xml（`terminal_preview_*`）。
