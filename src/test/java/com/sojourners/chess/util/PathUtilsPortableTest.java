package com.sojourners.chess.util;

import java.io.File;

public class PathUtilsPortableTest {

    public static void main(String[] args) {
        File appDir = new File("target/path-test/TCHESS").getAbsoluteFile();
        File bundledEngine = new File(appDir, "engine/pikafish.exe");
        String relativePath = new File("engine", "pikafish.exe").getPath();

        assertEquals(relativePath, PathUtils.toPortablePath(bundledEngine.getPath(), appDir));
        assertSameFile(bundledEngine, PathUtils.resolveAppPath(relativePath, appDir));

        File externalEngine = new File(appDir.getParentFile(), "external/pikafish.exe").getAbsoluteFile();
        assertEquals(externalEngine.toPath().normalize().toString(),
                PathUtils.toPortablePath(externalEngine.getPath(), appDir));
        assertSameFile(externalEngine, PathUtils.resolveAppPath(externalEngine.getPath(), appDir));
    }

    private static void assertSameFile(File expected, File actual) {
        assertEquals(expected.toPath().toAbsolutePath().normalize().toString(),
                actual.toPath().toAbsolutePath().normalize().toString());
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected '" + expected + "' but got '" + actual + "'.");
        }
    }
}
