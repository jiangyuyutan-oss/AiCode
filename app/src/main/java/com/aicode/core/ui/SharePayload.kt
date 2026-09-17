package com.aicode.core.ui

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 分享内容注入字符上限，避免超大文本撑爆输入框与草稿持久化。 */
internal const val MAX_SHARE_TEXT_CHARS = 20000

/**
 * 外部 App 经 [Intent.ACTION_SEND] 发来的分享载荷。
 *
 * @property text 纯文本（已截断至上限），可能为空。
 * @property imageUris 图片 Uri 列表（单选场景至多一个）。
 * @property receivedAt 接收时间戳，用于去重与延迟消费判断。
 */
internal data class SharePayload(
    val text: String?,
    val imageUris: List<Uri>,
    val receivedAt: Long,
)

/**
 * 进程级分享载荷中转站：MainActivity 解析 Intent 后写入，AppNavigation / AIChatPanel 观察消费。
 *
 * 用进程级单例而非 CompositionLocal，原因：
 * - 语言切换触发 Activity [recreate] 时 CompositionLocal 会随组合销毁，单例保留待消费的载荷不丢。
 * - AIChatPanel 与 AppNavigation 均可直接访问，无需层层透传。
 */
internal object SharePayloadHolder {
    private val _payload = MutableStateFlow<SharePayload?>(null)
    val payload: StateFlow<SharePayload?> = _payload

    fun set(payload: SharePayload?) {
        _payload.value = payload
    }

    fun consume() {
        _payload.value = null
    }
}

/**
 * 解析 [Intent.ACTION_SEND] 为 [SharePayload]，无效内容返回 null。
 *
 * 纯函数，便于单元测试：传入构造好的 Intent 断言输出。
 */
internal fun parseShareIntent(intent: Intent): SharePayload? {
    if (intent.action != Intent.ACTION_SEND) return null
    val rawText = intent.getStringExtra(Intent.EXTRA_TEXT)
    val text = rawText?.take(MAX_SHARE_TEXT_CHARS)?.takeIf { it.isNotBlank() }
    val uris = mutableListOf<Uri>()
    @Suppress("DEPRECATION")
    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
    intent.clipData?.let { clip ->
        for (i in 0 until clip.itemCount) {
            clip.getItemAt(i).uri?.let { uri ->
                if (uri !in uris) uris.add(uri)
            }
        }
    }
    if (text == null && uris.isEmpty()) return null
    return SharePayload(text = text, imageUris = uris, receivedAt = System.currentTimeMillis())
}
