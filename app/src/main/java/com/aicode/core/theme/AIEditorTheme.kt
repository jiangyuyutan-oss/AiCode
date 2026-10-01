package com.aicode.core.theme

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Radius {
    val xs = 6.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 18.dp
    val xl = 22.dp
    val xxl = 28.dp
    val pill = 999.dp
}

/**
 * 品牌色对齐 HarmonyOS 设计规范：强调蓝 `#0A59F7` 为主色，Sky 为深一档，
 * 通知橙对齐系统提醒色，图标灰采用鸿蒙中性冷灰。
 * 参考 HarmonyOS 应用界面设计「色彩」章节的系统色板。
 */
object Brand {
    val Blue = Color(0xFF0A59F7)
    val Sky = Color(0xFF317AF7)
    val IconGray = Color(0xFF8A8E99)
    val Orange = Color(0xFFFF9600)
}

/**
 * 统一动效曲线与时长，对齐 HarmonyOS「动效」：位移走减速贝塞尔（前快后缓落位），
 * 强调交互用弹性收尾。集中定义避免各组件 tween 参数各写一套、节奏不齐。
 */
object Motion {
    // 鸿蒙位移曲线：起步快、落位缓，等价 CubicBezier(0.25, 0.1, 0.25, 1) 的减速族。
    val Decelerate = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    // 强调/收尾曲线：略带回弹感。
    val Emphasized = CubicBezierEasing(0.4f, 0.48f, 0.3f, 1.0f)

    const val QUICK_MS = 140
    const val STANDARD_MS = 240
    const val SMOOTH_MS = 320
    const val PAGE_MS = 300
}

/** 全局统一语义色彩，解决业务代码私自 hardcode 颜色问题。 */
data class AppSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val diffAdd: Color,
    val diffAddBg: Color,
    val diffRemove: Color,
    val diffRemoveBg: Color,
    val subtleText: Color,
    val subtleBorder: Color,
    val cardSurface: Color,
    val pageBackground: Color,
    val mutedSurface: Color,
    val capsuleSurface: Color,
    val buttonMutedBg: Color
)

val LightSemanticColors = AppSemanticColors(
    success = Color(0xFF22C55E),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFDCFCE7),
    onSuccessContainer = Color(0xFF15803D),
    warning = Color(0xFFF59E0B),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFEF3C7),
    onWarningContainer = Color(0xFF92400E),
    info = Color(0xFF0284C7),
    onInfo = Color(0xFFFFFFFF),
    infoContainer = Color(0xFFE0F2FE),
    onInfoContainer = Color(0xFF075985),
    diffAdd = Color(0xFF22C55E),
    diffAddBg = Color(0x2622C55E),
    diffRemove = Color(0xFFEF4444),
    diffRemoveBg = Color(0x26EF4444),
    subtleText = Color(0xFF8A8E99),
    subtleBorder = Color(0xFFDDE0E5),
    cardSurface = Color(0xFFFFFFFF),
    pageBackground = Color(0xFFF1F3F5),
    mutedSurface = Color(0xFFEBEEF2),
    capsuleSurface = Color(0xFFE4E7EC),
    buttonMutedBg = Color(0xFFF2F3F5)
)

val DarkSemanticColors = AppSemanticColors(
    success = Color(0xFF4ADE80),
    onSuccess = Color(0xFF052E16),
    successContainer = Color(0x59052E16),
    onSuccessContainer = Color(0xFFBBF7D0),
    warning = Color(0xFFFBBF24),
    onWarning = Color(0xFF78350F),
    warningContainer = Color(0x5978350F),
    onWarningContainer = Color(0xFFFDE68A),
    info = Color(0xFF38BDF8),
    onInfo = Color(0xFF0C4A6E),
    infoContainer = Color(0x590C4A6E),
    onInfoContainer = Color(0xFFBAE6FD),
    diffAdd = Color(0xFF4ADE80),
    diffAddBg = Color(0x334ADE80),
    diffRemove = Color(0xFFF87171),
    diffRemoveBg = Color(0x33F87171),
    subtleText = Color(0xFF8A8E99),
    subtleBorder = Color(0xFF33373D),
    cardSurface = Color(0xFF16181B),
    pageBackground = Color(0xFF0B0C0E),
    mutedSurface = Color(0xFF222529),
    capsuleSurface = Color(0xFF2B2F34),
    buttonMutedBg = Color(0xFF2B2F34)
)

