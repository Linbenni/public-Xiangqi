package com.sojourners.chess.board;

/**
 * 棋盘点位（从桌面 ChessBoard.Point 下沉为顶层核心类型，供核心层与界面层共用）。
 */
public class BoardPoint {
    int x;
    int y;

    public BoardPoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }
}
