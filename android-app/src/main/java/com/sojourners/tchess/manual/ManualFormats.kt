package com.sojourners.tchess.manual

import com.sojourners.chess.manual.CbrChessManualImpl
import com.sojourners.chess.manual.ChessManualService
import com.sojourners.chess.manual.PgnChessManualImpl
import com.sojourners.chess.manual.TxqChessManualImpl
import com.sojourners.chess.manual.XqfChessManualImpl

/**
 * 棋谱格式路由（M4）：与桌面 ChessManualHandle 的 manualServices 表一致。
 * 导入支持 txq/pgn/xqf/cbr；导出/分享与桌面「另存为」一致仅 txq/pgn。
 */
object ManualFormats {

    val IMPORT_EXTENSIONS: List<String> = listOf("txq", "pgn", "xqf", "cbr")

    /** 可保存/分享的格式（桌面 saveAs 同款集合） */
    val EXPORT_EXTENSIONS: List<String> = listOf("txq", "pgn")

    private val services: Map<String, ChessManualService> = mapOf(
        "txq" to TxqChessManualImpl(),
        "pgn" to PgnChessManualImpl(),
        "xqf" to XqfChessManualImpl(),
        "cbr" to CbrChessManualImpl(),
    )

    /** 从文件名取小写扩展名（无扩展名返回 null） */
    fun extensionOf(fileName: String): String? {
        val dot = fileName.lastIndexOf('.')
        if (dot < 0 || dot == fileName.length - 1) return null
        return fileName.substring(dot + 1).lowercase()
    }

    /** 按扩展名取解析服务；不支持返回 null */
    fun serviceFor(extension: String?): ChessManualService? {
        if (extension == null) return null
        return services[extension.lowercase()]
    }
}
