package com.aicode.core.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.min

/** 染色表面基准 alpha，保证玻璃面板上文字可读（需求下限 0.3）。 */
private const val TINT_ALPHA = 0.4f

/** 水波折射的坐标偏移幅度。 */
private val WAVE_AMPLITUDE = 6.dp

/**
 * 玻璃面板 Modifier：按 [LocalGlassSettings] 档位渲染磨砂 / 水玻璃 / 液体玻璃材质，
 * backdrop 源从 [LocalBackdrop] 读取（MainActivity 标记壁纸层）。
 *
 * 短路规则：backdrop 未接线、总开关关闭、[area] 区域开关关闭、API < 33 时原样透传。
 * 液体玻璃的效果链顺序遵循库约束 color filter => blur => lens；
 * 水玻璃在 blur 后叠加 [WATER_WAVE_SHADER] 折射，静态水纹为默认，动画仅在水波开关开启时驱动。
 */
@Composable
fun Modifier.glassPanel(
    shape: CornerBasedShape,
    area: GlassPanelArea,
): Modifier {
    val backdrop = LocalBackdrop.current ?: return this
    val settings = LocalGlassSettings.current
    if (!settings.enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    if (!settings.isEnabled(area)) return this

    val density = LocalDensity.current
    val radiusPx = with(density) {
        settings.radiusDp.coerceIn(0f, GlassSettings.MAX_RADIUS_DP).dp.toPx()
    }
    val amplitudePx = with(density) { WAVE_AMPLITUDE.toPx() }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isLight = surfaceColor.luminance() > 0.5f

    val timeState = remember { mutableFloatStateOf(WATER_WAVE_STATIC_TIME) }
    val waveShader = remember { RuntimeShader(WATER_WAVE_SHADER) }
    val animateWave = settings.mode == GlassMode.WATER && settings.waterWaveAnimated
    LaunchedEffect(animateWave) {
        if (animateWave) {
            while (true) {
                withFrameNanos { frameTimeNanos ->
                    timeState.floatValue = (frameTimeNanos % 10_000_000_000L) / 1_000_000_000f
                }
            }
        } else {
            timeState.floatValue = WATER_WAVE_STATIC_TIME
        }
    }

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            when (settings.mode) {
                GlassMode.FROSTED -> blur(radiusPx, TileMode.Clamp)

                GlassMode.WATER -> {
                    blur(radiusPx, TileMode.Clamp)
                    waveShader.setFloatUniform("uTime", timeState.floatValue)
                    waveShader.setFloatUniform("uAmplitude", amplitudePx)
                    effect(
                        RenderEffect.createRuntimeShaderEffect(
                            waveShader,
                            WATER_WAVE_UNIFORM_CONTENT,
                        )
                    )
                }

                GlassMode.LIQUID -> {
                    vibrancy()
                    blur(radiusPx, TileMode.Clamp)
                    val cornerPx = shape.topStart.toPx(size, this)
                    val height = min(cornerPx, radiusPx * 0.5f)
                    lens(
                        refractionHeight = height,
                        refractionAmount = height * 2f,
                        depthEffect = false,
                        chromaticAberration = true,
                    )
                }
            }
        },
        onDrawSurface = {
            drawRect(surfaceColor.copy(alpha = TINT_ALPHA))
            if (settings.mode == GlassMode.LIQUID) {
                val cornerPx = shape.topStart.toPx(size, this)
                drawRoundRect(
                    color = Color.White.copy(alpha = if (isLight) 0.5f else 0.18f),
                    style = Stroke(width = 1.dp.toPx()),
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                )
            }
        },
    )
}
