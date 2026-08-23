package com.sojourners.tchess

import android.app.Application
import com.sojourners.chess.config.ConfigProvider
import com.sojourners.chess.openbook.SqliteAccessProvider
import com.sojourners.tchess.config.AndroidConfigStore
import com.sojourners.tchess.engine.EngineSession
import com.sojourners.tchess.sqlite.FrameworkSqliteAccess

/**
 * 应用入口：初始化 core SPI（ConfigProvider / SqliteAccessProvider）与全局引擎会话。
 * M4：注册框架 SQLite 工厂（开局库 xqb/obk/pfBook 均为 SQLite 文件，经 SPI 只读访问）。
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
        SqliteAccessProvider.setFactory { path -> FrameworkSqliteAccess(path) }
        engineSession = EngineSession(this)
    }
}
