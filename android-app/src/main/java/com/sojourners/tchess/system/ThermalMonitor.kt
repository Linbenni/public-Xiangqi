package com.sojourners.tchess.system

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 发热状态监听（ANDROID_PLAN.md M3：手机性能档位 + 发热降频提示）。
 * API 29+ 使用 PowerManager.OnThermalStatusListener；低版本无系统热状态，保持 UNKNOWN。
 */
object ThermalMonitor {

    /** -1 = 设备/系统不支持（API < 29），其余为 PowerManager.THERMAL_STATUS_* */
    private val _status = MutableStateFlow(-1)
    val status: StateFlow<Int> = _status.asStateFlow()

    private var registered = false

    @Synchronized
    fun init(context: Context) {
        if (registered || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        try {
            _status.value = pm.currentThermalStatus
            pm.addThermalStatusListener { s -> _status.value = s }
            registered = true
        } catch (_: Exception) {
            // 个别 ROM 未实现热状态服务：静默降级为不提示
        }
    }

    /** 是否应显示"建议降档"提示：SEVERE 及以上一律提示；MODERATE 仅在全力档提示 */
    fun shouldWarn(statusValue: Int, fullPowerProfile: Boolean): Boolean = when (statusValue) {
        PowerManager.THERMAL_STATUS_MODERATE -> fullPowerProfile
        PowerManager.THERMAL_STATUS_SEVERE,
        PowerManager.THERMAL_STATUS_CRITICAL,
        PowerManager.THERMAL_STATUS_EMERGENCY,
        PowerManager.THERMAL_STATUS_SHUTDOWN,
        -> true
        else -> false
    }
}
