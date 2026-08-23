package com.sojourners.chess.openbook;

import java.util.List;
import java.util.Map;

/**
 * 只读 SQLite 访问 SPI：核心层开局库只需要执行简单查询。
 * 桌面版基于 sqlite-jdbc 实现；安卓版基于系统 SQLite / Room 实现。
 */
public interface SqliteAccess extends AutoCloseable {

    /**
     * 执行只读查询，按结果集列名返回行数据（值为 Number/String 等基础类型）。
     */
    List<Map<String, Object>> query(String sql) throws Exception;

    @Override
    void close();
}
