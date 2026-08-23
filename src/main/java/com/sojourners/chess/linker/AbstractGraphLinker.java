package com.sojourners.chess.linker;

import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.util.LinkDiagnostics;
import com.sojourners.chess.util.XiangqiUtils;
import com.sojourners.chess.yolo.OnnxModel;
import com.sojourners.chess.yolo.Yolo11Model;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;


public abstract class AbstractGraphLinker implements GraphLinker, Runnable {

    private static final AtomicLong SESSION_SEQUENCE = new AtomicLong();
    private static final int MAX_AUTO_CLICK_ATTEMPTS = 3;

    /**
     * 扫描线程
     */
    private Thread thread;
    /**
     * 棋盘区域
     */
    private Rectangle boardPos;
    /**
     * 识别棋盘 暂存
     */
    private char[][] board2 = new char[10][9];

    private char[][] board1 = new char[10][9];

    private OnnxModel aiModel;

    private LinkerCallBack callBack;

    private Robot robot;

    private int count;

    private volatile boolean pause;

    private Properties prop;

    private volatile String sessionId = "not-started";

    private long boardSearchAttempt;

    private long boardReadAttempt;

    private Action lastAutoClickAction;

    private String lastAutoClickBoardLayout;

    private int autoClickAttemptCount;

    private boolean autoClickSuppressedLogged;

    public AbstractGraphLinker(LinkerCallBack callBack) throws AWTException {
        this.callBack = callBack;
        robot = new Robot();
        this.count = 0;
        this.aiModel = new Yolo11Model();
        this.prop = Properties.getInstance();
        this.pause = false;
    }

    /**
     * 开始连线
     */
    @Override
    public void start() {
        this.sessionId = Long.toString(SESSION_SEQUENCE.incrementAndGet());
        this.boardSearchAttempt = 0;
        this.boardReadAttempt = 0;
        resetAutoClickRetry();
        log("selection_started", "backMode=" + prop.isLinkBackMode()
                + " scanMs=" + prop.getLinkScanTime()
                + " modelThreads=" + prop.getLinkThreadNum()
                + " animationConfirm=" + prop.isLinkAnimation());
        getTargetWindowId();
    }

    void scan() {
        this.thread = Thread.ofVirtual().name("link-recognition-" + sessionId).unstarted(this);
        log("scan_thread_starting", "thread=" + this.thread.getName());
        this.thread.start();
    }

    protected final void log(String event, String fields) {
        LinkDiagnostics.info("[LINK] session=" + sessionId + " event=" + event
                + (fields == null || fields.isBlank() ? "" : " " + fields));
    }

    protected final void logError(String event, String fields, Throwable error) {
        LinkDiagnostics.error("[LINK] session=" + sessionId + " event=" + event
                + (fields == null || fields.isBlank() ? "" : " " + fields)
                + " errorType=" + error.getClass().getName()
                + " errorMessage=" + String.valueOf(error.getMessage()).replace('\n', ' ').replace('\r', ' '), error);
    }

    protected final void notifySelectionCancelled() {
        callBack.linkerSelectionCancelled();
    }

