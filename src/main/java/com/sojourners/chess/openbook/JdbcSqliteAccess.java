package com.sojourners.chess.openbook;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 桌面版 SqliteAccess 实现（sqlite-jdbc）。仅桌面模块使用，不进入 core。
 */
public class JdbcSqliteAccess implements SqliteAccess {

    private final Connection connection;

    public JdbcSqliteAccess(String bookPath) {
        try {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + bookPath);
        } catch (Exception e) {
            throw new RuntimeException("打开 SQLite 开局库失败: " + bookPath, e);
        }
    }

    @Override
    public List<Map<String, Object>> query(String sql) throws Exception {
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= n; i++) {
                    row.put(md.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
