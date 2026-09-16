package com.aicode.feature.settings.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aicode.core.ui.glass.GlassMode
import com.aicode.core.ui.glass.GlassPanelArea
import com.aicode.core.ui.glass.GlassSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * [BackgroundSettingsRepository] 玻璃材质配置：模式容错解析、六键读写、
 * glassStateFlow 聚合与 frost_intensity × 32 半径换算。
 * DataStore 依赖 Android Context，用 Robolectric；Android PRoot 容器加载不了
 * Robolectric native 库，非标准 Linux 环境跳过。
 * 同一 JVM 内 DataStore 单例跨方法共享状态，写入类断言集中在单个方法内保证顺序。
 */
@RunWith(AndroidJUnit4::class)
class BackgroundGlassSettingsRepositoryTest {

    private lateinit var repo: BackgroundSettingsRepository

    @Before
    fun setUp() {
        guardEnvironment()
        repo = BackgroundSettingsRepository(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun parseGlassMode_fallsBackToFrostedOnNullOrUnknown() {
        assertEquals(GlassMode.FROSTED, BackgroundSettingsRepository.parseGlassMode(null))
        assertEquals(GlassMode.FROSTED, BackgroundSettingsRepository.parseGlassMode("frosted"))
        assertEquals(GlassMode.FROSTED, BackgroundSettingsRepository.parseGlassMode("watery"))
        assertEquals(GlassMode.WATER, BackgroundSettingsRepository.parseGlassMode("water"))
        assertEquals(GlassMode.LIQUID, BackgroundSettingsRepository.parseGlassMode("liquid"))
    }

    @Test
    fun glassModeToRaw_roundTripsThroughParse() {
        GlassMode.entries.forEach { mode ->
            assertEquals(mode, BackgroundSettingsRepository.parseGlassMode(
                BackgroundSettingsRepository.glassModeToRaw(mode)
            ))
        }
    }

    @Test
    fun glassState_writeReadAndAggregateInOrder() = runTest {
        // 重置到默认基准：总开关关、磨砂档、三区域开、动画关
        repo.setGlassEnabled(false)
        repo.setGlassMode(GlassMode.FROSTED)
        repo.setGlassPanelAreaEnabled(GlassPanelArea.SIDEBAR, true)
        repo.setGlassPanelAreaEnabled(GlassPanelArea.INPUT, true)
        repo.setGlassPanelAreaEnabled(GlassPanelArea.CONTENT, true)
        repo.setWaterWaveAnimated(false)
        repo.setFrostIntensity(BackgroundSettingsRepository.DEFAULT_FROST_INTENSITY)

        // 默认态聚合：关闭、磨砂、半径 = 0.5 × 32
        val defaults = repo.glassStateFlow.first()
        assertEquals(false, defaults.enabled)
        assertEquals(GlassMode.FROSTED, defaults.mode)
        assertEquals(true, defaults.sidebarEnabled)
        assertEquals(true, defaults.inputEnabled)
        assertEquals(true, defaults.contentEnabled)
        assertEquals(
            BackgroundSettingsRepository.DEFAULT_FROST_INTENSITY * GlassSettings.MAX_RADIUS_DP,
            defaults.radiusDp,
            1e-4f
        )
        assertEquals(false, defaults.waterWaveAnimated)

        // 写入自定义配置后聚合立即生效
        repo.setGlassEnabled(true)
        repo.setGlassMode(GlassMode.LIQUID)
        repo.setGlassPanelAreaEnabled(GlassPanelArea.SIDEBAR, false)
        repo.setWaterWaveAnimated(true)
        repo.setFrostIntensity(0.25f)
        val custom = repo.glassStateFlow.first()
        assertEquals(true, custom.enabled)
        assertEquals(GlassMode.LIQUID, custom.mode)
        assertEquals(false, custom.sidebarEnabled)
        assertEquals(true, custom.inputEnabled)
        assertEquals(true, custom.contentEnabled)
        assertEquals(0.25f * GlassSettings.MAX_RADIUS_DP, custom.radiusDp, 1e-4f)
        assertEquals(true, custom.waterWaveAnimated)

        // 半径越界夹紧到 0..1（×32 后 0..32dp）
        repo.setFrostIntensity(2f)
        assertEquals(
            GlassSettings.MAX_RADIUS_DP,
            repo.glassStateFlow.first().radiusDp,
            1e-4f
        )

        // 恢复默认基准，避免污染同 JVM 内后续运行
        repo.setGlassEnabled(false)
        repo.setGlassMode(GlassMode.FROSTED)
        repo.setGlassPanelAreaEnabled(GlassPanelArea.SIDEBAR, true)
        repo.setWaterWaveAnimated(false)
        repo.setFrostIntensity(BackgroundSettingsRepository.DEFAULT_FROST_INTENSITY)
    }

    private fun guardEnvironment() {
        val androidContainer = File("/system").exists() ||
            System.getProperty("java.library.path")?.contains("/data/app") == true
        assumeTrue(
            "Robolectric 仅支持标准 Linux/CI 环境（当前为 Android PRoot 容器，会 UnsatisfiedLinkError）",
            !androidContainer
        )
    }
}
