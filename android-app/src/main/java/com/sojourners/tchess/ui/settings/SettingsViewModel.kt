package com.sojourners.tchess.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.sojourners.tchess.TchessApp
import com.sojourners.tchess.settings.EngineSettings
import com.sojourners.tchess.settings.PerfProfile
import com.sojourners.tchess.settings.TimeControl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * M3 设置中心状态：性能档位 / 引擎参数 / 时间控制。
 * 所有修改即时持久化（AndroidConfigStore 异步落盘）并下发引擎（下次 go 生效）。
 * 输入合法性（正整数等）由界面层校验后调用，这里只做范围收敛。
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val store = (app as TchessApp).configStore
    private val session = (app as TchessApp).engineSession

    private val _settings = MutableStateFlow(store.snapshotSettings())
    val settings: StateFlow<EngineSettings> = _settings.asStateFlow()

    val engineAvailable: Boolean get() = session.isAvailable

    fun setEngineEnabled(enabled: Boolean) {
        store.updateEngineEnabled(enabled)
        publish()
    }

    fun setThreads(threads: Int) {
        val s = _settings.value
        store.updateEngineParams(
            threads.coerceIn(EngineSettings.MIN_THREADS, EngineSettings.MAX_THREADS),
            s.hashMB,
            s.multiPV,
        )
        publish()
    }

    fun setHashMB(hashMB: Int) {
        val s = _settings.value
        store.updateEngineParams(
            s.threads,
            hashMB.coerceIn(EngineSettings.MIN_HASH_MB, EngineSettings.MAX_HASH_MB),
            s.multiPV,
        )
        publish()
    }

    fun setMultiPV(multiPV: Int) {
        val s = _settings.value
        store.updateEngineParams(s.threads, s.hashMB, multiPV.coerceIn(1, EngineSettings.MAX_MULTI_PV))
        publish()
    }

    /** 时间控制策略；INFINITE 忽略数值 */
    fun setTimeControl(control: TimeControl, valueMillisOrCount: Long) {
        store.updateTimeControl(control, valueMillisOrCount)
        publish()
    }

    /** 切换性能档位：写入预设 Threads/Hash（滑杆随之更新，可继续微调） */
    fun applyProfile(profile: PerfProfile) {
        store.applyProfile(profile)
        publish()
    }

    private fun publish() {
        val s = store.snapshotSettings()
        _settings.value = s
        session.applySettings(s)
    }
}