    private boolean isSame(char[][] board1, char[][] board2) {
        if (board1 == null || board2 == null) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (board1[i][j] != board2[i][j]) {
                    return false;
                }
            }
        }
        return true;
    }

    public void pause() {
        this.pause = true;
    }
    public void resume() {
        this.pause = false;
    }

    @Override
    public void run() {
        log("scan_thread_started", "thread=" + Thread.currentThread().getName());
        while (!Thread.currentThread().isInterrupted()) {
            if (!findBoardPosition()) {
                sleep(1000);
                continue;
            }
            if (!initChessBoard()) {
                sleep(1000);
                continue;
            }
            while (!Thread.currentThread().isInterrupted()) {
                sleep(prop.getLinkScanTime());
                if (!callBack.isThinking() && !pause) {

                    if (!findChessBoard(board2)) {
                        continue;
                    }

                    boolean isReverse;
                    try {
                        isReverse = reverse(board2);
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }

                    if (isSame(board2, callBack.getEngineBoard())) {
                        resetAutoClickRetry();
                        continue;
                    }

                    Action action = compareBoard(board2, callBack.getEngineBoard(), isReverse, callBack.isWatchMode());
                    if (prop.isLinkAnimation() && needConfirm(board2, callBack.getEngineBoard(), action)) {
                        boolean f = false;
                        do {
                            char[][] tmp = board1;
                            board1 = board2;
                            board2 = tmp;

                            if (!findChessBoard(board2)) {
                                f = true;
                                break;
                            }

                            try {
                                isReverse = reverse(board2);
                            } catch (Exception e) {
                                e.printStackTrace();
                                f = true;
                                break;
                            }
                        } while (!isSame(board1, board2));

                        if (f) continue;

                        action = compareBoard(board2, callBack.getEngineBoard(), isReverse, callBack.isWatchMode());
                    }

                    if (action != null) {
                        System.out.println("action " + action);
                        if (action.flag == 1) {
                            resetAutoClickRetry();
                            callBack.linkerMove(action.x1, action.y1, action.x2, action.y2);

                        } else if (action.flag == 2) {
                            if (!beginAutoClickAttempt(action, boardLayout(board2))) {
                                if (!autoClickSuppressedLogged) {
                                    log("auto_click_suppressed", "attempts=" + autoClickAttemptCount + " action=" + action);
                                    autoClickSuppressedLogged = true;
                                }
                                continue;
                            }
                            if (isReverse) {
                                action.y1 = 9 - action.y1;
                                action.y2 = 9 - action.y2;
                                action.x1 = 8 - action.x1;
                                action.x2 = 8 - action.x2;
                            }
                            autoClick(action.x1, action.y1, action.x2, action.y2);

                        } else if (action.flag == 3) {
                            resetAutoClickRetry();
                            break;
                        }
                        if (action.flag == 4) {
                            count++;
                            if (count > 9) {
                                break;
                            }
                        } else {
                            count = 0;
                        }
                    }

                }
            }
        }
    }

    private boolean beginAutoClickAttempt(Action action, String currentBoardLayout) {
        if (!isSameAutoClickAction(lastAutoClickAction, action)
                || !currentBoardLayout.equals(lastAutoClickBoardLayout)) {
            lastAutoClickAction = new Action(action.flag, action.x1, action.y1, action.x2, action.y2);
            lastAutoClickBoardLayout = currentBoardLayout;
            autoClickAttemptCount = 0;
            autoClickSuppressedLogged = false;
        }
        if (autoClickAttemptCount >= MAX_AUTO_CLICK_ATTEMPTS) {
            return false;
        }
        autoClickAttemptCount++;
        log("auto_click_attempt", "attempt=" + autoClickAttemptCount + "/" + MAX_AUTO_CLICK_ATTEMPTS + " action=" + action);
        return true;
    }

    private boolean isSameAutoClickAction(Action action1, Action action2) {
        return action1 != null && action2 != null
                && action1.flag == action2.flag
                && action1.x1 == action2.x1
                && action1.y1 == action2.y1
                && action1.x2 == action2.x2
                && action1.y2 == action2.y2;
    }

    private void resetAutoClickRetry() {
        lastAutoClickAction = null;
        lastAutoClickBoardLayout = null;
        autoClickAttemptCount = 0;
        autoClickSuppressedLogged = false;
    }

    class Action {
        int flag;
        int x1;
        int y1;
        int x2;
        int y2;
        public Action(int flag) {
            this.flag = flag;
        }
        public Action(int flag, int x1, int y1, int x2, int y2) {
            this.flag = flag;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        @Override
        public String toString() {
            return "Action{" +
                    "flag=" + flag +
                    ", x1=" + x1 +
                    ", y1=" + y1 +
                    ", x2=" + x2 +
                    ", y2=" + y2 +
                    '}';
        }
    }

    private boolean needConfirm(char[][] linkBoard, char[][] engineBoard, Action action) {
        if (action == null) {
            return false;
        }
        if (action.flag == 3) {
            return true;
        }
        if (action.flag != 1 || !(linkBoard[action.y2][action.x2] == 'r' || linkBoard[action.y2][action.x2] == 'R' || linkBoard[action.y2][action.x2] == 'c' || linkBoard[action.y2][action.x2] == 'C') || !(engineBoard[action.y2][action.x2] == ' ')) {
            return false;
        }
        if (linkBoard[action.y2][action.x2] == 'r' || linkBoard[action.y2][action.x2] == 'R') {
            int x = -1, y = -1;
            if (action.x1 == action.x2) {
                x = action.x1;
                if (action.y2 > action.y1) {
                    y = action.y2 + 1;
                } else {
                    y = action.y2 - 1;
                }
            }
            if (action.y1 == action.y2) {
                y = action.y1;
                if (action.x2 > action.x1) {
                    x = action.x2 + 1;
                } else {
                    x = action.x2 - 1;
                }
            }
            if (x < 0 || x > 8 || y < 0 || y > 9 || engineBoard[y][x] != ' ' && XiangqiUtils.isRed(engineBoard[action.y1][action.x1]) == XiangqiUtils.isRed(engineBoard[y][x])) {
                return false;
            }
        }
        if (linkBoard[action.y2][action.x2] == 'c' || linkBoard[action.y2][action.x2] == 'C') {
            if (action.x1 == action.x2) {
                int x = action.x1, y;
                int p;
                if (action.y2 > action.y1) {
                    y = action.y2 + 1;
                    p = 1;
                } else {
                    y = action.y2 - 1;
                    p = -1;
                }
                if (y < 0 || y > 9) {
                    return false;
                }
                if (engineBoard[y][x] != ' ') {
                    for (int i = y + p; i >= 0 && i <= 9; i += p) {
                        if (engineBoard[i][x] != ' ' && XiangqiUtils.isRed(engineBoard[i][x]) == XiangqiUtils.isRed(engineBoard[action.y1][action.x1])) {
                            return false;
                        } else if (engineBoard[i][x] != ' ' && XiangqiUtils.isRed(engineBoard[i][x]) != XiangqiUtils.isRed(engineBoard[action.y1][action.x1])) {
                            return true;
                        }
                    }
                    return false;
                }
            }
            if (action.y1 == action.y2) {
                int x, y = action.y1;
                int p;
                if (action.x2 > action.x1) {
                    x = action.x2 + 1;
                    p = 1;
                } else {
                    x = action.x2 - 1;
                    p = -1;
                }
                if (x < 0 || x > 8 || y < 0 || y > 9) {
                    return false;
                }
                if (engineBoard[y][x] != ' ') {
                    for (int j = x + p; j >= 0 && j <= 8; j += p) {
                        if (engineBoard[y][j] != ' ' && XiangqiUtils.isRed(engineBoard[y][j]) == XiangqiUtils.isRed(engineBoard[action.y1][action.x1])) {
                            return false;
                        } else if (engineBoard[y][j] != ' ' && XiangqiUtils.isRed(engineBoard[y][j]) != XiangqiUtils.isRed(engineBoard[action.y1][action.x1])) {
                            return true;
                        }
                    }
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 对比棋盘，计算出当前操作
     * flag： 1对方已走棋，需要同步到引擎
     *      2引擎已走棋，需要同步到目标平台
     *      3识别到新棋局
     *      4可能识别到新棋局
     * @param linkBoard
     * @param engineBoard
     * @param robotBlack
     * @return
     */
    private Action compareBoard(char[][] linkBoard, char[][] engineBoard, boolean robotBlack, boolean analysisMode) {
        int diff1 = 0, diff2 = 0, diff3 = 0;

        List<Point> diffList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (linkBoard[i][j] != engineBoard[i][j]) {
                    diffList.add(new Point(i, j));
                    if (linkBoard[i][j] != ' ' && engineBoard[i][j] != ' ') {
                        diff1++;
                    } else if (linkBoard[i][j] != ' ' && engineBoard[i][j] == ' ') {
                        diff2++;
                    } else {
                        diff3++;
                    }
                }
            }
        }

        if (diff1 > 2 || diff2 >= 2 && diff3 > 2) {
            return new Action(3);
        }

        Action action = null;
        int flag = 0, sum = 0;
        Point from = null, to = null;
        for (int i = 0; i < diffList.size(); i++) {
            for (int j = i + 1; j < diffList.size(); j++) {
                Point p1 = diffList.get(i), p2 = diffList.get(j);
                boolean f = false;
                if (linkBoard[p1.x][p1.y] == engineBoard[p2.x][p2.y] && linkBoard[p1.x][p1.y] != ' ') {
                    if (linkBoard[p2.x][p2.y] == ' ' && engineBoard[p1.x][p1.y] == ' ') {
                        if (analysisMode || robotBlack && XiangqiUtils.isRed(linkBoard[p1.x][p1.y]) || !robotBlack && !XiangqiUtils.isRed(linkBoard[p1.x][p1.y])) {
                            flag = 1;
                            from = p2;
                            to = p1;
                            f = true;
                        } else if (robotBlack && !XiangqiUtils.isRed(linkBoard[p1.x][p1.y]) || !robotBlack && XiangqiUtils.isRed(linkBoard[p1.x][p1.y])) {
                            flag = 2;
                            from = p1;
                            to = p2;
                            f = true;
                        }
                    }
                    if (linkBoard[p2.x][p2.y] == ' ' && engineBoard[p1.x][p1.y] != ' ' && XiangqiUtils.isRed(linkBoard[p1.x][p1.y]) != XiangqiUtils.isRed(engineBoard[p1.x][p1.y])) {
                        flag = 1;
                        from = p2;
                        to = p1;
                        f = true;
                    }
                    if (!analysisMode && engineBoard[p1.x][p1.y] == ' ' && linkBoard[p2.x][p2.y] != ' ' && XiangqiUtils.isRed(engineBoard[p2.x][p2.y]) != XiangqiUtils.isRed(linkBoard[p2.x][p2.y])) {
                        flag = 2;
                        from = p1;
                        to = p2;
                        f = true;
                    }
                }
                if (linkBoard[p2.x][p2.y] == engineBoard[p1.x][p1.y] && linkBoard[p2.x][p2.y] != ' ') {
                    if (linkBoard[p1.x][p1.y] == ' ' && engineBoard[p2.x][p2.y] == ' ') {
                        if (analysisMode || robotBlack && XiangqiUtils.isRed(linkBoard[p2.x][p2.y]) || !robotBlack && !XiangqiUtils.isRed(linkBoard[p2.x][p2.y])) {
                            flag = 1;
                            from = p1;
                            to = p2;
                            f = true;
                        } else if (robotBlack && !XiangqiUtils.isRed(linkBoard[p2.x][p2.y]) || !robotBlack && XiangqiUtils.isRed(linkBoard[p2.x][p2.y])) {
                            flag = 2;
                            from = p2;
                            to = p1;
                            f = true;
                        }
                    }
                    if (linkBoard[p1.x][p1.y] == ' ' && engineBoard[p2.x][p2.y] != ' ' && XiangqiUtils.isRed(linkBoard[p2.x][p2.y]) != XiangqiUtils.isRed(engineBoard[p2.x][p2.y])) {
                        flag = 1;
                        from = p1;
                        to = p2;
                        f = true;
                    }
                    if (!analysisMode && engineBoard[p2.x][p2.y] == ' ' && linkBoard[p1.x][p1.y] != ' ' && XiangqiUtils.isRed(engineBoard[p1.x][p1.y]) != XiangqiUtils.isRed(linkBoard[p1.x][p1.y])) {
                        flag = 2;
                        from = p2;
                        to = p1;
                        f = true;
                    }
                }
                if (f && (flag == 1 && XiangqiUtils.canGo(engineBoard, from.x, from.y, to.x, to.y) || flag == 2 && XiangqiUtils.canGo(linkBoard, from.x, from.y, to.x, to.y))) {
                    sum++;
                    action = new Action(flag, from.y, from.x, to.y, to.x);
                }
            }
        }

        if (sum == 1) {
            return action;
        }

//        if (diff1 + diff2 + diff3 == 1) {
//            return new Action(3);
//        }

        if (diff1 + diff2 + diff3 > 2) {
            return new Action(4);
        }

        return null;
    }

    void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 前台截图
     * @param windowPos
     * @return
     */
    public BufferedImage screenshotByFront(Rectangle windowPos) {
        if (windowPos.width == 0 || windowPos.height == 0) {
            return null;
        }
        return robot.createScreenCapture(windowPos);
    }

    /**
     * 前台点击
     * @param windowPos
     * @param p1
     * @param p2
     */
    @Override
    public void mouseClickByFront(Rectangle windowPos, Point p1, Point p2) {

        Point mouse = MouseInfo.getPointerInfo().getLocation();

        robot.mouseMove(windowPos.x + p1.x, windowPos.y+ p1.y);

        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        if (prop.getMouseClickDelay() > 0) {
            robot.delay(prop.getMouseClickDelay());
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        if (prop.getMouseMoveDelay() > 0) {
            robot.delay(prop.getMouseMoveDelay());
        }
        robot.mouseMove(windowPos.x + p2.x, windowPos.y + p2.y);

        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        if (prop.getMouseClickDelay() > 0) {
            robot.delay(prop.getMouseClickDelay());
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        robot.mouseMove((int) mouse.getX(), (int) mouse.getY());

    }

    /**
     * 寻找棋盘区域
     * @return
     */
    boolean findBoardPosition() {
        long attempt = ++boardSearchAttempt;
        BufferedImage img = screenshot(true);
        if (img == null) {
            log("board_search_failed", "attempt=" + attempt + " stage=screenshot reason=null_image");
            return false;
        }
        log("board_search_screenshot", "attempt=" + attempt + " image=" + imageSize(img));
        this.boardPos = this.aiModel.findBoardPosition(img);
        if (this.boardPos == null) {
            log("board_search_failed", "attempt=" + attempt + " stage=model reason=board_not_detected");
            return false;
        }
        log("board_search_succeeded", "attempt=" + attempt + " board=" + rectangle(this.boardPos));
        return true;
    }

    /**
     * 截图
     * @param fullScreen
     * @return
     */
    BufferedImage screenshot(boolean fullScreen) {
        if (prop.isLinkBackMode()) {
            Rectangle requested = fullScreen ? null : boardPos;
            log("screenshot_requested", "mode=background scope=" + (fullScreen ? "window" : "board")
                    + " rect=" + rectangle(requested));
            BufferedImage img = screenshotByBack(requested);
            log("screenshot_completed", "mode=background scope=" + (fullScreen ? "window" : "board")
                    + " result=" + imageSummary(img));
            if (img == null) {
                Rectangle pos = getTargetWindowPosition();
                if (!fullScreen) {
                    pos.setLocation(pos.x + boardPos.x, pos.y + boardPos.y);
                    pos.setSize(boardPos.width, boardPos.height);
                }
                log("screenshot_fallback", "from=background to=foreground scope="
                        + (fullScreen ? "window" : "board") + " rect=" + rectangle(pos));
                img = screenshotByFront(pos);
                log("screenshot_completed", "mode=foreground_fallback scope="
                        + (fullScreen ? "window" : "board") + " result=" + imageSummary(img));
            }
            return img;

        } else {
            Rectangle pos = getTargetWindowPosition();
            if (!fullScreen) {
                pos.setLocation(pos.x + boardPos.x, pos.y + boardPos.y);
                pos.setSize(boardPos.width, boardPos.height);
            }
            log("screenshot_requested", "mode=foreground scope=" + (fullScreen ? "window" : "board")
                    + " rect=" + rectangle(pos));
            BufferedImage img = screenshotByFront(pos);
            log("screenshot_completed", "mode=foreground scope=" + (fullScreen ? "window" : "board")
                    + " result=" + imageSummary(img));
            return img;
        }
    }


    private boolean findChessBoard(char[][] board) {
        long attempt = ++boardReadAttempt;
        // 截图
        BufferedImage img = screenshot(false);
        if (img == null) {
            log("piece_recognition_failed", "attempt=" + attempt + " stage=screenshot reason=null_image board=" + rectangle(boardPos));
            return false;
        }
        // ai识别棋盘棋子
        if (!this.aiModel.findChessBoard(img, board)) {
            log("piece_recognition_failed", "attempt=" + attempt + " stage=model reason=board_or_pieces_not_detected image=" + imageSize(img));
            return false;
        }
        String validationError = XiangqiUtils.getChessBoardValidationError(board);
        if (validationError != null) {
            log("piece_recognition_failed", "attempt=" + attempt + " stage=validation reason=" + validationError
                    + " pieces=" + countPieces(board) + " layout=" + boardLayout(board));
            return false;
        }
        log("piece_recognition_succeeded", "attempt=" + attempt + " pieces=" + countPieces(board)
                + " image=" + imageSize(img) + " layout=" + boardLayout(board));
        return true;
    }
    private boolean reverse(char[][] board) throws Exception {
        // 是否翻转
        int rowRedKing = -1, rowBlackKing = -1;
        for (int i = 0; i < 10; i++) {
            for (int j = 3; j < 6; j++) {
                if (board[i][j] == 'k') {
                    rowBlackKing = i;
                } else if (board[i][j] == 'K') {
                    rowRedKing = i;
                }
            }
        }
        if (rowBlackKing == -1 && rowRedKing == -1) {
            throw new Exception("find king failed.");
        }
        boolean isReverse = rowRedKing >= 0 && rowRedKing <= 2 || rowBlackKing >= 7 && rowBlackKing <= 9;
        if (isReverse) {
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 9; j++) {
                    char tmp = board[i][j];
                    board[i][j] = board[9 - i][8 - j];
                    board[9 - i][8 - j] = tmp;
                }
            }
        }
        return isReverse;
    }

    /**
     * 初始化棋盘局面
     * @return
     */
    private boolean initChessBoard() {
        if (!findChessBoard(board2)) {
            return false;
        }

        boolean isReverse = false;
        try {
            isReverse = reverse(board2);
        } catch (Exception e) {
            logError("board_initialization_failed", "stage=orientation", e);
            return false;
        }
        // 是否红走
        String fenCode = ChessBoard.fenCode(board2, null);
        boolean redGo = !isReverse || "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR".equals(fenCode);
        fenCode = ChessBoard.fenCode(board2, redGo);
        log("board_initialization_succeeded", "reverse=" + isReverse + " redGo=" + redGo + " fen=" + fenCode);
        // 回调，初始化棋盘
        callBack.linkerInitChessBoard(fenCode, isReverse);
        return true;
    }

    /**
     * 自动点击走棋
     * @param x1
     * @param y1
     * @param x2
     * @param y2
     */
    public void autoClick(int x1, int y1, int x2, int y2) {

        Point p1 = getPosition(x1, y1);
        Point p2 = getPosition(x2, y2);
        if (prop.isLinkBackMode()) {
            mouseClickByBack(p1, p2);
        } else {
            Rectangle windowPos = getTargetWindowPosition();
            mouseClickByFront(windowPos, p1, p2);
        }
    }
    private Point getPosition(int x, int y) {
        double pieceWith = boardPos.width / (8 + OnnxModel.PADDING * 2);
        double pieceHeight = boardPos.height / (9 + OnnxModel.PADDING * 2);
        Point p = new Point((int) (boardPos.x + pieceWith * OnnxModel.PADDING + (x * pieceWith)),
                (int) (boardPos.y + pieceHeight * OnnxModel.PADDING + (y * pieceHeight)));
        if (x == 0) {
            p.x += 0.2 * pieceWith;
        } else if (x == 8) {
            p.x -= 0.2 * pieceWith;
        }
        if (y == 0) {
            p.y += 0.2 * pieceHeight;
        } else if (y == 9) {
            p.y -= 0.2 * pieceHeight;
        }
        return p;
    }

    /**
     * 停止连线
     */
    @Override
    public void stop() {
        log("link_stopping", "threadAlive=" + (thread != null && thread.isAlive()));
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
    }

    private static String imageSize(BufferedImage image) {
        return image.getWidth() + "x" + image.getHeight();
    }

    private static String imageSummary(BufferedImage image) {
        if (image == null) {
            return "null";
        }
        int xStep = Math.max(1, image.getWidth() / 64);
        int yStep = Math.max(1, image.getHeight() / 64);
        long red = 0;
        long green = 0;
        long blue = 0;
        int minLuma = 255;
        int maxLuma = 0;
        int samples = 0;
        for (int y = 0; y < image.getHeight(); y += yStep) {
            for (int x = 0; x < image.getWidth(); x += xStep) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int luma = (r * 299 + g * 587 + b * 114) / 1000;
                red += r;
                green += g;
                blue += b;
                minLuma = Math.min(minLuma, luma);
                maxLuma = Math.max(maxLuma, luma);
                samples++;
            }
        }
        return imageSize(image) + ",avgRgb=" + (red / samples) + ":" + (green / samples) + ":" + (blue / samples)
                + ",lumaRange=" + minLuma + ":" + maxLuma + ",samples=" + samples;
    }

    private static String rectangle(Rectangle rectangle) {
        return rectangle == null ? "null" : rectangle.x + "," + rectangle.y + "," + rectangle.width + "x" + rectangle.height;
    }

    private static int countPieces(char[][] board) {
        int pieces = 0;
        for (char[] row : board) {
            for (char piece : row) {
                if (piece != ' ') {
                    pieces++;
                }
            }
        }
        return pieces;
    }

    private static String boardLayout(char[][] board) {
        StringBuilder layout = new StringBuilder(99);
        for (int i = 0; i < board.length; i++) {
            if (i > 0) {
                layout.append('/');
            }
            for (char piece : board[i]) {
                layout.append(piece == ' ' ? '.' : piece);
            }
        }
        return layout.toString();
    }

    // find chess board from image
    public char[][] findChessBoard(BufferedImage img) {
        char[][] tmp = new char[10][9];
        if (this.aiModel.findChessBoard(img, tmp)) {
            return tmp;
        } else {
            return null;
        }
    }
}
