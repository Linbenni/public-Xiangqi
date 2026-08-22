package com.sojourners.chess.linker;

import com.sojourners.chess.config.Properties;
import com.sojourners.chess.jna.User32Extra;
import com.sojourners.chess.mouse.GlobalMouseListener;
import com.sojourners.chess.mouse.MouseListenCallBack;
import com.sojourners.chess.util.PathUtils;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;

public class WindowsGraphLinker extends AbstractGraphLinker implements MouseListenCallBack {

    private WinDef.HWND hwnd;
    private GlobalMouseListener listener;
    private double screenScalingFactor;
    private boolean needScaling;
    private final AtomicBoolean selectionPending = new AtomicBoolean(false);
    private Thread selectionPollThread;

    public WindowsGraphLinker(LinkerCallBack callBack) throws AWTException {
        super(callBack);
        this.listener = new GlobalMouseListener(this);
        // 分辨率缩放系数
        this.screenScalingFactor = getScreenScalingFactor();
        log("windows_linker_initialized", "screenScale=" + this.screenScalingFactor);
    }

    @Override
    public void getTargetWindowId() {
        if (!selectionPending.compareAndSet(false, true)) {
            log("selection_ignored", "reason=already_pending");
            return;
        }
        try {
            this.listener.startListenMouse();
            selectCursor();
            startTargetSelectionPolling();
            log("selection_ready", "message=click_target_board");

        } catch (Exception e) {
            cancelPendingSelection("start_failed");
            logError("selection_failed", "stage=start_mouse_listener", e);
        }
    }
    @Override
    public void mouseClick() {
        trySelectTarget("native_hook");
    }

    private void trySelectTarget(String source) {
        if (!selectionPending.get()) {
            log("selection_click_ignored", "source=" + source + " reason=selection_not_pending");
            return;
        }
        try {
            long[] getPos = new long[1];
            if (!User32Extra.INSTANCE.GetCursorPos(getPos)) {
                log("selection_failed", "source=" + source + " stage=get_cursor_pos lastError=" + Kernel32.INSTANCE.GetLastError());
                return;
            }
            WinDef.HWND candidate = User32Extra.INSTANCE.WindowFromPoint(getPos[0]);
            int cursorX = (int) getPos[0];
            int cursorY = (int) (getPos[0] >> 32);
            if (candidate == null) {
                log("selection_failed", "source=" + source + " stage=window_from_point cursor=" + cursorX + "," + cursorY + " reason=null_hwnd");
                return;
            }

            trySelectWindow(candidate, source, cursorX, cursorY);

        } catch (Exception e) {
            logError("selection_failed", "source=" + source + " stage=resolve_target_window", e);
        }
    }

    private void trySelectWindow(WinDef.HWND candidate, String source, int cursorX, int cursorY) {
        try {
            long targetProcessId = getWindowProcessId(candidate);
            if (targetProcessId == ProcessHandle.current().pid()) {
                log("target_window_rejected", "source=" + source + " reason=self_window cursor=" + cursorX + "," + cursorY + " " + windowDetails(candidate));
                cancelPendingSelection("self_window");
                notifySelectionCancelled();
                return;
            }

            if (!selectionPending.compareAndSet(true, false)) {
                log("selection_click_ignored", "source=" + source + " reason=claimed_by_other_detector");
                return;
            }

            this.hwnd = candidate;
            stopTargetSelectionMechanisms("target_selected");

            this.needScaling = needScaling(this.hwnd);
            log("target_window_selected", "source=" + source + " cursor=" + cursorX + "," + cursorY + " " + windowDetails(this.hwnd)
                    + " screenScale=" + screenScalingFactor + " needScaling=" + needScaling);

            scan();

        } catch (Exception e) {
            logError("selection_failed", "source=" + source + " stage=resolve_target_window", e);
        }
    }

    @Override
    public void stop() {
        cancelPendingSelection("link_stopped");
        super.stop();
    }

    private void cancelPendingSelection(String reason) {
        if (!selectionPending.compareAndSet(true, false)) {
            return;
        }
        stopTargetSelectionMechanisms(reason);
        log("selection_cancelled", "reason=" + reason);
    }

