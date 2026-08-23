package com.sojourners.tchess.book

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

/**
 * 本地开局库命名规则（纯 JVM 可测）。
 *
 * core [com.sojourners.chess.openbook.OpenBookManager] 按扩展名（区分大小写）路由：
 * `.xqb` / `.obk` / `.pfBook`。SAF 来源文件名大小写不可控，这里统一归一化为可识别后缀。
 */
object BookNames {

    /** 支持的库格式（小写形式）→ 归一化后的规范文件名后缀 */
    private val CANONICAL_SUFFIX = mapOf("xqb" to ".xqb", "obk" to ".obk", "pfbook" to ".pfBook")

    /** UI 提示用格式清单 */
    const val SUPPORTED_KEYS = "xqb / obk / pfBook"

    fun isSupported(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return CANONICAL_SUFFIX.containsKey(ext)
    }

    /** 源文件名 → 规范目标名（保留主名，后缀归一化） */
    fun canonicalName(sourceName: String): String? {
        val dot = sourceName.lastIndexOf('.')
        if (dot <= 0) return null
        val ext = sourceName.substring(dot + 1).lowercase()
        val suffix = CANONICAL_SUFFIX[ext] ?: return null
        return sanitizeBase(sourceName.substring(0, dot)) + suffix
    }

    /** 目标重名时追加序号：a.xqb → a (2).xqb */
    fun uniqueName(targetDir: File, desired: String): String {
        if (!File(targetDir, desired).exists()) return desired
        val dot = desired.lastIndexOf('.')
        val base = if (dot > 0) desired.substring(0, dot) else desired
        val ext = if (dot > 0) desired.substring(dot) else ""
        var i = 2
        while (File(targetDir, "$base ($i)$ext").exists()) i++
        return "$base ($i)$ext"
    }

    private fun sanitizeBase(base: String): String {
        // 去掉路径分隔与非法字符，避免 SAF 文件名带奇怪字符落盘失败
        val cleaned = base.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifEmpty { "book" }
    }
}

/**
 * 开局库导入器（M4）：SAF Uri → filesDir/books/。
 * 安卓无法长期持有 content:// 权限，且 OpenBookManager 只认真实路径，故复制进应用私有目录。
 */
class BookImporter(private val context: Context) {

    val booksDir: File = File(context.filesDir, "books")

    /** 导入并返回落盘文件的绝对路径；不支持的格式返回 null（调用方提示） */
    @Throws(IOException::class)
    fun import(uri: Uri, displayName: String): String? {
        val targetName = BookNames.canonicalName(displayName) ?: return null
        if (!BookNames.isSupported(targetName)) return null
        if (!booksDir.exists()) booksDir.mkdirs()
        val dest = File(booksDir, BookNames.uniqueName(booksDir, targetName))
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("无法打开所选文件")
        return dest.absolutePath
    }

    /** 删除已导入的库文件（列表移除时调用；文件不存在视为已删除） */
    fun delete(path: String) {
        val f = File(path)
        if (f.exists() && f.parentFile == booksDir) f.delete()
    }
}
