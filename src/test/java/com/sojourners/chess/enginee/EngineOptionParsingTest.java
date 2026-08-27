package com.sojourners.chess.enginee;

import java.util.Map;

public class EngineOptionParsingTest {

    public static void main(String[] args) {
        assertOption("option name MultiPV type spin default 1 min 1 max 128", "MultiPV", "1");
        assertOption("option name Move Overhead type spin default 30 min 0 max 5000", "Move Overhead", "30");
        assertOption("option name Repetition Rule type combo default AsianRule var AsianRule var ChineseRule",
                "Repetition Rule", "AsianRule");
        assertOption("option name Eval File type string default network files/default.nnue",
                "Eval File", "network files/default.nnue");
        assertOption("option name Debug Log File type string default <empty>", "Debug Log File", "");

        assertEquals("1", Engine.normalizeStoredOptionValue("1 min 1 max 128"));
        assertEquals("-5", Engine.normalizeStoredOptionValue("-5 min -10 max 10"));
        assertEquals("file min version.nnue", Engine.normalizeStoredOptionValue("file min version.nnue"));

        if (Engine.parseOptionDefault("option name Hash type spin default 16 min 1 max 65536") != null) {
            throw new AssertionError("Hash must remain controlled by the main window setting.");
        }
    }

    private static void assertOption(String line, String expectedKey, String expectedValue) {
        Map.Entry<String, String> option = Engine.parseOptionDefault(line);
        if (option == null) {
            throw new AssertionError("Option was not parsed: " + line);
        }
        assertEquals(expectedKey, option.getKey());
        assertEquals(expectedValue, option.getValue());
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected '" + expected + "' but got '" + actual + "'.");
        }
    }
}
