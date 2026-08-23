package com.sojourners.tchess

import android.app.Application
import com.sojourners.chess.config.ConfigProvider
import com.sojourners.tchess.config.AndroidConfigStore
import com.sojourners.tchess.engine.EngineSession

/**
 * 应用入口：初始化 core 配置 SPI（ConfigProvider）与全局引擎会话（M3：对弈/分析共用）。
 */
class TchessApp : Application() {

    lateinit var configStore: AndroidConfigStore
        private set

    lateinit var engineSession: EngineSession
        private set

    override fun onCreate() {
        super.onCreate()
        configStore = AndroidConfigStore(this)
        configStore.loadOrDefault()
        ConfigProvider.set(configStore)
        engineSession = EngineSession(this)
    }
}
