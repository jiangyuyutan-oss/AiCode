package com.aicode.feature.settings.presentation.component

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicode.R
import com.aicode.core.theme.Spacing
import com.aicode.core.ui.AppSwitch
import com.aicode.core.ui.SegmentedTabs
import com.aicode.core.ui.glass.GlassMode
import com.aicode.core.ui.glass.GlassPanelArea
import com.aicode.core.ui.glass.GlassSettings
import com.aicode.feature.settings.data.repository.BackgroundSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 按目标尺寸采样解码背景图（inSampleSize 2 的幂缩放，控制峰值内存），失败返回 null。
 * 全局背景层与设置弹窗缩略图共用。
 */
internal fun decodeBackgroundBitmap(path: String, maxWidth: Int, maxHeight: Int): ImageBitmap? =
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxWidth * 2 || bounds.outHeight / sample > maxHeight * 2) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
    }.getOrNull()

/**
 * 自定义背景图设置弹窗：缩略图预览 + 透明度滑块 + 选图/移除。
 *
 * @param imagePath 当前背景图路径，null 表示未设置。
 * @param alpha 当前透明度（0.05~1.0）。
 * @param onPickImage 用户选定图片后回调。
 * @param onAlphaChange 透明度变化回调（写 DataStore，全局背景随之变化；落盘节流在 ViewModel 侧）。
 * @param onRemove 移除背景回调。
 * @param onDismiss 关闭弹窗。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackgroundImageSheet(
    imagePath: String?,
    alpha: Float,
    frostIntensity: Float,
    onPickImage: (Uri) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onFrostIntensityChange: (Float) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    glassState: GlassSettings = GlassSettings.DISABLED,
    onGlassEnabledChange: (Boolean) -> Unit = {},
    onGlassModeChange: (GlassMode) -> Unit = {},
    onGlassAreaEnabledChange: (GlassPanelArea, Boolean) -> Unit = { _, _ -> },
    onWaterWaveAnimatedChange: (Boolean) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPickImage(uri) }

    // 缩略图预览：按屏幕宽度采样，避免大图全量解码
    val previewBitmap by produceState<ImageBitmap?>(initialValue = null, imagePath) {
        value = imagePath?.let { path ->
            withContext(Dispatchers.IO) {
                decodeBackgroundBitmap(path, 1024, 1024)
            }
        }
    }

    // 滑块位置由本地状态驱动：直接绑外部 alpha 会让每次拖动都走「写 DataStore → 回读 → 整个设置页重组」
    // 的往返，滑块因此跟不上手指。弹窗每次打开都重建，初值取当前设置即可。
    var sliderPercent by remember { mutableFloatStateOf(BackgroundSettingsRepository.alphaToSlider(alpha)) }
    var frostPercent by remember {
        mutableFloatStateOf(BackgroundSettingsRepository.frostToSlider(frostIntensity))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
        ) {
            Text(
                text = stringResource(R.string.settings_background_image),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.md)
            )

            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap!!,
                    contentDescription = stringResource(R.string.settings_background_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(Spacing.lg))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_background_image_alpha),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${sliderPercent.toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Slider(
                value = sliderPercent,
                onValueChange = { percent ->
                    sliderPercent = percent
                    onAlphaChange(BackgroundSettingsRepository.sliderToAlpha(percent))
                },
                valueRange = 0f..100f
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_background_frost_intensity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${frostPercent.toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Slider(
                value = frostPercent,
                onValueChange = { percent ->
                    frostPercent = percent
                    onFrostIntensityChange(BackgroundSettingsRepository.sliderToFrost(percent))
                },
                valueRange = 0f..100f
            )

            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = stringResource(R.string.settings_glass_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.md)
            )
            val glassSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            if (!glassSupported) {
                Text(
                    text = stringResource(R.string.settings_glass_unsupported),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_glass_enabled),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    AppSwitch(
                        checked = glassState.enabled,
                        onCheckedChange = onGlassEnabledChange
                    )
                }
                if (glassState.enabled) {
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = stringResource(R.string.settings_glass_mode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.xs)
                    )
                    SegmentedTabs(
                        selected = glassState.mode.ordinal,
                        labels = listOf(
                            stringResource(R.string.settings_glass_mode_frosted),
                            stringResource(R.string.settings_glass_mode_water),
                            stringResource(R.string.settings_glass_mode_liquid),
                        ),
                        onSelect = { idx ->
                            onGlassModeChange(GlassMode.entries[idx])
                        }
                    )
                    Spacer(Modifier.height(Spacing.md))
                    GlassAreaToggle(
                        label = R.string.settings_glass_area_sidebar,
                        checked = glassState.sidebarEnabled,
                        onCheckedChange = { onGlassAreaEnabledChange(GlassPanelArea.SIDEBAR, it) }
                    )
                    GlassAreaToggle(
                        label = R.string.settings_glass_area_input,
                        checked = glassState.inputEnabled,
                        onCheckedChange = { onGlassAreaEnabledChange(GlassPanelArea.INPUT, it) }
                    )
                    GlassAreaToggle(
                        label = R.string.settings_glass_area_content,
                        checked = glassState.contentEnabled,
                        onCheckedChange = { onGlassAreaEnabledChange(GlassPanelArea.CONTENT, it) }
                    )
                    if (glassState.mode == GlassMode.WATER) {
                        Spacer(Modifier.height(Spacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_glass_water_animated),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            AppSwitch(
                                checked = glassState.waterWaveAnimated,
                                onCheckedChange = onWaterWaveAnimatedChange
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Button(
                    onClick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.settings_background_image_pick))
                }
                OutlinedButton(
                    onClick = {
                        onRemove()
                        onDismiss()
                    },
                    enabled = imagePath != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.settings_background_image_remove))
                }
            }
        }
    }
}

/** 玻璃区域开关行：标签 + AppSwitch。 */
@Composable
private fun GlassAreaToggle(
    @androidx.annotation.StringRes label: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
