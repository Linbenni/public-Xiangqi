package com.sojourners.tchess.engine

import android.content.Context
import com.sojourners.chess.enginee.Engine
import com.sojourners.chess.enginee.EngineCallBack
import java.io.File

/**
 * 内置 Pikafish 的定位与装配。
 * - 引擎二进制：jniLibs/libpikafish.so → nativeLibraryDir（安装时系统解压，可执行）
 * - NNUE 权重：官方引擎需要外部权重（EvalFile），assets/pikafish.nnue 首次启动释放到
 *   filesDir 并注入；缺权重时引擎无法正常评估（脚本 fetch-engine 一并放置）。
 */
class PikafishProvider(private val context: Context) {

    companion object {
        const val ENGINE_LIB = "libpikafish.so"
        const val NNUE_ASSET = "pikafish.nnue"

        /** 手机保守默认（ANDROID_PLAN.md M3：Threads 2~4、Hash 64~128MB） */
        const val DEFAULT_THREADS = 2
        const val DEFAULT_HASH_MB = 64
    }

    fun isAvailable(): Boolean = File(context.applicationInfo.nativeLibraryDir, ENGINE_LIB).exists()

    /**
     * 创建引擎实例；不可用或启动失败返回 null（调用方进入双人模式兜底）。
     */
    fun create(callback: EngineCallBack): Engine? {
        if (!isAvailable()) return null
        val so = File(context.applicationInfo.nativeLibraryDir, ENGINE_LIB)
        val options = LinkedHashMap<String, String>().apply {
            put("Threads", DEFAULT_THREADS.toString())
            put("Hash", DEFAULT_HASH_MB.toString())
            extractNnue()?.let { put("EvalFile", it.absolutePath) }
        }
        val config = com.sojourners.chess.model.EngineConfig(
            "Pikafish",
            so.absolutePath,
            "uci",
            options,
        )
        return try {
            Engine(config, callback, AndroidProcessStarter())
        } catch (_: Exception) {
            null
        }
    }

    private fun extractNnue(): File? {
        val target = File(context.filesDir, NNUE_ASSET)
        if (target.exists() && target.length() > 0) return target
        return try {
            context.assets.open(NNUE_ASSET).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target
        } catch (_: Exception) {
            null // 未打包 nnue 资产，交给引擎自带权重
        }
    }
}
