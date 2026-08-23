package com.sojourners.tchess.manual

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManualFormatsTest {

    @Test
    fun `四种导入格式都有解析服务`() {
        assertEquals(setOf("txq", "pgn", "xqf", "cbr"), ManualFormats.IMPORT_EXTENSIONS.toSet())
        ManualFormats.IMPORT_EXTENSIONS.forEach { ext ->
            assertNotNull(ManualFormats.serviceFor(ext), "缺少 $ext 解析器")
        }
    }

    @Test
    fun `扩展名大小写与点号处理`() {
        assertNotNull(ManualFormats.serviceFor(ManualFormats.extensionOf("对局.XQF")))
        assertEquals("xqf", ManualFormats.extensionOf("对局.XQF"))
        assertEquals("pgn", ManualFormats.extensionOf("a.b.pgn"))
        assertNull(ManualFormats.extensionOf("无扩展名"))
        assertNull(ManualFormats.extensionOf("以点结尾."))
        assertNull(ManualFormats.serviceFor("docx"))
        assertNull(ManualFormats.serviceFor(null))
    }

    @Test
    fun `导出格式是导入格式的子集且可路由`() {
        assertTrue(ManualFormats.IMPORT_EXTENSIONS.containsAll(ManualFormats.EXPORT_EXTENSIONS))
        ManualFormats.EXPORT_EXTENSIONS.forEach { ext ->
            assertNotNull(ManualFormats.serviceFor(ext))
        }
    }
}