    private void startTargetSelectionPolling() {
        this.selectionPollThread = Thread.ofVirtual().name("link-target-selection-poll").unstarted(() -> {
            boolean wasPressed = isLeftButtonPressed();
            while (selectionPending.get() && !Thread.currentThread().isInterrupted()) {
                WinDef.HWND foregroundWindow = User32.INSTANCE.GetForegroundWindow();
                if (foregroundWindow != null
                        && getWindowProcessId(foregroundWindow) != ProcessHandle.current().pid()) {
                    log("selection_foreground_target_detected", "source=foreground_poll " + windowDetails(foregroundWindow));
                    trySelectWindow(foregroundWindow, "foreground_poll", -1, -1);
                    continue;
                }

                boolean pressed = isLeftButtonPressed();
                if (pressed && !wasPressed) {
                    log("selection_poll_detected_press", "source=async_key_poll");
                    trySelectTarget("async_key_poll");
                }
                wasPressed = pressed;
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        this.selectionPollThread.start();
    }

    private boolean isLeftButtonPressed() {
        return (User32Extra.INSTANCE.GetAsyncKeyState(0x01) & 0x8000) != 0;
    }

    private void stopTargetSelectionMechanisms(String reason) {
        try {
            this.listener.stopListenMouse();
        } catch (Exception e) {
            logError("selection_cancel_failed", "reason=" + reason + " stage=stop_mouse_listener", e);
        } finally {
            if (selectionPollThread != null && selectionPollThread != Thread.currentThread()) {
                selectionPollThread.interrupt();
            }
            restoreCursor();
        }
    }

    private boolean needScaling(WinDef.HWND hwnd) {
        // 获取系统DPI
        int systemDpi = User32Extra.INSTANCE.GetDpiForSystem();
        // 通过窗口句柄获取当前窗口的DPI
        int windowDpi = User32Extra.INSTANCE.GetDpiForWindow(hwnd);
        log("target_dpi_detected", "systemDpi=" + systemDpi + " windowDpi=" + windowDpi);
        // 比较系统DPI和窗口DPI是否相同，如果不同则需要缩放处理
        return systemDpi != windowDpi;
    }

    private static long getWindowProcessId(WinDef.HWND hwnd) {
        IntByReference processId = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, processId);
        return Integer.toUnsignedLong(processId.getValue());
    }

    private static String windowDetails(WinDef.HWND hwnd) {
        char[] title = new char[256];
        char[] className = new char[256];
        User32.INSTANCE.GetWindowText(hwnd, title, title.length);
        User32.INSTANCE.GetClassName(hwnd, className, className.length);
        IntByReference processId = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, processId);
        WinDef.RECT windowRect = new WinDef.RECT();
        WinDef.RECT clientRect = new WinDef.RECT();
        User32.INSTANCE.GetWindowRect(hwnd, windowRect);
        User32.INSTANCE.GetClientRect(hwnd, clientRect);
        return "hwnd=" + Pointer.nativeValue(hwnd.getPointer())
                + " pid=" + processId.getValue()
                + " class=" + quote(Native.toString(className))
                + " title=" + quote(Native.toString(title))
                + " windowRect=" + windowRect.left + "," + windowRect.top + "," + (windowRect.right - windowRect.left) + "x" + (windowRect.bottom - windowRect.top)
                + " clientSize=" + (clientRect.right - clientRect.left) + "x" + (clientRect.bottom - clientRect.top);
    }

    private static String quote(String value) {
        return '"' + value.replace('"', '\'').replace('\n', ' ').replace('\r', ' ') + '"';
    }

    @Override
    public Rectangle getTargetWindowPosition() {
        WinDef.RECT rect = new WinDef.RECT();
        User32.INSTANCE.GetWindowRect(hwnd, rect);
        Rectangle rectangle = rect.toRectangle();
        // windows缩放处理
        rectangle.x /= screenScalingFactor;
        rectangle.y /= screenScalingFactor;
        rectangle.width /= screenScalingFactor;
        rectangle.height /= screenScalingFactor;
        return rectangle;
    }

    private double getScreenScalingFactor() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        return gd.getDefaultConfiguration().getDefaultTransform().getScaleX();
    }

    @Override
    public BufferedImage screenshotByBack(Rectangle windowPos) {
        log("background_capture_requested", "rect=" + (windowPos == null ? "full_client" : windowPos.x + "," + windowPos.y + "," + windowPos.width + "x" + windowPos.height));
        return capture(this.hwnd, windowPos);
    }

    @Override
    public void mouseClickByBack(Point p1, Point p2) {
        // 处理windows缩放问题
        if (needScaling) {
            p1.x *= screenScalingFactor;
            p1.y *= screenScalingFactor;
            p2.x *= screenScalingFactor;
            p2.y *= screenScalingFactor;
        }

        leftClick(p1.x, p1.y);
        if (Properties.getInstance().getMouseMoveDelay() > 0) {
            sleep(Properties.getInstance().getMouseMoveDelay());
        }
        leftClick(p2.x, p2.y);
    }

