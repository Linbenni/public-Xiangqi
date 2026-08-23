package com.sojourners.chess.mouse;


import com.sojourners.chess.util.LinkDiagnostics;
import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.mouse.NativeMouseEvent;
import org.jnativehook.mouse.NativeMouseInputListener;

import java.util.concurrent.atomic.AtomicBoolean;

public class GlobalMouseListener implements NativeMouseInputListener {

    private MouseListenCallBack cb;

    private final AtomicBoolean listening = new AtomicBoolean(false);

    public void nativeMouseClicked(NativeMouseEvent e) {
        LinkDiagnostics.info("[LINK_MOUSE] event=native_mouse_clicked button=" + e.getButton()
                + " clicks=" + e.getClickCount() + " x=" + e.getX() + " y=" + e.getY()
                + " listening=" + listening.get());
    }

    public void nativeMousePressed(NativeMouseEvent e) {
        LinkDiagnostics.info("[LINK_MOUSE] event=native_mouse_pressed button=" + e.getButton()
                + " x=" + e.getX() + " y=" + e.getY() + " listening=" + listening.get());
        if (listening.get() && e.getButton() == NativeMouseEvent.BUTTON1) {
            this.cb.mouseClick();
        }
    }

    public void nativeMouseReleased(NativeMouseEvent e) {

    }

    public void nativeMouseMoved(NativeMouseEvent e) {

    }

    public void nativeMouseDragged(NativeMouseEvent e) {

    }

    public GlobalMouseListener(MouseListenCallBack cb) {
        this.cb = cb;
    }

    public void startListenMouse() throws NativeHookException {
        if (!listening.compareAndSet(false, true)) {
            return;
        }
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeMouseListener(this);
        } catch (NativeHookException e) {
            listening.set(false);
            throw e;
        }
    }
    public void stopListenMouse() throws NativeHookException {
        if (!listening.compareAndSet(true, false)) {
            return;
        }
        GlobalScreen.removeNativeMouseListener(this);
        GlobalScreen.unregisterNativeHook();
    }

    public boolean isListening() {
        return listening.get();
    }

}
