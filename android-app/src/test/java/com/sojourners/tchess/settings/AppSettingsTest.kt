package com.sojourners.tchess.settings

import com.sojourners.chess.enginee.Engine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppSettingsTest {

    @Test
    fun `clamp 收敛非法值`() {
        val s = EngineSettings(threads = 99, hashMB = 1, multiPV = 100, timeValue = 0).clamp()
        assertEquals(EngineSettings.MAX_THREADS, s.threads)
        assertEquals(EngineSettings.MIN_HASH_MB, s.hashMB)
        assertEquals(EngineSettings.MAX_MULTI_PV, s.multiPV)
        assertEquals(1L, s.timeValue)
    }

    @Test
    fun `TimeControl 与 core AnalysisModel 一一对应`() {
        assertEquals(Engine.AnalysisModel.FIXED_TIME, TimeControl.FIXED_TIME.toEngineModel())
        assertEquals(Engine.AnalysisModel.FIXED_STEPS, TimeControl.FIXED_STEPS.toEngineModel())
        assertEquals(Engine.AnalysisModel.FIXED_NODES, TimeControl.FIXED_NODES.toEngineModel())
        assertEquals(Engine.AnalysisModel.INFINITE, TimeControl.INFINITE.toEngineModel())
    }

    @Test
    fun `PerfProfile 预设与解析回退`() {
        assertEquals(PerfProfile.POWER_SAVER, PerfProfile.of("POWER_SAVER"))
        assertEquals(PerfProfile.BALANCED, PerfProfile.of("不存在"))
        assertEquals(1, PerfProfile.POWER_SAVER.threads)
        assertEquals(2, PerfProfile.BALANCED.threads)
        assertEquals(4, PerfProfile.FULL.threads)
        assertEquals(128, PerfProfile.FULL.hashMB)
    }
}
