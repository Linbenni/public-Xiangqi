package com.sojourners.chess.util;

import java.util.List;

public class XiangqiUtilsRepetitionTest {

    private static final String START_FEN =
            "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";
    private static final String FORCED_CHECK_FEN =
            "2ba1k3/2N1a2n1/4b4/p6Rp/9/9/P8/4RC3/6r2/2BA1K2c b - - 0 1";

    public static void main(String[] args) {
        List<String> oneCycle = List.of("b0c2", "b9c7", "c2b0", "c7b9");
        List<String> twoCycles = List.of(
                "b0c2", "b9c7", "c2b0", "c7b9",
                "b0c2", "b9c7", "c2b0", "c7b9");
        List<String> brokenCycle = List.of(
                "b0c2", "b9c7", "c2b0", "c7b9",
                "b0c2", "b9c7", "c2b0", "g6g5");
        List<String> beforeThirdOccurrence = List.of(
                "b0c2", "b9c7", "c2b0", "c7b9",
                "b0c2", "b9c7", "c2b0");

        assertResult(false, XiangqiUtils.isThreefoldRepetition(START_FEN, oneCycle),
                "A second occurrence must not be treated as threefold repetition.");
        assertResult(true, XiangqiUtils.isThreefoldRepetition(START_FEN, twoCycles),
                "A third occurrence must be detected.");
        assertResult(false, XiangqiUtils.isThreefoldRepetition(START_FEN, brokenCycle),
                "A different current position must not be treated as repeated.");
        assertResult(true, XiangqiUtils.wouldCauseThreefoldRepetition(
                        START_FEN, beforeThirdOccurrence, "c7b9"),
                "The move returning to the initial position must be rejected before it is played.");
        assertResult(false, XiangqiUtils.wouldCauseThreefoldRepetition(
                        START_FEN, beforeThirdOccurrence, "g6g5"),
                "A move to a different position must remain available to the engine.");

        List<String> beforeThirdCheck = List.of(
                "g1g0", "f0f1", "g0g1", "f1f0");
        List<String> beforeFourthCheck = List.of(
                "g1g0", "f0f1", "g0g1", "f1f0", "g1g0", "f0f1");
        assertResult(false, XiangqiUtils.wouldForceThreefoldRepetition(
                        FORCED_CHECK_FEN, beforeThirdCheck, "g1g0"),
                "The third consecutive check must remain allowed.");
        assertResult(true, XiangqiUtils.wouldForceThreefoldRepetition(
                        FORCED_CHECK_FEN, beforeFourthCheck, "g0g1"),
                "The fourth consecutive check must be rejected when the only reply completes threefold repetition.");
        assertResult(false, XiangqiUtils.wouldForceThreefoldRepetition(
                        FORCED_CHECK_FEN, beforeFourthCheck, "g0h0"),
                "A non-repeating alternative must remain available to the engine.");
    }

    private static void assertResult(boolean expected, boolean actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " Expected " + expected + " but got " + actual + ".");
        }
    }
}
