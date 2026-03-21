package com.sojourners.chess.linker;

import com.sojourners.chess.App;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.jna.User32Extra;
import com.sojourners.chess.mouse.GlobalMouseListener;
import com.sojourners.chess.mouse.MouseListenCallBack;
import com.sojourners.chess.util.PathUtils;
import com.sun.jna.Memory;
import com.sun.jna.platform.win32.*;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.awt.*;
import java.awt.image.BufferedImage;

public class WindowsGraphLinker extends AbstractGraphLinker implements MouseListenCallBack {

    private WinDef.HWND hwnd;
    private GlobalMouseListener listener;
    private double screenScalingFactor;
    private boolean needScaling;
    private Properties prop;
    private SelectState selectState = SelectState.SELECT_WINDOW;
    private Stage maskStage;

    private enum SelectState {
        SELECT_WINDOW,
        SELECT_BOARD_AREA
    }

    public WindowsGraphLinker(LinkerCallBack callBack) throws AWTException {
        super(callBack);
        this.listener = new GlobalMouseListener(this);
        this.prop = Properties.getInstance();
        // 分辨率缩放系数
        this.screenScalingFactor = getScreenScalingFactor();
    }

    @Override
    public void getTargetWindowId() {
        try {
            this.selectState = SelectState.SELECT_WINDOW;
            this.listener.startListenMouse();
            selectCursor();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public void mouseClick() {
        try {
            long[] getPos = new long[1];
            User32Extra.INSTANCE.GetCursorPos(getPos);
            if (this.selectState == SelectState.SELECT_WINDOW) {
                this.hwnd = User32Extra.INSTANCE.WindowFromPoint(getPos[0]);
                this.needScaling = needScaling(this.hwnd);
                if (prop.isLinkUseManualBoardRegion() && !prop.hasLinkBoardArea()) {
                    this.selectState = SelectState.SELECT_BOARD_AREA;
                    openBoardAreaMask();
                } else {
                    finishSelectionAndScan();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void mousePress(int x, int y) {
    }

    @Override
    public void mouseRelease(int x, int y) {
    }

    private void finishSelectionAndScan() throws Exception {
        this.listener.stopListenMouse();
        restoreCursor();
        scan();
    }

    private void saveManualBoardArea(Point p1, Point p2) {
        if (p1 == null || p2 == null) {
            return;
        }
        java.awt.Rectangle windowPos = getTargetWindowPosition();
        if (windowPos == null || windowPos.width <= 0 || windowPos.height <= 0) {
            return;
        }
        int left = Math.min(p1.x, p2.x) - windowPos.x;
        int top = Math.min(p1.y, p2.y) - windowPos.y;
        int right = Math.max(p1.x, p2.x) - windowPos.x;
        int bottom = Math.max(p1.y, p2.y) - windowPos.y;
        if (left < 0) left = 0;
        if (top < 0) top = 0;
        if (right > windowPos.width) right = windowPos.width;
        if (bottom > windowPos.height) bottom = windowPos.height;
        int width = right - left;
        int height = bottom - top;
        if (width < 10 || height < 10) {
            return;
        }

        prop.setLinkBoardAreaXRatio(left * 1.0 / windowPos.width);
        prop.setLinkBoardAreaYRatio(top * 1.0 / windowPos.height);
        prop.setLinkBoardAreaWRatio(width * 1.0 / windowPos.width);
        prop.setLinkBoardAreaHRatio(height * 1.0 / windowPos.height);
        prop.save();
    }

    private void openBoardAreaMask() throws Exception {
        this.listener.stopListenMouse();
        restoreCursor();
        Platform.runLater(() -> {
            try {
                closeMaskStage();

                java.awt.Rectangle windowPos = getTargetWindowPosition();
                if (windowPos == null || windowPos.width <= 0 || windowPos.height <= 0) {
                    scan();
                    return;
                }

                Stage stage = new Stage(StageStyle.TRANSPARENT);
                stage.initOwner(App.getMainStage());
                stage.initModality(Modality.NONE);
                stage.setAlwaysOnTop(true);
                stage.setFullScreenExitHint("");
                stage.setFullScreen(false);

                javafx.geometry.Rectangle2D bounds = Screen.getPrimary().getBounds();
                Pane root = new Pane();
                root.setPickOnBounds(true);
                root.setStyle("-fx-background-color: rgba(0,0,0,0.25);");

                javafx.scene.shape.Rectangle windowFrame = new javafx.scene.shape.Rectangle(windowPos.x, windowPos.y, windowPos.width, windowPos.height);
                windowFrame.setFill(Color.color(1, 1, 1, 0.08));
                windowFrame.setStroke(Color.web("#9FE870"));
                windowFrame.getStrokeDashArray().addAll(10.0, 8.0);

                javafx.scene.shape.Rectangle selection = new javafx.scene.shape.Rectangle();
                selection.setVisible(false);
                selection.setFill(Color.color(1, 1, 1, 0.12));
                selection.setStroke(Color.web("#F7D154"));
                selection.setStrokeWidth(2);

                Label tip = new Label("拖拽框选棋盘区域（可为长方形），回车确认，Esc 取消");
                tip.setTextFill(Color.WHITE);
                tip.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-background-color: rgba(0,0,0,0.45); -fx-padding: 10 16;");

                StackPane tipWrap = new StackPane(tip);
                tipWrap.setMouseTransparent(true);
                tipWrap.setAlignment(Pos.TOP_CENTER);
                tipWrap.setPrefWidth(bounds.getWidth());
                tipWrap.setLayoutY(Math.max(24, windowPos.y - 60));

                final double[] startX = new double[1];
                final double[] startY = new double[1];
                final boolean[] hasSelection = new boolean[1];

                root.setOnMousePressed(event -> {
                    if (!windowPos.contains((int) event.getScreenX(), (int) event.getScreenY())) {
                        return;
                    }
                    startX[0] = event.getScreenX();
                    startY[0] = event.getScreenY();
                    hasSelection[0] = true;
                    selection.setVisible(true);
                    updateSelection(selection, windowPos, startX[0], startY[0], event.getScreenX(), event.getScreenY());
                });

                root.setOnMouseDragged(event -> {
                    if (!hasSelection[0]) {
                        return;
                    }
                    updateSelection(selection, windowPos, startX[0], startY[0], event.getScreenX(), event.getScreenY());
                });

                root.setOnMouseReleased(event -> {
                    if (!hasSelection[0]) {
                        return;
                    }
                    updateSelection(selection, windowPos, startX[0], startY[0], event.getScreenX(), event.getScreenY());
                });

                root.setOnKeyPressed(event -> {
                    if (event.getCode() == KeyCode.ESCAPE) {
                        closeMaskStage();
                    } else if (event.getCode() == KeyCode.ENTER && selection.isVisible() && selection.getWidth() > 10 && selection.getHeight() > 10) {
                        Point p1 = new Point((int) Math.round(selection.getX()), (int) Math.round(selection.getY()));
                        Point p2 = new Point((int) Math.round(selection.getX() + selection.getWidth()), (int) Math.round(selection.getY() + selection.getHeight()));
                        saveManualBoardArea(p1, p2);
                        closeMaskStage();
                        scan();
                    }
                });

                root.getChildren().addAll(windowFrame, selection, tipWrap);

                Scene scene = new Scene(root, bounds.getWidth(), bounds.getHeight(), Color.TRANSPARENT);
                stage.setScene(scene);
                stage.setX(bounds.getMinX());
                stage.setY(bounds.getMinY());
                stage.show();
                root.requestFocus();

                this.maskStage = stage;
            } catch (Exception e) {
                e.printStackTrace();
                scan();
            }
        });
    }

    private void closeMaskStage() {
        if (this.maskStage != null) {
            this.maskStage.close();
            this.maskStage = null;
        }
    }

    /**
     * 在目标窗口客户区内更新选框。历史上取 min(|dx|,|dy|) 强制正方形，非正方形棋盘会被裁切；现改为任意轴对齐矩形。
     */
    private void updateSelection(javafx.scene.shape.Rectangle selection, java.awt.Rectangle windowPos, double anchorX, double anchorY, double currentX, double currentY) {
        double winL = windowPos.x;
        double winT = windowPos.y;
        double winR = windowPos.x + windowPos.width;
        double winB = windowPos.y + windowPos.height;

        double ax = clamp(anchorX, winL, winR);
        double ay = clamp(anchorY, winT, winB);
        double cx = clamp(currentX, winL, winR);
        double cy = clamp(currentY, winT, winB);

        double left = Math.min(ax, cx);
        double top = Math.min(ay, cy);
        double right = Math.max(ax, cx);
        double bottom = Math.max(ay, cy);

        selection.setX(left);
        selection.setY(top);
        selection.setWidth(Math.max(0, right - left));
        selection.setHeight(Math.max(0, bottom - top));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean needScaling(WinDef.HWND hwnd) {
        // 获取系统DPI
        int systemDpi = User32Extra.INSTANCE.GetDpiForSystem();
        // 通过窗口句柄获取当前窗口的DPI
        int windowDpi = User32Extra.INSTANCE.GetDpiForWindow(hwnd);
        // 比较系统DPI和窗口DPI是否相同，如果不同则需要缩放处理
        return systemDpi != windowDpi;
    }

    @Override
    public java.awt.Rectangle getTargetWindowPosition() {
        WinDef.RECT rect = new WinDef.RECT();
        User32.INSTANCE.GetClientRect(hwnd, rect);
        WinDef.POINT point = new WinDef.POINT();
        point.x = 0;
        point.y = 0;
        User32Extra.INSTANCE.ClientToScreen(hwnd, point);
        java.awt.Rectangle rectangle = new java.awt.Rectangle(point.x, point.y, rect.right - rect.left, rect.bottom - rect.top);
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
        sleepMouseMoveDelay();
        leftClick(p2.x, p2.y);
    }

    private void leftClick(int x, int y) {
        User32.INSTANCE.PostMessage(hwnd, 0x0200, new WinDef.WPARAM(1), new WinDef.LPARAM(makeLParam(x, y)));
        User32.INSTANCE.PostMessage(hwnd, 0x0201, new WinDef.WPARAM(1), new WinDef.LPARAM(makeLParam(x, y)));
        sleepMouseClickDelay();
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
            e.printStackTrace();
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
