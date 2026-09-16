package com.aicode.core.ui.glass

import androidx.compose.runtime.staticCompositionLocalOf
import com.kyant.backdrop.Backdrop

/** 玻璃材质三档：磨砂 / 水玻璃（AGSL 水波折射）/ 液体玻璃（透镜折射 + 色散）。 */
enum class GlassMode {
    FROSTED,
    WATER,
    LIQUID,
}

/**
 * 玻璃材质全局配置。radiusDp 由 frost_intensity × 32 换算，三档共用。
 */
data class GlassSettings(
    val enabled: Boolean,
    val mode: GlassMode,
    val sidebarEnabled: Boolean,
    val inputEnabled: Boolean,
    val contentEnabled: Boolean,
    val radiusDp: Float,
    val waterWaveAnimated: Boolean,
) {
    companion object {
        const val MAX_RADIUS_DP = 32f

        /** 总开关关闭时的默认态：区域开关保持用户选择记忆。 */
        val DISABLED = GlassSettings(
            enabled = false,
            mode = GlassMode.FROSTED,
            sidebarEnabled = true,
            inputEnabled = true,
            contentEnabled = true,
            radiusDp = 16f,
            waterWaveAnimated = false,
        )
    }
}

/** 玻璃面板三大应用区域。 */
enum class GlassPanelArea { SIDEBAR, INPUT, CONTENT }

/** 区域开关读取。 */
fun GlassSettings.isEnabled(area: GlassPanelArea): Boolean = when (area) {
    GlassPanelArea.SIDEBAR -> sidebarEnabled
    GlassPanelArea.INPUT -> inputEnabled
    GlassPanelArea.CONTENT -> contentEnabled
}

/** 全局玻璃配置注入点；未提供时视为关闭态，玻璃面板全部透传。 */
val LocalGlassSettings = staticCompositionLocalOf { GlassSettings.DISABLED }

/**
 * Backdrop 源注入点：MainActivity 用 [com.kyant.backdrop.backdrops.rememberLayerBackdrop]
 * 标记壁纸层后在此提供；null（未接线）时玻璃面板透传。
 */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }
