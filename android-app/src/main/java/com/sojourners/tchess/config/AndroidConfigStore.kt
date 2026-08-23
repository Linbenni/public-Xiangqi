package com.sojourners.tchess.config

import android.content.Context
import com.sojourners.chess.config.AppConfig
import com.sojourners.chess.openbook.MoveRule
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * core [AppConfig] SPI 的安卓实现。
 * M2 先提供保守默认值（不开开局库、无延迟），配置以 JSON 文件持久化在 filesDir，
 * 后续里程碑（M3 设置页）在此基础上扩展。
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
}
