package com.sojourners.tchess.config

import android.content.Context
import com.sojourners.chess.config.AppConfig
import com.sojourners.chess.openbook.MoveRule
import com.sojourners.tchess.settings.BookSettings
import com.sojourners.tchess.settings.EngineSettings
import com.sojourners.tchess.settings.PerfProfile
import com.sojourners.tchess.settings.TimeControl
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * core [AppConfig] SPI 的安卓实现。
 * M2 先提供保守默认值（不开开局库、无延迟），配置以 JSON 文件持久化在 filesDir。
 * M3 扩展：引擎开关/线程/Hash/MultiPV、分析时间控制、性能档位（安卓侧专属字段，
 * 与 core SPI 字段同文件存储）。
 */
class AndroidConfigStore(context: Context) : AppConfig {

    private val file = File(context.filesDir, "tchess_config.json")
    private val io = Executors.newSingleThreadExecutor()

    @Volatile private var engineDelayStart: Int = 0
    @Volatile private var engineDelayEnd: Int = 0
    @Volatile private var bookDelayStart: Int = 0
    @Volatile private var bookDelayEnd: Int = 0
    @Volatile private var bookSwitch: Boolean = false
    @Volatile private var offManualSteps: Int = 6
    @Volatile private var openBookList: List<String> = emptyList()
    @Volatile private var useCloudBook: Boolean = false
    @Volatile private var localBookFirst: Boolean = false
    @Volatile private var moveRule: MoveRule = MoveRule.BEST_SCORE
    @Volatile private var onlyCloudFinalPhase: Boolean = false
    @Volatile private var cloudBookTimeout: Int = 5000

    // ---- M3 安卓侧扩展设置 ----
    @Volatile private var engineEnabled: Boolean = true
    @Volatile private var threads: Int = EngineSettings.DEFAULT_THREADS
    @Volatile private var hashMB: Int = EngineSettings.DEFAULT_HASH_MB
    @Volatile private var multiPV: Int = EngineSettings.DEFAULT_MULTI_PV
    @Volatile private var timeControl: TimeControl = TimeControl.FIXED_TIME
    @Volatile private var timeValue: Long = EngineSettings.DEFAULT_TIME_VALUE
    @Volatile private var perfProfile: PerfProfile = PerfProfile.BALANCED

    /** 当前开局库设置快照（已做范围收敛） */
    @Synchronized
    fun snapshotBookSettings(): BookSettings = BookSettings(
        bookSwitch = bookSwitch,
        useCloudBook = useCloudBook,
        localBookFirst = localBookFirst,
        moveRule = moveRule,
        offManualSteps = offManualSteps,
        onlyCloudFinalPhase = onlyCloudFinalPhase,
        cloudBookTimeout = cloudBookTimeout,
    ).clamp()

    @Synchronized
    fun updateBookSettings(s: BookSettings) {
        val c = s.clamp()
        bookSwitch = c.bookSwitch
        useCloudBook = c.useCloudBook
        localBookFirst = c.localBookFirst
        moveRule = c.moveRule
        offManualSteps = c.offManualSteps
        onlyCloudFinalPhase = c.onlyCloudFinalPhase
        cloudBookTimeout = c.cloudBookTimeout
        persistAsync()
    }

    /** 本地库列表变化（导入/删除/排序）后整体替换并落盘 */
    @Synchronized
    fun updateOpenBooks(paths: List<String>) {
        openBookList = ArrayList(paths)
        persistAsync()
    }

    /** 本地库路径列表（UI 展示用；core 侧经 getOpenBookList 读取） */
    fun currentOpenBooks(): List<String> = openBookList

    /** 应用启动时同步加载（文件仅几 KB）。 */
    fun loadOrDefault() {
        try {
            if (!file.exists()) return
            val json = JSONObject(file.readText(Charsets.UTF_8))
            engineDelayStart = json.optInt("engineDelayStart", engineDelayStart)
            engineDelayEnd = json.optInt("engineDelayEnd", engineDelayEnd)
            bookDelayStart = json.optInt("bookDelayStart", bookDelayStart)
            bookDelayEnd = json.optInt("bookDelayEnd", bookDelayEnd)
            bookSwitch = json.optBoolean("bookSwitch", bookSwitch)
            offManualSteps = json.optInt("offManualSteps", offManualSteps)
            useCloudBook = json.optBoolean("useCloudBook", useCloudBook)
            localBookFirst = json.optBoolean("localBookFirst", localBookFirst)
            onlyCloudFinalPhase = json.optBoolean("onlyCloudFinalPhase", onlyCloudFinalPhase)
            cloudBookTimeout = json.optInt("cloudBookTimeout", cloudBookTimeout)
            moveRule = runCatching { MoveRule.valueOf(json.optString("moveRule", moveRule.name)) }
                .getOrDefault(MoveRule.BEST_SCORE)
            val books = json.optJSONArray("openBookList")
            if (books != null) {
                openBookList = (0 until books.length()).map { books.getString(it) }
            }
            engineEnabled = json.optBoolean("engineEnabled", engineEnabled)
            threads = json.optInt("threads", threads)
            hashMB = json.optInt("hashMB", hashMB)
            multiPV = json.optInt("multiPV", multiPV)
            timeControl = TimeControl.of(json.optString("timeControl", timeControl.name))
            timeValue = json.optLong("timeValue", timeValue)
            perfProfile = PerfProfile.of(json.optString("perfProfile", perfProfile.name))
        } catch (_: Exception) {
            // 配置损坏时回退默认值
        }
    }

