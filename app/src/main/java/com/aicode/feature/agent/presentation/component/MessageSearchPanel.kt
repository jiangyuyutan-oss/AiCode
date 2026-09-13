package com.aicode.feature.agent.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicode.core.theme.Spacing
import com.aicode.core.ui.AppTextField
import com.aicode.feature.agent.presentation.MessageRole
import com.aicode.feature.agent.presentation.MessageSearchResult
import com.aicode.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 会话内消息搜索面板：顶栏搜索按钮开关，悬浮在消息列表上方。
 * 结果按时间倒序（至多 50 条），点击请求跳转定位。
 */
@Composable
internal fun MessageSearchPanel(
    query: String,
    results: List<MessageSearchResult>,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onResultClick: (MessageSearchResult) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = stringResource(R.string.chat_search_messages_hint),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            when {
                searching -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.common_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                query.isNotBlank() && results.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.chat_search_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                    )
                }
                else -> {
                    val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        itemsIndexed(items = results, key = { _, result -> result.id }) { _, result ->
                            MessageSearchResultRow(
                                result = result,
                                query = query,
                                timeText = timeFormat.format(Date(result.timestamp)),
                                onClick = { onResultClick(result) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageSearchResultRow(
    result: MessageSearchResult,
    query: String,
    timeText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        val (roleLabel, roleIcon) = when (result.role) {
            MessageRole.USER.name -> stringResource(R.string.chat_search_role_user) to Icons.Filled.Person
            MessageRole.TOOL.name -> stringResource(R.string.chat_search_role_tool) to Icons.Filled.Build
            else -> stringResource(R.string.chat_search_role_ai) to Icons.Filled.AutoAwesome
        }
        Icon(
            roleIcon,
            contentDescription = roleLabel,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = roleLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val highlightColor = MaterialTheme.colorScheme.primary
            val snippet = remember(result.snippet, query, highlightColor) {
                highlightQueryInSnippet(result.snippet, query, highlightColor)
            }
            Text(
                text = snippet,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 片段内高亮全部命中词（忽略大小写）：主色加粗，其余样式保持正文。 */
private fun highlightQueryInSnippet(
    snippet: String,
    query: String,
    highlightColor: Color
): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(snippet)
    return buildAnnotatedString {
        append(snippet)
        var index = snippet.indexOf(query, ignoreCase = true)
        while (index >= 0) {
            addStyle(
                SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold),
                index,
                index + query.length
            )
            index = snippet.indexOf(query, index + query.length, ignoreCase = true)
        }
    }
}
