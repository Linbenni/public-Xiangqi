package com.sojourners.tchess

import android.app.Application
import com.sojourners.chess.config.ConfigProvider
import com.sojourners.tchess.config.AndroidConfigStore

/**
 * 应用入口：初始化 core 配置 SPI（ConfigProvider）。
 */
class TchessApp : Application() {

    lateinit var configStore: AndroidConfigStore
        private set

    override fun onCreate() {
        super.onCreate()
        configStore = AndroidConfigStore(this)
        configStore.loadOrDefault()
        ConfigProvider.set(configStore)
    }
}