    private void leftClick(int x, int y) {
        User32.INSTANCE.PostMessage(hwnd, 0x0200, new WinDef.WPARAM(1), new WinDef.LPARAM(makeLParam(x, y)));
        User32.INSTANCE.PostMessage(hwnd, 0x0201, new WinDef.WPARAM(1), new WinDef.LPARAM(makeLParam(x, y)));
        if (Properties.getInstance().getMouseClickDelay() > 0) {
            sleep(Properties.getInstance().getMouseClickDelay());
        }
        User32.INSTANCE.PostMessage(hwnd, 0x0202, new WinDef.WPARAM(0), new WinDef.LPARAM(makeLParam(x, y)));
    }
    private int makeLParam(int loWord, int hiWord) {
        return (hiWord << 16) | (loWord & 0xFFFF);
    }

    private BufferedImage capture(WinDef.HWND hWnd, Rectangle rect) {
        // 创建与窗口相关联的设备上下文和一个内存设备上下文以执行离屏渲染
        WinDef.HDC hdcWindow = User32.INSTANCE.GetDC(hWnd);
        WinDef.HDC hdcMemDC = GDI32.INSTANCE.CreateCompatibleDC(hdcWindow);
        try {
            int width, height;
            WinDef.RECT bounds = new WinDef.RECT();
            User32.INSTANCE.GetClientRect(hWnd, bounds);
            width = bounds.right - bounds.left;
            height = bounds.bottom - bounds.top;
            // 处理windows缩放问题
            if (needScaling) {
                width /= screenScalingFactor;
                height /= screenScalingFactor;
            }
            // 创建兼容的位图，并且将其选入内存设备上下文
            WinDef.HBITMAP hBitmap = GDI32.INSTANCE.CreateCompatibleBitmap(hdcWindow, width, height);
            WinNT.HANDLE hOld = GDI32.INSTANCE.SelectObject(hdcMemDC, hBitmap);
            // 请求窗口自行完成绘制工作
            if (!User32.INSTANCE.PrintWindow(hWnd, hdcMemDC, 0x1 | 0x2)) {
                log("background_capture_failed", "stage=print_window flags=3 lastError=" + Kernel32.INSTANCE.GetLastError()
                        + " " + windowDetails(hWnd));
                return null;
            }

            // 将所绘制的位图转化为Java缓冲图片（BufferedImage）
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            WinGDI.BITMAPINFO bmi = new WinGDI.BITMAPINFO();
            bmi.bmiHeader.biWidth = width;
            bmi.bmiHeader.biHeight = -height; // 注意：biHeight为负表示顶向下DIB
            bmi.bmiHeader.biPlanes = 1;
            bmi.bmiHeader.biBitCount = 32;
            bmi.bmiHeader.biCompression = WinGDI.BI_RGB;

            Memory buffer = new Memory(width * height * 4);
            GDI32.INSTANCE.GetDIBits(hdcMemDC, hBitmap, 0, height, buffer, bmi, WinGDI.DIB_RGB_COLORS);

            int[] data = buffer.getIntArray(0, width * height);
            image.setRGB(0, 0, width, height, data, 0, width);

            // 清理资源
            GDI32.INSTANCE.SelectObject(hdcMemDC, hOld);
            GDI32.INSTANCE.DeleteObject(hBitmap);

            if (rect != null) {
                width = (int) rect.getWidth();
                height = (int) rect.getHeight();
                int x = rect.x;
                int y = rect.y;
                image = image.getSubimage(x, y, width, height);
            }

            return image;

        } catch (Exception e) {
            logError("background_capture_failed", "stage=convert_bitmap rect="
                    + (rect == null ? "full_client" : rect.x + "," + rect.y + "," + rect.width + "x" + rect.height), e);
            return null;
        } finally {
            // 清理设备上下文对象
            GDI32.INSTANCE.DeleteDC(hdcMemDC);
            User32.INSTANCE.ReleaseDC(hWnd, hdcWindow);
        }
    }

    private void selectCursor() {
        WinDef.HCURSOR h = User32Extra.INSTANCE.LoadCursorFromFileA(PathUtils.getJarPath() + "ui/circle.ico");
        User32Extra.INSTANCE.SetSystemCursor(h, new WinDef.DWORD(32512));
    }

    private void restoreCursor() {
        User32Extra.INSTANCE.SystemParametersInfoA(87, 0, 0, 2);
    }
}
