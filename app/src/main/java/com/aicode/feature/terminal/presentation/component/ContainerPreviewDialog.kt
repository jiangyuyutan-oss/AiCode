package com.aicode.feature.terminal.presentation.component

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aicode.R
import com.aicode.core.ui.AppTextField
import com.aicode.core.ui.rememberImeBottomInset
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.ExternalLink
import compose.icons.feathericons.RefreshCw

private const val DEFAULT_PREVIEW_PORT = 3000
private val PORT_RANGE = 1..65535

/**
 * 容器内 Web 服务预览：输入端口 → 内嵌 WebView 打开 `http://127.0.0.1:<port>`。
 *
 * PRoot 容器与宿主共享网络命名空间，容器内绑定 127.0.0.1 / 0.0.0.0 端口的服务
 * 在 App 内直接以 localhost 访问，无需真实端口转发。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerPreviewDialog(
    onDismiss: () -> Unit,
) {
    var portInput by remember { mutableStateOf(DEFAULT_PREVIEW_PORT.toString()) }
    var browsingPort by remember { mutableIntStateOf(0) }

    if (browsingPort == 0) {
        // 输入态
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.terminal_preview_dialog_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.terminal_preview_dialog_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    AppTextField(
                        value = portInput,
                        onValueChange = { portInput = it.filter { c -> c.isDigit() }.take(5) },
                        label = stringResource(R.string.terminal_preview_port_hint),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = portInput.toIntOrNull()?.let { it in PORT_RANGE } == true,
                    onClick = {
                        val port = portInput.toIntOrNull() ?: return@TextButton
                        browsingPort = port
                    }
                ) { Text(stringResource(R.string.terminal_preview_open)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    } else {
        PreviewWebView(
            port = browsingPort,
            onClose = onDismiss,
        )
    }
}

/** 全屏 WebView 预览页：顶栏（关闭 / 地址）/ 页面本身。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewWebView(
    port: Int,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var reloadKey by remember { mutableIntStateOf(0) }
    val url = "http://127.0.0.1:$port"

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(
                                FeatherIcons.ArrowLeft,
                                contentDescription = stringResource(R.string.common_close)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { reloadKey += 1 }) {
                            Icon(
                                FeatherIcons.RefreshCw,
                                contentDescription = stringResource(R.string.terminal_preview_reload)
                            )
                        }
                        IconButton(onClick = {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url)
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        }) {
                            Icon(
                                FeatherIcons.ExternalLink,
                                contentDescription = stringResource(R.string.terminal_preview_external)
                            )
                        }
                    }
                )
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f).imePadding(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.setSupportZoom(true)
                        }
                    },
                    update = { webView ->
                        webView.stopLoading()
                        webView.loadUrl(url)
                    }
                )
            }
        }
    }
}