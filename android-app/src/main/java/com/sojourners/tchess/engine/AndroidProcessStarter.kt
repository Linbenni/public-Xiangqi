package com.sojourners.tchess.engine

import com.sojourners.chess.enginee.ProcessStarter
import java.io.File

/**
 * 安卓引擎进程启动实现（ANDROID_PLAN.md §5.1）：
 * 引擎以 libpikafish.so 形式随 APK 安装到只读的 nativeLibraryDir，
 * 该目录允许 exec；stdin/stdout 交给 core 协议层。
 */
class AndroidProcessStarter : ProcessStarter {

    override fun start(executablePath: String, workDir: File?): Process {
        val builder = ProcessBuilder(executablePath)
        if (workDir != null && workDir.isDirectory) {
            builder.directory(workDir)
        }
        builder.redirectErrorStream(false)
        return builder.start()
    }
}
