# Requirements Document

## Introduction

移动端单手、通勤、走路场景手写需求与代码体验差。本功能为聊天输入框增加系统语音听写（`SpeechRecognizer`）入口：点麦克风开始、原地实时出字、再点停止结束，识别文本追加进输入框待用户确认发送。

## Glossary

- **听写**: SpeechRecognizer 连续识别用户语音并实时产出文本的过程。
- **部分结果**: 识别过程中未定稿的中间文本（`onPartialResults`），用于实时预览。
- **基线文本**: 开始听写时输入框已有的内容，识别结果追加在其后。

## Requirements

### Requirement 1: 启动/停止听写

**User Story:** AS 开发者，I want 点一下麦克风开始说话再点一下结束，so that 手不动键盘也能把需求说进输入框。

#### Acceptance Criteria

1. WHEN 用户点击麦克风按钮且已授予 RECORD_AUDIO 权限，AiCode SHALL 启动 SpeechRecognizer 连续听写。
2. WHEN 听写进行中用户再次点击该按钮，AiCode SHALL 停止听写并把最终识别文本追加到基线文本之后。
3. WHILE 听写进行中，AiCode SHALL 把部分结果实时追加在基线文本后展示于输入框，停止时以最终结果替换部分结果。

### Requirement 2: 权限与可用性

**User Story:** AS 开发者，I want 首次用语音时 App 自己处理权限，so that 流程不被打断。

#### Acceptance Criteria

1. IF RECORD_AUDIO 未授予，WHEN 用户点击麦克风，AiCode SHALL 先发起运行时权限申请，授予后自动开始听写。
2. IF 用户拒绝权限，AiCode SHALL 展示提示文案且保持输入框内容不变。
3. IF 设备 SpeechRecognizer 不可用（`isRecognitionAvailable == false`），WHEN 用户点击麦克风，AiCode SHALL 展示不支持提示且不崩溃。

### Requirement 3: 文本合并与保留

#### Acceptance Criteria

1. WHEN 听写结束产出文本，AiCode SHALL 以空白分隔把识别文本追加到基线文本末尾，保留用户已输入内容。
2. AiCode SHALL 覆写前保留基线文本，听写期间用户手动编辑输入框的行为 SHALL 以基线文本 + 最新识别结果为准重新合并。

### Requirement 4: 生命周期与双语文案

#### Acceptance Criteria

1. WHEN 离开聊天页或 Activity 销毁，AiCode SHALL 销毁 SpeechRecognizer 实例，杜绝泄漏。
2. WHEN 展示语音相关提示，AiCode SHALL 从双语 `strings.xml` 引用 `chat_voice_` 前缀文案。
