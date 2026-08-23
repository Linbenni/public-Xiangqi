package com.sojourners.tchess.engine

import android.content.Context
import com.sojourners.chess.enginee.Engine
import com.sojourners.chess.enginee.EngineCallBack
import com.sojourners.chess.model.BookData
import com.sojourners.chess.model.ThinkData
import com.sojourners.tchess.settings.EngineSettings

/**
 * 引擎消费方（对弈 / 分析各为一个）。回调可能来自引擎 reader 线程，
 * 消费方自行切线程（与桌面 Controller 的 Platform.runLater 同理）。
 */
interface EngineConsumer {
    fun onBestMove(first: String?, second: String?) {}
    fun onThinkDetail(td: ThinkData) {}
    fun onBookResults(list: MutableList<BookData>?) {}
}

/**
 * 全局唯一引擎会话（M3）：
 * - 进程懒创建，对弈与分析两个页面共用同一引擎（省内存、避免双进程争 CPU）；
 * - 回调按当前绑定的消费方分发（切页即换绑，旧搜索由 core 的代际机制自动失效）；
 * - 设置（Threads/Hash/MultiPV）统一下发，与桌面 configureEngineForSearch 一致。
 */
class EngineSession(context: Context) {

    private val provider = PikafishProvider(context.applicationContext)

    @Volatile private var engine: Engine? = null

    @Volatile private var bound: EngineConsumer? = null

    /** 引擎二进制缺失（构建未打包 libpikafish.so 或启动失败） */
    @Volatile var engineMissing: Boolean = !provider.isAvailable()
        private set

    val isAvailable: Boolean get() = provider.isAvailable()

    /** 当前是否有活跃搜索（对弈页判断是否需要保活提示等） */
    val hasEngine: Boolean get() = engine != null

    fun bind(consumer: EngineConsumer) {
        bound = consumer
    }

    fun unbind(consumer: EngineConsumer) {
        if (bound === consumer) bound = null
    }

    private val dispatch = object : EngineCallBack {
        override fun bestMove(first: String?, second: String?) {
            bound?.onBestMove(first, second)
        }

        override fun thinkDetail(td: ThinkData?) {
            val c = bound ?: return
            td?.let { c.onThinkDetail(it) }
        }

        override fun showBookResults(list: MutableList<BookData>?) {
            bound?.onBookResults(list)
        }
    }

    /** 懒创建引擎；不可用返回 null 并置 engineMissing（调用方走双人兜底） */
    @Synchronized
    fun acquire(): Engine? {
        engine?.let { return it }
        if (!provider.isAvailable()) {
            engineMissing = true
            return null
        }
        val e = try {
            provider.create(dispatch)
        } catch (_: Exception) {
            null
        }
        if (e == null) {
            engineMissing = true
            return null
        }
        engine = e
        return e
    }

    /** 下发待生效参数（下次 go 前由 core 统一 setoption） */
    fun applySettings(s: EngineSettings) {
        val e = engine ?: return
        val c = s.clamp()
        e.setThreadNum(c.threads)
        e.setHashSize(c.hashMB)
        e.setMultiPV(c.multiPV)
    }

    /** 请求当前搜索尽快停止（幂等；无搜索时为无害 no-op） */
    fun stopSearch() {
        engine?.stop()
    }

    @Synchronized
    fun close() {
        engine?.close()
        engine = null
    }
}
