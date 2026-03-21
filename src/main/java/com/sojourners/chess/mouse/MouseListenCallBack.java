package com.sojourners.chess.mouse;

public interface MouseListenCallBack {

    void mouseClick();

    default void mousePress(int x, int y) {
    }

    default void mouseRelease(int x, int y) {
    }
}
