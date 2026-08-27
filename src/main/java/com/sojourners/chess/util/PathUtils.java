package com.sojourners.chess.util;

import com.sun.jna.Platform;

import java.io.File;
import java.nio.file.Path;

/**
 * Path 工具类
 */
public class PathUtils {
    public static String getJarPath() {
        try {
            String path = PathUtils.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            path = java.net.URLDecoder.decode(path, "UTF-8");
            if (Platform.isWindows() && path.startsWith("/")) {
                path = path.substring(1);
            }
            int i = path.lastIndexOf("/");
            if (i >= 0) {
                path = path.substring(0, i + 1);
            }
            return path;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取 path 的父目录
     * @param path
     * @return
     */
    public static File getParentDir(String path) {
        return new File(path).getParentFile();
    }

    public static File resolveAppPath(String path) {
        return resolveAppPath(path, new File(getJarPath()));
    }

    static File resolveAppPath(String path, File appDir) {
        Path configuredPath = Path.of(path);
        if (!configuredPath.isAbsolute()) {
            configuredPath = appDir.toPath().resolve(configuredPath);
        }
        return configuredPath.toAbsolutePath().normalize().toFile();
    }

    public static String toPortablePath(String path) {
        return toPortablePath(path, new File(getJarPath()));
    }

    static String toPortablePath(String path, File appDir) {
        if (path == null || path.isBlank()) {
            return path;
        }

        Path appPath = appDir.toPath().toAbsolutePath().normalize();
        Path configuredPath = Path.of(path);
        if (!configuredPath.isAbsolute()) {
            configuredPath = appPath.resolve(configuredPath);
        }
        configuredPath = configuredPath.toAbsolutePath().normalize();

        if (configuredPath.startsWith(appPath)) {
            return appPath.relativize(configuredPath).toString();
        }
        return configuredPath.toString();
    }

    public static boolean isImage(String path) {
        String[] paths = path.split("\\.");
        String suffix = paths[paths.length - 1].toLowerCase();
        if (suffix.equals("png") || suffix.equals("jpg") || suffix.equals("jpeg") || suffix.equals("bmp")) {
            return true;
        }
        return false;
    }

    public static String getDotExtension(File file) {
        String name = file.getName();
        int idx = name.lastIndexOf('.');
        if (idx > 0 && idx < name.length() - 1) {
            return name.substring(idx + 1);
        }
        return "";
    }
}
