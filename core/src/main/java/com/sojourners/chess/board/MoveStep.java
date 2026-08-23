package com.sojourners.chess.board;

/**
 * 一步着法（从桌面 ChessBoard.Step 下沉为顶层核心类型）。
 */
public class MoveStep {
    BoardPoint start;
    BoardPoint end;

    public MoveStep(BoardPoint start, BoardPoint end) {
        this.start = start;
        this.end = end;
    }

    public BoardPoint getStart() {
        return start;
    }

    public void setStart(BoardPoint start) {
        this.start = start;
    }

    public BoardPoint getEnd() {
        return end;
    }

    public void setEnd(BoardPoint end) {
        this.end = end;
    }
}