val LocalAppSemanticColors = staticCompositionLocalOf { LightSemanticColors }

val MaterialTheme.semanticColors: AppSemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppSemanticColors.current

/** Git 模块统一 8 色泳道调色板 */
object GitLanePalette {
    val colors = listOf(
        Color(0xFF0A59F7), // 1. 鸿蒙蓝
        Color(0xFF16A34A), // 2. 翠绿
        Color(0xFFF59E0B), // 3. 琥珀
        Color(0xFF8B5CF6), // 4. 优雅紫
        Color(0xFF06B6D4), // 5. 青蓝
        Color(0xFFEF4444), // 6. 珊瑚红
        Color(0xFFEC4899), // 7. 亮粉
        Color(0xFFEAB308)  // 8. 柠檬金
    )
}

/** Git 状态徽章统一色彩映射 */
object GitStatusColors {
    val Added = Color(0xFF16A34A)
    val Modified = Color(0xFFD97706)
    val Deleted = Color(0xFFDC2626)
    val Renamed = Color(0xFF0A59F7)
    val Untracked = Color(0xFF94A3B8)
    val Conflict = Color(0xFF9333EA)
    val TypeChanged = Color(0xFF0891B2)
    val Default = Color(0xFF64748B)
}

/** 日志级别统一色彩映射 */
object LogLevelColors {
    val Verbose = Color(0xFF94A3B8)
    val Debug = Color(0xFF3B82F6)
    val Info = Color(0xFF22C55E)
    val Warn = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)
    val None = Color(0xFF64748B)
}

/** Token / 统计图表统一调色板 */
object TokenStatsPalette {
    val Input = Color(0xFF3B82F6)
    val Output = Color(0xFFF59E0B)
    val Cache = Color(0xFF8B5CF6)
    val Error = Color(0xFFEF4444)
    val Cancelled = Color(0xFF94A3B8)
    val Progress = Color(0xFF22C55E)
}

/** 存储空间页各占用分类的色标（堆叠条与行前色点共用）。 */
object StorageUsagePalette {
    val Chat = Color(0xFF3B82F6)
    val Container = Color(0xFF8B5CF6)
    val ContainerImages = Color(0xFFA78BFA)
    val Workspaces = Color(0xFF22C55E)
    val AiConfig = Color(0xFF14B8A6)
    val Checkpoints = Color(0xFFF59E0B)
    val Logs = Color(0xFFF97316)
    val Caches = Color(0xFF94A3B8)
    val OtherData = Color(0xFFCBD5E1)
    val Apk = Color(0xFF64748B)
}

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF317AF7),
    onPrimary = Color(0xFF061A40),
    primaryContainer = Color(0xFF13294B),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF4E8CFF),
    onSecondary = Color(0xFF061A40),
    secondaryContainer = Color(0xFF16305C),
    onSecondaryContainer = Color(0xFFD6E4FF),
    tertiary = Color(0xFF22C55E),
    tertiaryContainer = Color(0xFF14532D),
    onTertiaryContainer = Color(0xFFBBF7D0),
    background = Color(0xFF0B0C0E),
    onBackground = Color(0xFFF0F1F3),
    surface = Color(0xFF16181B),
    onSurface = Color(0xFFF0F1F3),
    surfaceVariant = Color(0xFF222529),
    onSurfaceVariant = Color(0xFFB8BCC4),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF070809),
    surfaceContainerLow = Color(0xFF0B0C0E),
    surfaceContainer = Color(0xFF16181B),
    surfaceContainerHigh = Color(0xFF222529),
    surfaceContainerHighest = Color(0xFF2B2F34),
    surfaceBright = Color(0xFF3A3E44),
    surfaceDim = Color(0xFF0B0C0E),
    outline = Color(0xFF6B7078),
    outlineVariant = Color(0xFF33373D),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA)
)

