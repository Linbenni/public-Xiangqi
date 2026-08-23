package com.sojourners.chess.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FenUtilsTest {

    private static final String INITIAL_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR";

    private static char[][] fenToBoard(String fen) {
        return XiangqiUtils.fenToBoard(fen);
    }

    @Test
    void initialBoardFenRoundTrip() {
        char[][] board = fenToBoard(INITIAL_FEN);
        assertEquals(INITIAL_FEN, FenUtils.fenCode(board, null));
    }

    @Test
    void fenCodeAppendsSideToMove() {
        char[][] board = fenToBoard(INITIAL_FEN);
        assertEquals(INITIAL_FEN + " w - - 0 1", FenUtils.fenCode(board, true));
        assertEquals(INITIAL_FEN + " b - - 0 1", FenUtils.fenCode(board, false));
    }

    @Test
    void emptyBoardEncodesRunsOfNine() {
        char[][] board = new char[10][9];
        for (char[] row : board) {
            java.util.Arrays.fill(row, ' ');
        }
        assertEquals("9/9/9/9/9/9/9/9/9/9", FenUtils.fenCode(board, null));
    }

    @Test
    void stepForEngineCoordinateFormat() {
        // 编码规则：('a'+x) + (9-y)，行号自上而下
        assertEquals("a9b9", FenUtils.stepForEngine(0, 0, 1, 0));
        assertEquals("b0h9", FenUtils.stepForEngine(1, 9, 7, 0));
    }

    @Test
    void translateMovesAppliesSequenceOnSnapshot() {
        char[][] board = fenToBoard(INITIAL_FEN);
        // 初始局面红方两步常见着法：炮二平五、马8进7 对应引擎坐标 h2e2 / b9c7? 这里只验证快照不被修改
        String before = FenUtils.fenCode(board, null);
        var translated = FenUtils.translateMoves(board, java.util.List.of("h2e2"));
        String after = FenUtils.fenCode(board, null);
        assertEquals(before, after, "translateMoves 不应修改传入局面");
        assertEquals(1, translated.size());
        assertTrue(translated.get(0).length() > 0);
    }
}
