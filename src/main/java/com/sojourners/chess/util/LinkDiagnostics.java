package com.sojourners.chess.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Mirrors link-recognition diagnostics to the console and a per-run UTF-8 log file.
 */
public final class LinkDiagnostics {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final Path LOG_PATH = resolveLogPath();

    private static final PrintWriter FILE_WRITER = openLogFile();

    static {
        String opened = "[LINK_LOG] event=log_opened path=\"" + LOG_PATH.toAbsolutePath() + "\"";
        System.out.println(opened);
        writeToFile(opened, null);
        if (FILE_WRITER != null) {
            Runtime.getRuntime().addShutdownHook(new Thread(LinkDiagnostics::close, "link-diagnostics-shutdown"));
        }
    }

    private LinkDiagnostics() {
    }

    public static synchronized void info(String message) {
        System.out.println(message);
        writeToFile(message, null);
    }

    public static synchronized void error(String message, Throwable error) {
        System.err.println(message);
        if (error != null) {
            error.printStackTrace(System.err);
        }
        writeToFile(message, error);
    }

    public static Path getLogPath() {
        return LOG_PATH;
    }

    private static Path resolveLogPath() {
        String applicationPath = PathUtils.getJarPath();
        Path basePath = applicationPath == null ? Path.of(System.getProperty("user.dir")) : Path.of(applicationPath);
        return basePath.resolve("logs").resolve("link-recognition.log");
    }

    private static PrintWriter openLogFile() {
        try {
            Files.createDirectories(LOG_PATH.getParent());
            return new PrintWriter(Files.newBufferedWriter(LOG_PATH, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE));
        } catch (IOException e) {
            System.err.println("[LINK_LOG] event=log_open_failed path=\"" + LOG_PATH.toAbsolutePath()
                    + "\" errorType=" + e.getClass().getName() + " errorMessage=" + e.getMessage());
            e.printStackTrace(System.err);
            return null;
        }
    }

    private static void writeToFile(String message, Throwable error) {
        if (FILE_WRITER == null) {
            return;
        }
        FILE_WRITER.println(LocalDateTime.now().format(TIMESTAMP) + " " + message);
        if (error != null) {
            error.printStackTrace(FILE_WRITER);
        }
        FILE_WRITER.flush();
    }

    private static synchronized void close() {
        if (FILE_WRITER != null) {
            FILE_WRITER.flush();
            FILE_WRITER.close();
        }
    }
}
