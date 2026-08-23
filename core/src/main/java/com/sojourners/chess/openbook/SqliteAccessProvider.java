package com.sojourners.chess.openbook;

import java.util.function.Function;

/**
 * SqliteAccess 工厂定位器。
 * 宿主应用启动时必须调用 {@link #setFactory(Function)} 注入平台实现，
 * 例如桌面版：{@code SqliteAccessProvider.setFactory(JdbcSqliteAccess::new);}。
 */
public final class SqliteAccessProvider {

    private static volatile Function<String, SqliteAccess> factory;

    private SqliteAccessProvider() {
    }

    public static void setFactory(Function<String, SqliteAccess> f) {
        factory = f;
    }

    public static boolean isInitialized() {
        return factory != null;
    }

    public static SqliteAccess open(String bookPath) throws Exception {
        Function<String, SqliteAccess> f = factory;
        if (f == null) {
            throw new IllegalStateException("SqliteAccess 工厂未初始化：请在应用启动时调用 SqliteAccessProvider.setFactory(...)");
        }
        return f.apply(bookPath);
    }
}