internal val LightColorScheme = lightColorScheme(
    primary = Brand.Blue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9F1FF),
    onPrimaryContainer = Color(0xFF0A3A9B),
    secondary = Brand.Sky,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9F1FF),
    onSecondaryContainer = Color(0xFF0A59F7),
    tertiary = Color(0xFF16A34A),
    tertiaryContainer = Color(0xFFDCFCE7),
    onTertiaryContainer = Color(0xFF15803D),
    background = Color(0xFFF1F3F5),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFEBEEF2),
    onSurfaceVariant = Color(0xFF5A5E66),
    surfaceTint = Color.White,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color(0xFFF1F3F5),
    surfaceContainerHigh = Color(0xFFEAEDF1),
    surfaceContainerHighest = Color(0xFFE4E7EC),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE8EAED),
    outline = Color(0xFFC9CDD4),
    outlineVariant = Color(0xFFDDE0E5),
    error = Color(0xFFDC2626),

    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

/**
 * 应用字体族接线点。
 *
 * 鸿蒙设计规范使用 HarmonyOS Sans。字体 `.ttf` 为受版权保护的二进制资产且需从华为官网
 * 授权页下载，仓库不内置；这里用 `FontFamily.SansSerif` 接线——在华为 / EMUI / HarmonyOS
 * 设备上系统无衬线字体即 HarmonyOS Sans（自动落到鸿蒙字面），其它设备回退 Roboto。
 * 后续若拿到授权字体文件放入 `res/font/`，把本 val 换成
 * `FontFamily(ResourceFont(R.font.harmonyos_sans, ...))` 即可全站生效。
 */
val AppFontFamily: FontFamily = FontFamily.SansSerif

private val AppTypography = Typography().run {
    fun TextStyle.harmony(weight: FontWeight? = null, lh: Int? = null, ls: Int = 0) = copy(
        fontFamily = AppFontFamily,
        letterSpacing = ls.sp,
    ).let { if (weight != null) it.copy(fontWeight = weight) else it }
        .let { if (lh != null) it.copy(lineHeight = lh.sp) else it }

    copy(
        headlineLarge = headlineLarge.harmony(FontWeight.Bold),
        headlineMedium = headlineMedium.harmony(FontWeight.Bold),
        headlineSmall = headlineSmall.harmony(FontWeight.SemiBold, lh = 32),
        titleLarge = titleLarge.harmony(FontWeight.SemiBold, lh = 28),
        titleMedium = titleMedium.harmony(FontWeight.Medium, lh = 24),
        titleSmall = titleSmall.harmony(FontWeight.Medium, lh = 20),
        bodyLarge = bodyLarge.harmony(lh = 26),
        bodyMedium = bodyMedium.harmony(lh = 22),
        bodySmall = bodySmall.harmony(lh = 18),
        labelLarge = labelLarge.harmony(FontWeight.Medium),
        // 鸿蒙辅助文字带轻微正字距，避免窄屏小字粘连。
        labelMedium = labelMedium.harmony(ls = 2),
        labelSmall = labelSmall.harmony(ls = 2)
    )
}

/**
 * @param preset 用户选定的配色主题。
 * @param dynamicColor 是否启用系统莫奈取色；低于 Android 12 时自动回退到 [preset]。
 */
@Composable
fun AIEditorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    preset: AppThemePreset = AppThemePreset.DEFAULT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val themeColors = remember(darkTheme, preset, dynamicColor, context) {
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dynamicScheme =
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            deriveDynamicThemeColors(dynamicScheme, darkTheme)
        } else if (darkTheme) {
            preset.dark
        } else {
            preset.light
        }
    }

    CompositionLocalProvider(LocalAppSemanticColors provides themeColors.semanticColors) {
        MaterialTheme(
            colorScheme = themeColors.colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
