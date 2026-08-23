package com.sojourners.tchess.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.sojourners.chess.openbook.MoveRule
import com.sojourners.chess.openbook.OpenBookManager
import com.sojourners.tchess.TchessApp
import com.sojourners.tchess.book.BookImporter
import com.sojourners.tchess.book.BookNames
import com.sojourners.tchess.settings.BookSettings
import com.sojourners.tchess.settings.EngineSettings
import com.sojourners.tchess.settings.PerfProfile
import com.sojourners.tchess.settings.TimeControl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * M3 设置中心状态：性能档位 / 引擎参数 / 时间控制。
 * 所有修改即时持久化（AndroidConfigStore 异步落盘）并下发引擎（下次 go 生效）。
 * 输入合法性（正整数等）由界面层校验后调用，这里只做范围收敛。
 *
 * M4 扩展：开局库设置（总开关/云库/本地优先/选着规则/脱离步数/云库超时）与本地库管理
 * （SAF 导入到 filesDir/books、删除、排序），列表变化后即时重载 OpenBookManager。
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val store = (app as TchessApp).configStore
    private val session = (app as TchessApp).engineSession
    private val importer = BookImporter(app)
    private val io = Executors.newSingleThreadExecutor()

    private val _settings = MutableStateFlow(store.snapshotSettings())
    val settings: StateFlow<EngineSettings> = _settings.asStateFlow()

    private val _bookSettings = MutableStateFlow(store.snapshotBookSettings())
    val bookSettings: StateFlow<BookSettings> = _bookSettings.asStateFlow()

    /** 本地库绝对路径列表（顺序即查询优先级） */
    private val _openBooks = MutableStateFlow(store.currentOpenBooks())
    val openBooks: StateFlow<List<String>> = _openBooks.asStateFlow()

    /** 库操作提示（导入成功/失败等） */
    private val _bookMessage = MutableStateFlow<String?>(null)
    val bookMessage: StateFlow<String?> = _bookMessage.asStateFlow()

    val engineAvailable: Boolean get() = session.isAvailable

    fun dismissBookMessage() {
        _bookMessage.value = null
    }

    // ---------------------------------------------------------------- M3 引擎设置

    fun setEngineEnabled(enabled: Boolean) {
        store.updateEngineEnabled(enabled)
        publishEngine()
    }

    fun setThreads(threads: Int) {
        val s = _settings.value
        store.updateEngineParams(
            threads.coerceIn(EngineSettings.MIN_THREADS, EngineSettings.MAX_THREADS),
            s.hashMB,
            s.multiPV,
        )
        publishEngine()
    }

    fun setHashMB(hashMB: Int) {
        val s = _settings.value
        store.updateEngineParams(
            s.threads,
            hashMB.coerceIn(EngineSettings.MIN_HASH_MB, EngineSettings.MAX_HASH_MB),
            s.multiPV,
        )
        publishEngine()
    }

    fun setMultiPV(multiPV: Int) {
        val s = _settings.value
        store.updateEngineParams(s.threads, s.hashMB, multiPV.coerceIn(1, EngineSettings.MAX_MULTI_PV))
        publishEngine()
    }

    /** 时间控制策略；INFINITE 忽略数值 */
    fun setTimeControl(control: TimeControl, valueMillisOrCount: Long) {
        store.updateTimeControl(control, valueMillisOrCount)
        publishEngine()
    }

    /** 切换性能档位：写入预设 Threads/Hash（滑杆随之更新，可继续微调） */
    fun applyProfile(profile: PerfProfile) {
        store.applyProfile(profile)
        publishEngine()
    }

    private fun publishEngine() {
        val s = store.snapshotSettings()
        _settings.value = s
        session.applySettings(s)
    }

    // ---------------------------------------------------------------- M4 开局库设置

    fun setBookSwitch(enabled: Boolean) = updateBooks { it.copy(bookSwitch = enabled) }
    fun setUseCloudBook(enabled: Boolean) = updateBooks { it.copy(useCloudBook = enabled) }
    fun setLocalBookFirst(enabled: Boolean) = updateBooks { it.copy(localBookFirst = enabled) }
    fun setOnlyCloudFinalPhase(enabled: Boolean) = updateBooks { it.copy(onlyCloudFinalPhase = enabled) }
    fun setMoveRule(rule: MoveRule) = updateBooks { it.copy(moveRule = rule) }
    fun setOffManualSteps(steps: Int) = updateBooks { it.copy(offManualSteps = steps) }
    fun setCloudTimeout(ms: Int) = updateBooks { it.copy(cloudBookTimeout = ms) }

    private fun updateBooks(edit: (BookSettings) -> BookSettings) {
        store.updateBookSettings(edit(_bookSettings.value))
        _bookSettings.value = store.snapshotBookSettings()
    }

    // ---------------------------------------------------------------- M4 本地库管理

    /** SAF 导入：复制进 filesDir/books 后加入列表并重载 */
    fun importBook(uri: Uri, displayName: String?) {
        io.execute {
            try {
                if (displayName == null || !BookNames.isSupported(displayName)) {
                    _bookMessage.value = "仅支持 ${BookNames.SUPPORTED_KEYS} 格式"
                    return@execute
                }
                val path = importer.import(uri, displayName)
                if (path == null) {
                    _bookMessage.value = "无法识别的库文件：$displayName"
                    return@execute
                }
                synchronized(this@SettingsViewModel) {
                    val updated = ArrayList(_openBooks.value)
                    if (!updated.contains(path)) updated.add(path)
                    store.updateOpenBooks(updated)
                    OpenBookManager.getInstance().reloadIfChanged()
                    _openBooks.value = updated
                }
                _bookMessage.value = "已导入 ${path.substringAfterLast('/')}"
            } catch (e: Exception) {
                _bookMessage.value = "导入失败：${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun removeBook(index: Int) {
        if (index < 0 || index >= _openBooks.value.size) return
        val path = _openBooks.value[index]
        importer.delete(path)
        val updated = ArrayList(_openBooks.value).apply { removeAt(index) }
        store.updateOpenBooks(updated)
        io.execute { OpenBookManager.getInstance().reloadIfChanged() }
        _openBooks.value = updated
    }

    fun moveBook(index: Int, delta: Int) {
        val list = ArrayList(_openBooks.value)
        val target = index + delta
        if (index < 0 || index >= list.size || target < 0 || target >= list.size) return
        val item = list.removeAt(index)
        list.add(target, item)
        store.updateOpenBooks(list)
        io.execute { OpenBookManager.getInstance().reloadIfChanged() }
        _openBooks.value = list
    }
}
