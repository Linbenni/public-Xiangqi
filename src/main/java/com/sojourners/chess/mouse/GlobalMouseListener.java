package com.sojourners.chess.mouse;


import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.mouse.NativeMouseEvent;
import org.jnativehook.mouse.NativeMouseInputListener;

public class GlobalMouseListener implements NativeMouseInputListener {

    private MouseListenCallBack cb;

    public void nativeMouseClicked(NativeMouseEvent e) {
        System.out.println("Mouse Clicked: " + e.getClickCount());

        this.cb.mouseClick();
    }

    public void nativeMousePressed(NativeMouseEvent e) {
        this.cb.mousePress(e.getX(), e.getY());
    }

    public void nativeMouseReleased(NativeMouseEvent e) {
        this.cb.mouseRelease(e.getX(), e.getY());
    }

    public void nativeMouseMoved(NativeMouseEvent e) {

    }

    public void nativeMouseDragged(NativeMouseEvent e) {

    }

    public GlobalMouseListener(MouseListenCallBack cb) {
        this.cb = cb;
    }

    public void startListenMouse() throws NativeHookException {
        GlobalScreen.registerNativeHook();
        GlobalScreen.addNativeMouseListener(this);
        GlobalScreen.addNativeMouseMotionListener(this);
    }
    public void stopListenMouse() throws NativeHookException {
        GlobalScreen.removeNativeMouseListener(this);
        GlobalScreen.removeNativeMouseMotionListener(this);
        GlobalScreen.unregisterNativeHook();
    }

}
