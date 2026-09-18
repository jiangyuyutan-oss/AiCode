package com.aicode.feature.agent.presentation.component

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 追加听写文本到基线：以空白分隔，保留基线内容，收缩首尾空白。
 * 纯函数，独立于 Android 运行时可测。
 */
internal fun mergeDraft(base: String, recognized: String): String = buildString {
    append(base.trimEnd())
    if (isNotEmpty() && recognized.isNotBlank()) append(' ')
    append(recognized.trim())
}

/**
 * 聊天输入框语音听写控制器：封装 SpeechRecognizer 生命周期与状态，
 * 部分结果经 [onPartialPreview] 实时预览（仅 UI 展示），定稿经 [onFinal] 走草稿落盘链路。
 *
 * @property available 设备是否支持语音识别（false 时点击应提示不支持）。
 */
internal class ChatVoiceInputController(
    context: Context,
    private val onPartialPreview: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (ChatVoiceError) -> Unit,
) {
    enum class ChatVoiceError { NO_MATCH, FAILED }

    val available: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    var isListening by mutableStateOf(false)
        private set

    /** 开始听写时输入框内容快照，识别结果追加在其后。 */
    private var baseText: String = ""

    private val recognizer: SpeechRecognizer? =
        if (available) SpeechRecognizer.createSpeechRecognizer(context) else null

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            isListening = false
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    onError(ChatVoiceError.NO_MATCH)
                else -> onError(ChatVoiceError.FAILED)
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            onFinal(mergeDraft(baseText, text))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isNotBlank()) onPartialPreview(mergeDraft(baseText, text))
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun start(base: String) {
        val sr = recognizer ?: return
        if (isListening) return
        baseText = base
        sr.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // 语言不设 EXTRA_LANGUAGE，跟随系统默认。
        }
        isListening = true
        sr.startListening(intent)
    }

    fun stop() {
        if (!isListening) return
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
    }
}
