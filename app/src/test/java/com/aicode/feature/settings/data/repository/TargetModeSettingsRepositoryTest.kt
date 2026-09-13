package com.aicode.feature.settings.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * [TargetModeSettingsRepository] 阈值读写：默认 50/5，写入后立即生效且下限夹到 1。
 * DataStore 依赖 Android Context，用 Robolectric；Android PRoot 容器加载不了
 * Robolectric native 库（同 MigrationTest），非标准 Linux 环境跳过。
 * 同一 JVM 内 DataStore 单例跨方法共享状态，写入类断言集中在单个方法内保证顺序。
 */
@RunWith(AndroidJUnit4::class)
class TargetModeSettingsRepositoryTest {

    private lateinit var repo: TargetModeSettingsRepository

    @Before
    fun setUp() {
        guardEnvironment()
        repo = TargetModeSettingsRepository(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun thresholds_writeReadAndClampInOrder() = runTest {
        // 重置到默认基准后再验证默认值读取
        repo.setMaxStepBudget(50)
        repo.setMaxConsecutiveFailures(5)
        assertEquals(50, repo.thresholds.first().maxStepBudget)
        assertEquals(5, repo.thresholds.first().maxConsecutiveFailures)

        // 写入自定义值立即生效
        repo.setMaxStepBudget(80)
        repo.setMaxConsecutiveFailures(9)
        val custom = repo.thresholds.first()
        assertEquals(80, custom.maxStepBudget)
        assertEquals(9, custom.maxConsecutiveFailures)

        // 下限夹到 1
        repo.setMaxStepBudget(0)
        repo.setMaxConsecutiveFailures(-3)
        val clamped = repo.thresholds.first()
        assertEquals(1, clamped.maxStepBudget)
        assertEquals(1, clamped.maxConsecutiveFailures)

        // 恢复默认基准，避免污染同 JVM 内后续运行
        repo.setMaxStepBudget(50)
        repo.setMaxConsecutiveFailures(5)
    }

    private fun guardEnvironment() {
        val androidContainer = File("/system").exists() ||
            System.getProperty("java.library.path")?.contains("/data/app") == true
        assumeTrue("Robolectric 仅支持标准 Linux/CI 环境（当前为 Android PRoot 容器，会 UnsatisfiedLinkError）", !androidContainer)
    }
}
