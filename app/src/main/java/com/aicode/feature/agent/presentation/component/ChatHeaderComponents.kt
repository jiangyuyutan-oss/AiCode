package com.aicode.feature.agent.presentation.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicode.R
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.feature.agent.domain.model.AgentMode
import com.aicode.feature.onboarding.domain.OnboardingStep
import com.aicode.feature.onboarding.presentation.onboardingTarget
import com.aicode.feature.settings.presentation.component.ModelLogoIcon
import compose.icons.FeatherIcons
import compose.icons.feathericons.GitBranch
import compose.icons.feathericons.Menu
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Search
import compose.icons.feathericons.Terminal

@Composable
internal fun ChatHeader(
    sessionTitle: String,
    modelName: String?,
    inputTokens: Int,
    outputTokens: Int,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onToggleSearch: () -> Unit,
    searchActive: Boolean = false,
    onNavigateToTerminal: () -> Unit,
    onNavigateToGit: () -> Unit,
    currentMode: AgentMode,
    onToggleMode: (AgentMode) -> Unit,
    connectionState: com.aicode.feature.agent.domain.container.ConnectionState? = null,
    showMenuButton: Boolean = true,
    terminalActive: Boolean = false,
    gitActive: Boolean = false,
    contextUsedTokens: Int = 0,
    contextMaxTokens: Int = 0
) {
    Surface(
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 大屏常驻侧栏时隐掉汉堡键：侧栏已经摆在左边，再给个开关反而困惑。
                if (showMenuButton) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.onboardingTarget(OnboardingStep.OPEN_SIDEBAR)
                    ) {
                        Icon(
                            FeatherIcons.Menu,
                            contentDescription = stringResource(R.string.chat_open_sidebar),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Spacer(modifier = Modifier.width(Spacing.sm))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sessionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        if (!modelName.isNullOrBlank()) {
                            ModelLogoIcon(modelName = modelName, size = 14.dp)
                        }
                        Text(
                            text = modelName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_no_model_selected),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                WorkbenchIconButton(
                    icon = FeatherIcons.Search,
                    contentDescription = stringResource(R.string.common_search),
                    active = searchActive,
                    onClick = onToggleSearch
                )
                IconButton(onClick = onNewChat) {
                    Icon(
                        FeatherIcons.Plus,
                        contentDescription = stringResource(R.string.chat_new_session),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                WorkbenchIconButton(
                    icon = FeatherIcons.GitBranch,
                    contentDescription = stringResource(R.string.chat_open_git),
                    active = gitActive,
                    onClick = onNavigateToGit
                )
                WorkbenchIconButton(
                    icon = FeatherIcons.Terminal,
                    contentDescription = stringResource(R.string.chat_open_terminal),
                    active = terminalActive,
                    onClick = onNavigateToTerminal
                )
                if (contextMaxTokens > 0) {
                    ContextUsageCapsule(
                        usedTokens = contextUsedTokens,
                        maxTokens = contextMaxTokens
                    )
                }
            }
            // 远程模式：左边 SSH 连接状态，右边 token 累计统计
            if (connectionState != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ConnectionIndicator(state = connectionState)
                    TokenStats(
                        inputTokens = inputTokens,
                        outputTokens = outputTokens
                    )
                }
            }
        }
    }
}

/** 顶栏工作台入口按钮：大屏右栏开着对应内容时高亮，否则看不出点一下是开还是关。 */
@Composable
private fun WorkbenchIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .then(
                    if (active) {
                        Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun ConnectionIndicator(
    state: com.aicode.feature.agent.domain.container.ConnectionState
) {
    val (dotColor, text) = when (state) {
        com.aicode.feature.agent.domain.container.ConnectionState.CONNECTED ->
            MaterialTheme.colorScheme.primary to stringResource(R.string.chat_ssh_connected)
        com.aicode.feature.agent.domain.container.ConnectionState.CONNECTING ->
            MaterialTheme.colorScheme.tertiary to stringResource(R.string.chat_ssh_connecting)
        com.aicode.feature.agent.domain.container.ConnectionState.FAILED ->
            MaterialTheme.colorScheme.error to stringResource(R.string.chat_ssh_failed)
        com.aicode.feature.agent.domain.container.ConnectionState.DISCONNECTED ->
            MaterialTheme.colorScheme.outline to stringResource(R.string.chat_ssh_disconnected)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TokenStats(inputTokens: Int, outputTokens: Int) {
    val inStr = formatTokenCount(inputTokens.toLong())
    val outStr = formatTokenCount(outputTokens.toLong())
    Text(
        text = "↑$inStr ↓$outStr",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun RemoteConnectingPlaceholder(
    state: com.aicode.feature.agent.domain.container.ConnectionState
) {
    val text = when (state) {
        com.aicode.feature.agent.domain.container.ConnectionState.CONNECTING -> stringResource(R.string.chat_connecting_remote)
        com.aicode.feature.agent.domain.container.ConnectionState.FAILED -> stringResource(R.string.chat_remote_connect_failed)
        com.aicode.feature.agent.domain.container.ConnectionState.DISCONNECTED -> stringResource(R.string.chat_no_remote_connection)
        com.aicode.feature.agent.domain.container.ConnectionState.CONNECTED -> ""
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (state == com.aicode.feature.agent.domain.container.ConnectionState.CONNECTING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun WelcomeState(modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier.padding(Spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            // 底部悬浮输入框占据大量空间，纯居中会显得偏下；整体上移 13% 屏高，让重心落在顶栏与输入框之间的空白正中
            modifier = Modifier.offset(y = -(maxHeight * 0.13f))
        ) {
            Text(
                text = stringResource(R.string.chat_placeholder),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.chat_input_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 上下文占用胶囊：圆环进度（按占用率变色）+ 已用/最大 token 文本。
 * 占用率 = 最近一次请求的 inputTokens / 模型 contextTokens（当前上下文窗口占用，非累计消耗）。
 */
@Composable
private fun ContextUsageCapsule(usedTokens: Int, maxTokens: Int) {
    val progress = if (maxTokens > 0) (usedTokens.toFloat() / maxTokens).coerceIn(0f, 1f) else 0f
    val color = when {
        progress >= 0.9f -> MaterialTheme.colorScheme.error
        progress >= 0.7f -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.primary
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier
            .background(trackColor.copy(alpha = 0.6f), RoundedCornerShape(Radius.pill))
            .clip(RoundedCornerShape(Radius.pill))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        ContextUsageRing(progress = progress, color = color, trackColor = trackColor, modifier = Modifier.size(16.dp))
        Text(
            text = "${formatTokenCount(usedTokens.toLong())}/${formatTokenCount(maxTokens.toLong())}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun ContextUsageRing(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val stroke = size.minDimension / 8f
        val inset = stroke / 2f
        val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        if (progress > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}