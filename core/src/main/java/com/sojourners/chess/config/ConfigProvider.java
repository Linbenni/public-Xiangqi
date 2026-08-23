package com.sojourners.chess.config;

/**
 * 应用配置的静态获取入口（服务定位器）。
 * 宿主程序必须在启动时调用 {@link #set(AppConfig)} 注入实现，
 * 否则核心层相关功能将抛出 IllegalStateException。
 */
public final class ConfigProvider {

    private static volatile AppConfig instance;

    private ConfigProvider() {
    }

    public static void set(AppConfig config) {
        instance = config;
    }

    public static AppConfig get() {
        AppConfig c = instance;
        if (c == null) {
            throw new IllegalStateException("AppConfig 未初始化：请在应用启动时调用 ConfigProvider.set(...)");
        }
        return c;
    }

    public static boolean isInitialized() {
        return instance != null;
    }
}
