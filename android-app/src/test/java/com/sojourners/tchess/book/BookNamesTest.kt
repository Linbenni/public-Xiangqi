package com.sojourners.tchess.book

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BookNamesTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `三种库格式被支持`() {
        assertTrue(BookNames.isSupported("开局库.xqb"))
        assertTrue(BookNames.isSupported("book.OBK"))
        assertTrue(BookNames.isSupported("wind.PFBOOK"))
        assertFalse(BookNames.isSupported("book.pgn"))
        assertFalse(BookNames.isSupported("无扩展名"))
    }

    @Test
    fun `后缀归一化为core可识别的大小写`() {
        // OpenBookManager 按精确后缀路由（.pfBook 大小写敏感）
        assertEquals("旋风.pfBook", BookNames.canonicalName("旋风.PFBOOK"))
        assertEquals("兵河.obk", BookNames.canonicalName("兵河.OBK"))
        assertEquals("巫师.xqb", BookNames.canonicalName("巫师.xqb"))
        assertNull(BookNames.canonicalName("没有后缀"))
    }

    @Test
    fun `非法文件名字符被替换`(@TempDir dir: File) {
        val name = BookNames.canonicalName("a/b:c*?.xqb")
        assertEquals("a_b_c__.xqb", name)
    }

    @Test
    fun `重名自动追加序号`() {
        assertEquals("book.xqb", BookNames.uniqueName(tempDir, "book.xqb"))
        File(tempDir, "book.xqb").createNewFile()
        assertEquals("book (2).xqb", BookNames.uniqueName(tempDir, "book.xqb"))
        File(tempDir, "book (2).xqb").createNewFile()
        assertEquals("book (3).xqb", BookNames.uniqueName(tempDir, "book.xqb"))
    }
}
