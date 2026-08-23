package com.sojourners.tchess.sqlite

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.sojourners.chess.openbook.SqliteAccess

/**
 * core [SqliteAccess] SPI 的安卓实现（M4）：基于框架自带 SQLite 只读打开开局库文件。
 * 与桌面版 sqlite-jdbc 实现等价：按列名返回行数据，值为基础类型
 * （Long/Double/String/ByteArray），core 侧统一经 `Number` 取整。
 */
class FrameworkSqliteAccess(bookPath: String) : SqliteAccess {

    private val db: SQLiteDatabase = SQLiteDatabase.openDatabase(
        bookPath,
        null,
        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
    )

    override fun query(sql: String): MutableList<MutableMap<String, Any>> {
        val rows = ArrayList<MutableMap<String, Any>>()
        val cursor: Cursor = db.rawQuery(sql, null)
        cursor.use { c ->
            val names = c.columnNames
            while (c.moveToNext()) {
                val row = HashMap<String, Any>(names.size)
                for (i in names.indices) {
                    when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> {}
                        Cursor.FIELD_TYPE_INTEGER -> row[names[i]] = c.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> row[names[i]] = c.getDouble(i)
                        Cursor.FIELD_TYPE_BLOB -> row[names[i]] = c.getBlob(i)
                        else -> row[names[i]] = c.getString(i)
                    }
                }
                rows.add(row)
            }
        }
        return rows
    }

    override fun close() {
        try {
            db.close()
        } catch (_: Exception) {
        }
    }
}