    private fun persistAsync() {
        io.execute {
            try {
                val json = JSONObject()
                json.put("engineDelayStart", engineDelayStart)
                json.put("engineDelayEnd", engineDelayEnd)
                json.put("bookDelayStart", bookDelayStart)
                json.put("bookDelayEnd", bookDelayEnd)
                json.put("bookSwitch", bookSwitch)
                json.put("offManualSteps", offManualSteps)
                json.put("useCloudBook", useCloudBook)
                json.put("localBookFirst", localBookFirst)
                json.put("onlyCloudFinalPhase", onlyCloudFinalPhase)
                json.put("cloudBookTimeout", cloudBookTimeout)
                json.put("moveRule", moveRule.name)
                json.put("openBookList", org.json.JSONArray(openBookList))
                json.put("engineEnabled", engineEnabled)
                json.put("threads", threads)
                json.put("hashMB", hashMB)
                json.put("multiPV", multiPV)
                json.put("timeControl", timeControl.name)
                json.put("timeValue", timeValue)
                json.put("perfProfile", perfProfile.name)
                file.writeText(json.toString(), Charsets.UTF_8)
            } catch (_: Exception) {
                // 忽略持久化失败：下次启动用默认值
            }
        }
    }

    override fun getEngineDelayStart(): Int = engineDelayStart
    override fun getEngineDelayEnd(): Int = engineDelayEnd
    override fun getBookDelayStart(): Int = bookDelayStart
    override fun getBookDelayEnd(): Int = bookDelayEnd
    override fun getBookSwitch(): Boolean = bookSwitch
    override fun getOffManualSteps(): Int = offManualSteps
    override fun getOpenBookList(): List<String> = openBookList
    override fun getUseCloudBook(): Boolean = useCloudBook
    override fun getLocalBookFirst(): Boolean = localBookFirst
    override fun getMoveRule(): MoveRule = moveRule
    override fun getOnlyCloudFinalPhase(): Boolean = onlyCloudFinalPhase
    override fun getCloudBookTimeout(): Int = cloudBookTimeout

    // ---- M3 扩展设置（安卓侧专属，不进 core SPI）----

    /** 当前引擎设置快照（已做范围收敛） */
    @Synchronized
    fun snapshotSettings(): EngineSettings = EngineSettings(
        engineEnabled = engineEnabled,
        threads = threads,
        hashMB = hashMB,
        multiPV = multiPV,
        timeControl = timeControl,
        timeValue = timeValue,
        perfProfile = perfProfile,
    ).clamp()

    @Synchronized
    fun updateEngineEnabled(enabled: Boolean) {
        engineEnabled = enabled
        persistAsync()
    }

    @Synchronized
    fun updateEngineParams(threads: Int, hashMB: Int, multiPV: Int) {
        this.threads = threads.coerceIn(EngineSettings.MIN_THREADS, EngineSettings.MAX_THREADS)
        this.hashMB = hashMB.coerceIn(EngineSettings.MIN_HASH_MB, EngineSettings.MAX_HASH_MB)
        this.multiPV = multiPV.coerceIn(1, EngineSettings.MAX_MULTI_PV)
        persistAsync()
    }

    @Synchronized
    fun updateTimeControl(control: TimeControl, value: Long) {
        timeControl = control
        timeValue = value.coerceAtLeast(1L)
        persistAsync()
    }

    /**
     * 切换性能档位：档位是 Threads/Hash 的快捷预设（写入后可在引擎参数中微调）。
     * @return 应用后的线程/Hash 值
     */
    @Synchronized
    fun applyProfile(profile: PerfProfile): Pair<Int, Int> {
        perfProfile = profile
        threads = profile.threads
        hashMB = profile.hashMB
        persistAsync()
        return Pair(threads, hashMB)
    }
}
