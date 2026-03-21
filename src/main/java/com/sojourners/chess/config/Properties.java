package com.sojourners.chess.config;

import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.enginee.Engine;
import com.sojourners.chess.model.EngineConfig;
import com.sojourners.chess.openbook.MoveRule;
import com.sojourners.chess.util.PathUtils;


import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Properties implements Serializable {

    private static final long serialVersionUID = -1410031608529065857L;

    private static Properties prop;

    private ChessBoard.BoardSize boardSize;
    private ChessBoard.BoardStyle boardStyle = ChessBoard.BoardStyle.DEFAULT;

    private boolean stepTip;

    private boolean stepSound;

    private boolean showNumber = true;

    private boolean topWindow = false;

    private int threadNum;

    private int hashSize;

    private String engineName;

    private List<EngineConfig> engineConfigList = new ArrayList<>();

    private Engine.AnalysisModel analysisModel;

    private long analysisValue;

    private double stageWidth;

    private double stageHeight;

    private double splitPos;
    private double splitPos2;

    private long linkScanTime;
    private int linkThreadNum;
    private boolean linkAnimation;
    private boolean linkShowInfo;
    private boolean linkBackMode;

    private List<String> openBookList;

    private Boolean localBookFirst;

    private Boolean useCloudBook;

    private Boolean onlyCloudFinalPhase;

    private Integer cloudBookTimeout;

    private Integer offManualSteps;

    private MoveRule moveRule;

    private Boolean bookSwitch;

    private int engineDelayStart = 0;
    private int engineDelayEnd = 0;

    private int bookDelayStart = 0;
    private int bookDelayEnd = 0;

    // 兼容旧版本保留字段（固定延迟）
    private int mouseClickDelay = 200;
    private int mouseMoveDelay = 200;

    // 随机延迟区间（毫秒）
    private int mouseClickDelayStart = 200;
    private int mouseClickDelayEnd = 500;
    private int mouseMoveDelayStart = 200;
    private int mouseMoveDelayEnd = 500;

    /**
     * 连线手工棋盘区域（按窗口截图尺寸的相对比例保存）
     */
    private boolean linkUseManualBoardRegion = false;
    private double linkBoardAreaXRatio = -1;
    private double linkBoardAreaYRatio = -1;
    private double linkBoardAreaWRatio = -1;
    private double linkBoardAreaHRatio = -1;
    /*
     * 显示棋谱管理
     */
    private boolean showChessNotation = false;

    private String chessManualPath;

    private boolean manualTip = true;

    private Properties(ChessBoard.BoardSize boardSize, boolean stepTip,
                       int threadNum, int hashSize, String engineName, Engine.AnalysisModel analysisModel, long analysisValue,
                       boolean stepSound, double stageWidth, double stageHeight, double splitPos, double splitPos2,
                       long linkScanTime, int linkThreadNum, boolean linkAnimation, boolean linkShowInfo, boolean linkBackMode,
                       Boolean localBookFirst, Boolean useCloudBook, Boolean onlyCloudFinalPhase, Integer cloudBookTimeout, Integer offManualSteps,
                       MoveRule moveRule, Boolean bookSwitch, List<String> openBookList) {
        this.boardSize = boardSize;
        this.stepTip = stepTip;
        this.threadNum = threadNum;
        this.hashSize = hashSize;
        this.engineName = engineName;
        this.analysisModel = analysisModel;
        this.analysisValue = analysisValue;
        this.stepSound = stepSound;
        this.stageWidth = stageWidth;
        this.stageHeight = stageHeight;
        this.splitPos = splitPos;
        this.splitPos2 = splitPos2;
        this.linkScanTime = linkScanTime;
        this.linkThreadNum = linkThreadNum;
        this.linkAnimation = linkAnimation;
        this.linkShowInfo = linkShowInfo;
        this.linkBackMode = linkBackMode;
        this.localBookFirst = localBookFirst;
        this.useCloudBook = useCloudBook;
        this.onlyCloudFinalPhase = onlyCloudFinalPhase;
        this.cloudBookTimeout = cloudBookTimeout;
        this.offManualSteps = offManualSteps;
        this.moveRule = moveRule;
        this.bookSwitch = bookSwitch;
        this.openBookList = openBookList;
    }

    public static synchronized Properties getInstance() {
        if (prop == null) {
            String path = PathUtils.getJarPath() + "properties";
            File file = new File(path);
            if (file.exists()) {
                ObjectInputStream os = null;
                try {
                    os = new ObjectInputStream(new FileInputStream(file));
                    prop = (Properties) os.readObject();
                    prop.normalizeMouseDelayRange();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (os != null)
                            os.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else {
                try {
                    List<EngineConfig> engineConfigList = new ArrayList<>();
                    prop = new Properties(ChessBoard.BoardSize.AUTOFIT_BOARD, true,
                            1, 16, "",
                            Engine.AnalysisModel.FIXED_TIME, 5000, true,
                            920, 737, 0.64, 0.6,
                            100, 2, true, true, false,
                            true, true, false, 2000, 9999,
                            MoveRule.BEST_SCORE, true, new ArrayList<>());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return prop;
    }

    private void normalizeMouseDelayRange() {
        if (mouseClickDelayStart == 0 && mouseClickDelayEnd == 0 && mouseClickDelay > 0) {
            mouseClickDelayStart = mouseClickDelay;
            mouseClickDelayEnd = mouseClickDelay;
        }
        if (mouseMoveDelayStart == 0 && mouseMoveDelayEnd == 0 && mouseMoveDelay > 0) {
            mouseMoveDelayStart = mouseMoveDelay;
            mouseMoveDelayEnd = mouseMoveDelay;
        }
        // 旧版本默认值（点击2，走子0）统一迁移为更稳的 200-500。
        if (mouseClickDelayStart == 2 && mouseClickDelayEnd == 2
                && mouseMoveDelayStart == 0 && mouseMoveDelayEnd == 0) {
            mouseClickDelayStart = 200;
            mouseClickDelayEnd = 500;
            mouseMoveDelayStart = 200;
            mouseMoveDelayEnd = 500;
        }
        if (mouseClickDelayStart > mouseClickDelayEnd) {
            int tmp = mouseClickDelayStart;
            mouseClickDelayStart = mouseClickDelayEnd;
            mouseClickDelayEnd = tmp;
        }
        if (mouseMoveDelayStart > mouseMoveDelayEnd) {
            int tmp = mouseMoveDelayStart;
            mouseMoveDelayStart = mouseMoveDelayEnd;
            mouseMoveDelayEnd = tmp;
        }
        mouseClickDelay = mouseClickDelayStart;
        mouseMoveDelay = mouseMoveDelayStart;
    }

    public void save() {
        ObjectOutputStream os = null;
        try {
            String path = PathUtils.getJarPath() + "properties";
            File file = new File(path);
            os = new ObjectOutputStream(new FileOutputStream(file));
            os.writeObject(this);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (os != null)
                    os.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public ChessBoard.BoardStyle getBoardStyle() {
        return boardStyle;
    }

    public void setBoardStyle(ChessBoard.BoardStyle boardStyle) {
        this.boardStyle = boardStyle;
    }

    public int getEngineDelayStart() {
        return engineDelayStart;
    }

    public void setEngineDelayStart(int engineDelayStart) {
        this.engineDelayStart = engineDelayStart;
    }

    public int getEngineDelayEnd() {
        return engineDelayEnd;
    }

    public void setEngineDelayEnd(int engineDelayEnd) {
        this.engineDelayEnd = engineDelayEnd;
    }

    public int getBookDelayStart() {
        return bookDelayStart;
    }

    public void setBookDelayStart(int bookDelayStart) {
        this.bookDelayStart = bookDelayStart;
    }

    public int getBookDelayEnd() {
        return bookDelayEnd;
    }

    public void setBookDelayEnd(int bookDelayEnd) {
        this.bookDelayEnd = bookDelayEnd;
    }

    public int getMouseClickDelay() {
        return getMouseClickDelayStart();
    }

    public void setMouseClickDelay(int mouseClickDelay) {
        this.mouseClickDelay = mouseClickDelay;
        this.mouseClickDelayStart = mouseClickDelay;
        this.mouseClickDelayEnd = mouseClickDelay;
    }

    public int getMouseMoveDelay() {
        return getMouseMoveDelayStart();
    }

    public void setMouseMoveDelay(int mouseMoveDelay) {
        this.mouseMoveDelay = mouseMoveDelay;
        this.mouseMoveDelayStart = mouseMoveDelay;
        this.mouseMoveDelayEnd = mouseMoveDelay;
    }

    public int getMouseClickDelayStart() {
        return mouseClickDelayStart;
    }

    public void setMouseClickDelayStart(int mouseClickDelayStart) {
        this.mouseClickDelayStart = mouseClickDelayStart;
        this.mouseClickDelay = mouseClickDelayStart;
    }

    public int getMouseClickDelayEnd() {
        return mouseClickDelayEnd;
    }

    public void setMouseClickDelayEnd(int mouseClickDelayEnd) {
        this.mouseClickDelayEnd = mouseClickDelayEnd;
    }

    public int getMouseMoveDelayStart() {
        return mouseMoveDelayStart;
    }

    public void setMouseMoveDelayStart(int mouseMoveDelayStart) {
        this.mouseMoveDelayStart = mouseMoveDelayStart;
        this.mouseMoveDelay = mouseMoveDelayStart;
    }

    public int getMouseMoveDelayEnd() {
        return mouseMoveDelayEnd;
    }

    public void setMouseMoveDelayEnd(int mouseMoveDelayEnd) {
        this.mouseMoveDelayEnd = mouseMoveDelayEnd;
    }

    public boolean isLinkUseManualBoardRegion() {
        return linkUseManualBoardRegion;
    }

    public void setLinkUseManualBoardRegion(boolean linkUseManualBoardRegion) {
        this.linkUseManualBoardRegion = linkUseManualBoardRegion;
    }

    public double getLinkBoardAreaXRatio() {
        return linkBoardAreaXRatio;
    }

    public void setLinkBoardAreaXRatio(double linkBoardAreaXRatio) {
        this.linkBoardAreaXRatio = linkBoardAreaXRatio;
    }

    public double getLinkBoardAreaYRatio() {
        return linkBoardAreaYRatio;
    }

    public void setLinkBoardAreaYRatio(double linkBoardAreaYRatio) {
        this.linkBoardAreaYRatio = linkBoardAreaYRatio;
    }

    public double getLinkBoardAreaWRatio() {
        return linkBoardAreaWRatio;
    }

    public void setLinkBoardAreaWRatio(double linkBoardAreaWRatio) {
        this.linkBoardAreaWRatio = linkBoardAreaWRatio;
    }

    public double getLinkBoardAreaHRatio() {
        return linkBoardAreaHRatio;
    }

    public void setLinkBoardAreaHRatio(double linkBoardAreaHRatio) {
        this.linkBoardAreaHRatio = linkBoardAreaHRatio;
    }

    public boolean hasLinkBoardArea() {
        return linkBoardAreaXRatio >= 0 && linkBoardAreaYRatio >= 0
                && linkBoardAreaWRatio > 0 && linkBoardAreaHRatio > 0
                && linkBoardAreaXRatio + linkBoardAreaWRatio <= 1.001
                && linkBoardAreaYRatio + linkBoardAreaHRatio <= 1.001;
    }

    public void clearLinkBoardArea() {
        linkBoardAreaXRatio = -1;
        linkBoardAreaYRatio = -1;
        linkBoardAreaWRatio = -1;
        linkBoardAreaHRatio = -1;
    }

    public List<String> getOpenBookList() {
        return openBookList;
    }

    public void setOpenBookList(List<String> openBookList) {
        this.openBookList = openBookList;
    }

    public Boolean getLocalBookFirst() {
        return localBookFirst;
    }

    public void setLocalBookFirst(Boolean localBookFirst) {
        this.localBookFirst = localBookFirst;
    }

    public Boolean getUseCloudBook() {
        return useCloudBook;
    }

    public void setUseCloudBook(Boolean useCloudBook) {
        this.useCloudBook = useCloudBook;
    }

    public Boolean getOnlyCloudFinalPhase() {
        return onlyCloudFinalPhase;
    }

    public void setOnlyCloudFinalPhase(Boolean onlyCloudFinalPhase) {
        this.onlyCloudFinalPhase = onlyCloudFinalPhase;
    }

    public Integer getCloudBookTimeout() {
        return cloudBookTimeout;
    }

    public void setCloudBookTimeout(Integer cloudBookTimeout) {
        this.cloudBookTimeout = cloudBookTimeout;
    }

    public Integer getOffManualSteps() {
        return offManualSteps;
    }

    public void setOffManualSteps(Integer offManualSteps) {
        this.offManualSteps = offManualSteps;
    }

    public MoveRule getMoveRule() {
        return moveRule;
    }

    public void setMoveRule(MoveRule moveRule) {
        this.moveRule = moveRule;
    }

    public Boolean getBookSwitch() {
        return bookSwitch;
    }

    public void setBookSwitch(Boolean bookSwitch) {
        this.bookSwitch = bookSwitch;
    }

    public long getLinkScanTime() {
        return linkScanTime;
    }

    public void setLinkScanTime(long linkScanTime) {
        this.linkScanTime = linkScanTime;
    }

    public int getLinkThreadNum() {
        return linkThreadNum;
    }

    public void setLinkThreadNum(int linkThreadNum) {
        this.linkThreadNum = linkThreadNum;
    }

    public boolean isLinkAnimation() {
        return linkAnimation;
    }

    public void setLinkAnimation(boolean linkAnimation) {
        this.linkAnimation = linkAnimation;
    }

    public boolean isLinkShowInfo() {
        return linkShowInfo;
    }

    public void setLinkShowInfo(boolean linkShowInfo) {
        this.linkShowInfo = linkShowInfo;
    }

    public boolean isLinkBackMode() {
        return linkBackMode;
    }

    public void setLinkBackMode(boolean linkBackMode) {
        this.linkBackMode = linkBackMode;
    }

    public double getSplitPos2() {
        return splitPos2;
    }

    public void setSplitPos2(double splitPos2) {
        this.splitPos2 = splitPos2;
    }

    public double getStageWidth() {
        return stageWidth;
    }

    public void setStageWidth(double stageWidth) {
        this.stageWidth = stageWidth;
    }

    public double getStageHeight() {
        return stageHeight;
    }

    public void setStageHeight(double stageHeight) {
        this.stageHeight = stageHeight;
    }

    public double getSplitPos() {
        return splitPos;
    }

    public void setSplitPos(double splitPos) {
        this.splitPos = splitPos;
    }

    public boolean isStepSound() {
        return stepSound;
    }

    public void setStepSound(boolean stepSound) {
        this.stepSound = stepSound;
    }

    public Engine.AnalysisModel getAnalysisModel() {
        return analysisModel;
    }

    public void setAnalysisModel(Engine.AnalysisModel analysisModel) {
        this.analysisModel = analysisModel;
    }

    public long getAnalysisValue() {
        return analysisValue;
    }

    public void setAnalysisValue(long analysisValue) {
        this.analysisValue = analysisValue;
    }

    public String getEngineName() {
        return engineName;
    }

    public void setEngineName(String engineName) {
        this.engineName = engineName;
    }

    public int getThreadNum() {
        return threadNum;
    }

    public void setThreadNum(int threadNum) {
        this.threadNum = threadNum;
    }

    public int getHashSize() {
        return hashSize;
    }

    public void setHashSize(int hashSize) {
        this.hashSize = hashSize;
    }

    public List<EngineConfig> getEngineConfigList() {
        return engineConfigList;
    }

    public void setEngineConfigList(List<EngineConfig> engineConfigList) {
        this.engineConfigList = engineConfigList;
    }

    public ChessBoard.BoardSize getBoardSize() {
        return boardSize;
    }

    public void setBoardSize(ChessBoard.BoardSize boardSize) {
        this.boardSize = boardSize;
    }

    public boolean isStepTip() {
        return stepTip;
    }

    public void setStepTip(boolean stepTip) {
        this.stepTip = stepTip;
    }

    public boolean isShowNumber() {
        return showNumber;
    }

    public void setShowNumber(boolean showNumber) {
        this.showNumber = showNumber;
    }

    public boolean isTopWindow() {
        return topWindow;
    }

    public void setTopWindow(boolean topWindow) {
        this.topWindow = topWindow;
    }

    public boolean isShowChessNotation() {
        return showChessNotation;
    }

    public void setShowChessNotation(boolean showChessNotation) {
        this.showChessNotation = showChessNotation;
    }

    public String getChessManualPath() {
        return chessManualPath;
    }

    public void setChessManualPath(String chessManualPath) {
        this.chessManualPath = chessManualPath;
    }

    public boolean isManualTip() {
        return manualTip;
    }

    public void setManualTip(boolean manualTip) {
        this.manualTip = manualTip;
    }
}
