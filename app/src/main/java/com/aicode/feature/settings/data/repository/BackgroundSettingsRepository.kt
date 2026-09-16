package com.aicode.feature.settings.data.repository

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aicode.core.ui.glass.GlassMode
import com.aicode.core.ui.glass.GlassPanelArea
import com.aicode.core.ui.glass.GlassSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backgroundDataStore by preferencesDataStore(name = "background_prefs")

/**
 * 全局自定义背景图：图片文件拷贝到应用私有目录持久化（不依赖 content URI 授权），
 * 透明度与图片路径存 DataStore。路径为 null 表示未设置背景。
 */
@Singleton
class BackgroundSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        const val DEFAULT_ALPHA = 0.15f
        /** 顶层水印模式下透明度上限：超过会盖住文字影响可读性。 */
        const val MAX_ALPHA = 0.2f
        /** 最小透明度：0 表示完全透明（背景不可见）。 */
        const val MIN_ALPHA = 0f
        private val IMAGE_PATH_KEY = stringPreferencesKey("background_image_path")
        private val ALPHA_KEY = floatPreferencesKey("background_alpha")
        private val FROST_INTENSITY_KEY = floatPreferencesKey("frost_intensity")
        const val DEFAULT_FROST_INTENSITY = 0.5f
        const val MIN_FROST_INTENSITY = 0f
        const val MAX_FROST_INTENSITY = 1f
        private val GLASS_ENABLED_KEY = booleanPreferencesKey("glass_enabled")
        private val GLASS_MODE_KEY = stringPreferencesKey("glass_mode")
        private val GLASS_SIDEBAR_ENABLED_KEY = booleanPreferencesKey("glass_sidebar_enabled")
        private val GLASS_INPUT_ENABLED_KEY = booleanPreferencesKey("glass_input_enabled")
        private val GLASS_CONTENT_ENABLED_KEY = booleanPreferencesKey("glass_content_enabled")
        private val WATER_WAVE_ANIMATED_KEY = booleanPreferencesKey("water_wave_animated")

        const val MODE_FROSTED = "frosted"
        const val MODE_WATER = "water"
        const val MODE_LIQUID = "liquid"

        /** 未知值与未设置一律回退磨砂档。 */
        fun parseGlassMode(raw: String?): GlassMode = when (raw) {
            MODE_WATER -> GlassMode.WATER
            MODE_LIQUID -> GlassMode.LIQUID
            else -> GlassMode.FROSTED
        }

        fun glassModeToRaw(mode: GlassMode): String = when (mode) {
            GlassMode.FROSTED -> MODE_FROSTED
            GlassMode.WATER -> MODE_WATER
            GlassMode.LIQUID -> MODE_LIQUID
        }

        /** 把 UI 百分比（0~100）线性映射为实际透明度（0~MAX_ALPHA）。 */
        fun sliderToAlpha(percent: Float): Float =
            MAX_ALPHA * (percent / 100f).coerceIn(0f, 1f)

        /** 把实际透明度反映射为 UI 百分比（0~100）。 */
        fun alphaToSlider(alpha: Float): Float =
            (alpha / MAX_ALPHA).coerceIn(0f, 1f) * 100f

        fun frostToSlider(intensity: Float): Float =
            intensity.coerceIn(MIN_FROST_INTENSITY, MAX_FROST_INTENSITY) * 100f

        fun sliderToFrost(percent: Float): Float =
            (percent / 100f).coerceIn(MIN_FROST_INTENSITY, MAX_FROST_INTENSITY)
    }

    /** 当前背景图文件绝对路径；null 表示未设置。 */
    val imagePathFlow: Flow<String?> = context.backgroundDataStore.data.map { it[IMAGE_PATH_KEY] }

    /** 背景图不透明度（0.05~0.2，顶层水印保证文字可读）。 */
    val alphaFlow: Flow<Float> = context.backgroundDataStore.data.map {
        (it[ALPHA_KEY] ?: DEFAULT_ALPHA).coerceIn(MIN_ALPHA, MAX_ALPHA)
    }

    val frostIntensityFlow: Flow<Float> = context.backgroundDataStore.data.map {
        (it[FROST_INTENSITY_KEY] ?: DEFAULT_FROST_INTENSITY)
            .coerceIn(MIN_FROST_INTENSITY, MAX_FROST_INTENSITY)
    }

    val glassEnabledFlow: Flow<Boolean> = context.backgroundDataStore.data.map {
        it[GLASS_ENABLED_KEY] ?: false
    }

    val glassModeFlow: Flow<GlassMode> = context.backgroundDataStore.data.map {
        parseGlassMode(it[GLASS_MODE_KEY])
    }

    val glassSidebarEnabledFlow: Flow<Boolean> = context.backgroundDataStore.data.map {
        it[GLASS_SIDEBAR_ENABLED_KEY] ?: true
    }

    val glassInputEnabledFlow: Flow<Boolean> = context.backgroundDataStore.data.map {
        it[GLASS_INPUT_ENABLED_KEY] ?: true
    }

    val glassContentEnabledFlow: Flow<Boolean> = context.backgroundDataStore.data.map {
        it[GLASS_CONTENT_ENABLED_KEY] ?: true
    }

    val waterWaveAnimatedFlow: Flow<Boolean> = context.backgroundDataStore.data.map {
        it[WATER_WAVE_ANIMATED_KEY] ?: false
    }

    /** 玻璃配置聚合流：半径 = frost_intensity × 32dp，三档共用同一换算点。 */
    val glassStateFlow: Flow<GlassSettings> =
        combine(
            combine(
                glassEnabledFlow,
                glassModeFlow,
                glassSidebarEnabledFlow,
                glassInputEnabledFlow,
                glassContentEnabledFlow,
            ) { enabled, mode, sidebar, input, content ->
                GlassPrefs(enabled, mode, sidebar, input, content)
            },
            combine(frostIntensityFlow, waterWaveAnimatedFlow, ::Pair),
        ) { core, (frost, animated) ->
            GlassSettings(
                enabled = core.enabled,
                mode = core.mode,
                sidebarEnabled = core.sidebar,
                inputEnabled = core.input,
                contentEnabled = core.content,
                radiusDp = frost * GlassSettings.MAX_RADIUS_DP,
                waterWaveAnimated = animated,
            )
        }

    /**
     * 选择新背景图：把 [uri] 拷贝到私有目录 backgrounds/ 后替换旧图。
     * 先写新文件成功再更新 DataStore，失败时旧背景保持不变。
     */
    suspend fun setBackgroundImage(uri: Uri) {
        val extension = extensionFor(uri)
        val target = File(context.filesDir, "backgrounds/bg_${System.currentTimeMillis()}.$extension")
        target.parentFile?.mkdirs()
        val copied = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target.length() > 0
            }.getOrDefault(false)
        }
        if (!copied) return
        val oldPath = context.backgroundDataStore.data.first()[IMAGE_PATH_KEY]
        context.backgroundDataStore.edit { it[IMAGE_PATH_KEY] = target.absolutePath }
        oldPath?.let { runCatching { File(it).delete() } }
    }

    /** 移除背景：删图片文件并清路径。 */
    suspend fun clearBackground() {
        val oldPath = context.backgroundDataStore.data.first()[IMAGE_PATH_KEY]
        context.backgroundDataStore.edit { it.remove(IMAGE_PATH_KEY) }
        oldPath?.let { runCatching { File(it).delete() } }
    }

    suspend fun setBackgroundAlpha(alpha: Float) {
        context.backgroundDataStore.edit {
            it[ALPHA_KEY] = alpha.coerceIn(MIN_ALPHA, MAX_ALPHA)
        }
    }

    suspend fun setFrostIntensity(intensity: Float) {
        context.backgroundDataStore.edit {
            it[FROST_INTENSITY_KEY] =
                intensity.coerceIn(MIN_FROST_INTENSITY, MAX_FROST_INTENSITY)
        }
    }

    suspend fun setGlassEnabled(enabled: Boolean) {
        context.backgroundDataStore.edit { it[GLASS_ENABLED_KEY] = enabled }
    }

    suspend fun setGlassMode(mode: GlassMode) {
        context.backgroundDataStore.edit { it[GLASS_MODE_KEY] = glassModeToRaw(mode) }
    }

    suspend fun setGlassPanelAreaEnabled(
        area: GlassPanelArea,
        enabled: Boolean,
    ) {
        context.backgroundDataStore.edit {
            when (area) {
                GlassPanelArea.SIDEBAR -> it[GLASS_SIDEBAR_ENABLED_KEY] = enabled
                GlassPanelArea.INPUT -> it[GLASS_INPUT_ENABLED_KEY] = enabled
                GlassPanelArea.CONTENT -> it[GLASS_CONTENT_ENABLED_KEY] = enabled
            }
        }
    }

    suspend fun setWaterWaveAnimated(animated: Boolean) {
        context.backgroundDataStore.edit { it[WATER_WAVE_ANIMATED_KEY] = animated }
    }

    private data class GlassPrefs(
        val enabled: Boolean,
        val mode: GlassMode,
        val sidebar: Boolean,
        val input: Boolean,
        val content: Boolean,
    )

    private fun extensionFor(uri: Uri): String = when (context.contentResolver.getType(uri)) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
}
