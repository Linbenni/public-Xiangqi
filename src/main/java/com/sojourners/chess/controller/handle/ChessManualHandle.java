package com.sojourners.chess.controller.handle;

import com.sojourners.chess.App;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.manual.ChessManual;
import com.sojourners.chess.manual.ChessManualService;
import com.sojourners.chess.manual.PgnChessManualImpl;
import com.sojourners.chess.manual.TxqChessManualImpl;
import com.sojourners.chess.model.ManualRecord;
import com.sojourners.chess.util.DialogUtils;
import com.sojourners.chess.util.PathUtils;
import com.sojourners.chess.util.StringUtils;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class ChessManualHandle {

    private ChessManualCallBack cb;

    /**
     * 棋谱数据结构
     */
    private String fenCode;
    private ManualRecord manualHead;
    private int p;
    private File manualFile;

    /**
     * 控件
     */
    private BorderPane chessManualPane;
    private CheckMenuItem menuOfChessNotation;
    private CheckMenuItem menuOfShowTactic;
    private TreeView<File> notationTree;
    private Label manualTitleLabel;
    private TableView<ManualRecord> recordTable;
    private ListView<ManualRecord> subRecordTable;
    private TextArea remarkText;

    private Button manualBackButton;
    private Button manualDeleteButton;
    private Button manualDownButton;
    private Button manualFinalButton;
    private Button manualForwardButton;
    private Button manualFrontButton;
    private Button manualPlayButton;
    private Button manualUpButton;
    private Button openManualButton;
    private Button saveManualButton;
    private Button manualScoreButton;
    private TextField competitionNameText;
    private TextField competitionCityText;
    private TextField competitionDateText;
    private TextField competitionRedText;
    private TextField competitionBlackText;

    private Properties prop;

    private Timeline manualPlayTimeline;

    private static Map<String, ChessManualService> manualServices;

    static {
        manualServices = new HashMap<>();
        manualServices.put("txq", new TxqChessManualImpl());
        manualServices.put("pgn", new PgnChessManualImpl());
    }

    public ChessManualHandle(BorderPane chessManualPane, CheckMenuItem menuOfChessNotation, CheckMenuItem menuOfShowTactic, TreeView notationTree,
                             Label manualTitleLabel, TableView recordTable, ListView subRecordTable, TextArea remarkText,
                             Button manualBackButton, Button manualDeleteButton, Button manualDownButton, Button manualFinalButton,
                             Button manualForwardButton, Button manualFrontButton, Button manualPlayButton, Button manualUpButton,
                             Button openManualButton, Button saveManualButton, Button manualScoreButton,
                             TextField competitionNameText, TextField competitionCityText, TextField competitionDateText,
                             TextField competitionRedText, TextField competitionBlackText,
                             ChessManualCallBack cb) {
        this.chessManualPane = chessManualPane;
        this.menuOfChessNotation = menuOfChessNotation;
        this.menuOfShowTactic = menuOfShowTactic;
        this.notationTree = notationTree;
        this.manualTitleLabel = manualTitleLabel;
        this.recordTable = recordTable;
        this.subRecordTable = subRecordTable;
        this.remarkText = remarkText;
        this.manualBackButton = manualBackButton;
        this.manualDeleteButton = manualDeleteButton;
        this.manualDownButton = manualDownButton;
        this.manualFinalButton = manualFinalButton;
        this.manualForwardButton = manualForwardButton;
        this.manualFrontButton = manualFrontButton;
        this.manualPlayButton = manualPlayButton;
        this.manualUpButton = manualUpButton;
        this.openManualButton = openManualButton;
        this.saveManualButton = saveManualButton;
        this.manualScoreButton = manualScoreButton;
        this.competitionNameText = competitionNameText;
        this.competitionCityText = competitionCityText;
        this.competitionDateText = competitionDateText;
        this.competitionRedText = competitionRedText;
        this.competitionBlackText = competitionBlackText;

        this.cb = cb;

        prop = Properties.getInstance();

        initTreeView();
        initRecordTable();
        initRemarkText();
        initMenu();
        initButton();

        refreshManualTree();
    }

    private void refreshManualTree() {
        if (!StringUtils.isEmpty(prop.getChessManualPath())) {
            openChessNotationFolder(prop.getChessManualPath());
        }
    }

    private void initButton() {
        manualDownButton.setTooltip(new Tooltip("下变"));
        manualFrontButton.setTooltip(new Tooltip("开局"));
        manualPlayButton.setTooltip(new Tooltip("播放棋谱"));
        manualUpButton.setTooltip(new Tooltip("上变"));
        manualDeleteButton.setTooltip(new Tooltip("删除棋谱"));
        manualForwardButton.setTooltip(new Tooltip("前进"));
        manualBackButton.setTooltip(new Tooltip("后退"));
        manualFinalButton.setTooltip(new Tooltip("终局"));
        openManualButton.setTooltip(new Tooltip("打开棋谱"));
        saveManualButton.setTooltip(new Tooltip("保存棋谱"));
        manualScoreButton.setTooltip(new Tooltip("棋谱打分"));
    }

    private void initMenu() {
        menuOfChessNotation.setSelected(prop.isShowChessNotation());
        showChessManualPane(prop.isShowChessNotation());
        menuOfChessNotation.setOnAction(e -> {
            CheckMenuItem item = (CheckMenuItem) e.getTarget();
            prop.setShowChessNotation(item.isSelected());
            showChessManualPane(item.isSelected());
        });

        menuOfShowTactic.setSelected(prop.isManualTip());
    }

    private void initRemarkText() {
        remarkText.textProperty().addListener((obs, oldV, newV) -> {
            if (!remarkText.isFocused()) return;
            recordTable.getItems().get(p).setRemark(newV);
        });
    }

    private void initRecordTable() {
        TableColumn<ManualRecord, String> idCol = (TableColumn<ManualRecord, String>) recordTable.getColumns().get(0);
        idCol.setCellValueFactory(new PropertyValueFactory<ManualRecord, String>("id"));
        TableColumn<ManualRecord, String> nameCol = (TableColumn<ManualRecord, String>) recordTable.getColumns().get(1);
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getList().size() > 1
                        ? cellData.getValue().getCnMove() + "      b"
                        : cellData.getValue().getCnMove()));
        TableColumn<ManualRecord, String> scoreCol = (TableColumn<ManualRecord, String>) recordTable.getColumns().get(2);
        // 不用 PropertyValueFactory：棋谱行上 setScore 后需可靠刷新「分数」列
        scoreCol.setCellValueFactory(cd -> {
            Integer sc = cd.getValue().getScore();
            return new SimpleStringProperty(sc == null ? "" : String.valueOf(sc));
        });
        subRecordTable.setCellFactory(lv -> {
            ListCell<ManualRecord> cell = new ListCell<>() {
                @Override
                protected void updateItem(ManualRecord item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        int lineNo = getIndex() + 1;
                        setText("(" + lineNo + ")    " + item.getCnMove());
                    }
                }
            };
            cell.addEventHandler(MouseEvent.MOUSE_CLICKED, evt -> {
                if (evt.getButton() == MouseButton.PRIMARY && evt.getClickCount() == 2 && !cell.isEmpty()) {
                    ManualRecord selectRecord = subRecordTable.getSelectionModel().getSelectedItem();
                    List<String> nextList = boardMove(selectRecord.getMove(), selectRecord.getCnMove());
                    this.cb.browseChessRecord(fenCode, getMoveList(), getRedGo(), nextList);
                }
            });
            return cell;
        });
    }

    public void newChessManual(String fenCode) {
        this.fenCode = fenCode;
        this.manualHead = new ManualRecord(0, "开始局面", 0);
        p = 0;
        manualFile = null;

        competitionNameText.setText("");
        competitionDateText.setText("");
        competitionCityText.setText("");
        competitionRedText.setText("");
        competitionBlackText.setText("");

        manualTitleLabel.setText("未命名");
        remarkText.setText("");
        recordTable.getItems().clear();
        recordTable.getItems().add(manualHead);
        subRecordTable.getItems().clear();
    }

    public List<String> boardMove(String move, String cnMove) {
        if (manualPlayTimeline != null)
            manualPlayTimeline.stop();

        ManualRecord currentRecord = recordTable.getItems().get(p);
        ManualRecord next = null;
        for (ManualRecord mr : currentRecord.getList()) {
            if (mr.getMove().equals(move)) {
                next = mr;
                break;
            }
        }
        if (next == null) {
            next = new ManualRecord(p + 1, move, cnMove);
            currentRecord.getList().add(next);
            currentRecord.setNext(currentRecord.getList().size() - 1);
            refreshRecordView(currentRecord, null);
        }
        setRemarkAndNext(next);
        if (p == recordTable.getItems().size() - 1 || recordTable.getItems().get(p + 1) != next) {
            for (int i = recordTable.getItems().size() - 1; i > p; i--) {
                recordTable.getItems().remove(i);
            }
            do {
                recordTable.getItems().add(next);
                if (next.getList().size() > 0) {
                    next = next.getList().get(next.getNext());
                } else {
                    next = null;
                }
            } while (next != null);
        }
        p++;

        reLocationTable();

        return recordTable.getItems().get(p).getList().stream().map(ManualRecord::getMove).toList();
    }

    private void setRemarkAndNext(ManualRecord mr) {
        remarkText.setText(mr.getRemark());
        subRecordTable.getItems().clear();
        subRecordTable.getItems().addAll(mr.getList());
    }

    public void manualButtonClick(int action) {
        if (action != 9 && manualPlayTimeline != null) {
            manualPlayTimeline.stop();
        }
        switch (action) {
            case 1: if (p > 0) { p = 0; break; } else { return; }
            case 2: if (p > 0) { p--; break; } else { return; }
            case 9:
            case 3: if (p < recordTable.getItems().size() - 1) { p++; break; } else { return; }
            case 4: if (p < recordTable.getItems().size() - 1) { p = recordTable.getItems().size() - 1; break; } else { return; }
            case 5: {
                int index = recordTable.getSelectionModel().getSelectedIndex();
                if (index != p && index >= 0) {
                    p = index;
                    break;
                } else { return; }
            }
            case 6: {
                if (p > 0) {
                    p -= 2;
                    if (p < 0) {
                        p = 0;
                    }
                    break;
                } else { return; }
            }
            case 7: {
                boolean f = false;
                for (int i = p - 1; i >= 0; i--) {
                    if (recordTable.getItems().get(i).getList().size() > 1) {
                        p = i;
                        f = true;
                        break;
                    }
                }
                if (!f) {
                    return;
                } else {
                    break;
                }
            }
            case 8: {
                boolean f = false;
                for (int i = p + 1; i < recordTable.getItems().size(); i++) {
                    if (recordTable.getItems().get(i).getList().size() > 1) {
                        p = i;
                        f = true;
                        break;
                    }
                }
                if (!f) {
                    return;
                } else {
                    break;
                }
            }
            default: return;
        }
        setRemarkAndNext(recordTable.getItems().get(p));
        reLocationTable();

        this.cb.browseChessRecord(fenCode, getMoveList(), getRedGo(), recordTable.getItems().get(p).getList()
                .stream().map(ManualRecord::getMove).toList());
    }

    private void refreshRecordView(ManualRecord record, List<ManualRecord> subRecordList) {
        if (record != null) {
            recordTable.refresh();
        }
        if (subRecordList != null) {
            subRecordTable.getItems().clear();
            subRecordTable.getItems().addAll(subRecordList);
        }
    }

    public void deleteButtonClick(ActionEvent actionEvent) {
        ManualRecord currentRecord = recordTable.getItems().get(p);
        ManualRecord subRecord = subRecordTable.getSelectionModel().getSelectedItem();
        if (subRecord != null) {
            if (!DialogUtils.showConfirmDialog("确认", "要删除 " + subRecord.getCnMove() + " 及后续所有招法吗？")) {
                return;
            }
            currentRecord.getList().remove(subRecord);
            if (subRecord == recordTable.getItems().get(p + 1)) {
                for (int i = recordTable.getItems().size() - 1; i > p; i--) {
                    recordTable.getItems().remove(i);
                }
                if (currentRecord.getList().size() > 0) {
                    ManualRecord next = currentRecord.getList().get(0);
                    currentRecord.setNext(0);
                    do {
                        recordTable.getItems().add(next);
                        if (next.getList().size() > 0) {
                            next = next.getList().get(next.getNext());
                        } else {
                            next = null;
                        }
                    } while (next != null);
                }
            }
            refreshRecordView(currentRecord, currentRecord.getList());
            this.cb.setNextList(currentRecord.getList().stream().map(ManualRecord::getMove).toList());
        } else {
            if (p == 0) {
                return;
            }
            if (!DialogUtils.showConfirmDialog("确认", "要删除 " + currentRecord.getCnMove() + " 及后续所有招法吗？")) {
                return;
            }
            ManualRecord preRecord = recordTable.getItems().get(p - 1);
            preRecord.getList().remove(currentRecord);
            for (int i = recordTable.getItems().size() - 1; i >= p; i--) {
                recordTable.getItems().remove(i);
            }
            if (preRecord.getList().size() > 0) {
                ManualRecord next = preRecord.getList().get(0);
                preRecord.setNext(0);
                do {
                    recordTable.getItems().add(next);
                    if (next.getList().size() > 0) {
                        next = next.getList().get(next.getNext());
                    } else {
                        next = null;
                    }
                } while (next != null);
            }
            refreshRecordView(preRecord, null);
            manualButtonClick(2);
        }
    }

    public void scoreButtonClick(ActionEvent actionEvent) {
        TextInputDialog d = new TextInputDialog("300");
        d.setTitle("棋谱打分");
        d.setHeaderText("请设置每步打分时间(毫秒)，建议不低于300");
        d.setContentText("");
        d.initOwner(App.getMainStage());
        d.showAndWait().ifPresent(s -> {
            if (s.trim().isEmpty()) return;
            long delay = Long.parseLong(s.trim());
            if (delay <= 0) return;

            if (manualPlayTimeline != null && manualPlayTimeline.getStatus() == Animation.Status.RUNNING) {
                manualPlayTimeline.stop();
            }
            manualPlayTimeline = new Timeline(new KeyFrame(Duration.millis(delay), e -> {
                int size = recordTable.getItems().size();
                if (p < size - 1) {
                    manualButtonClick(9);
                } else {
                    manualPlayTimeline.stop();
                }
            }));
            manualPlayTimeline.statusProperty().addListener((obs, old, status) -> {
                if (status == Animation.Status.STOPPED) {
                    this.cb.turnOffAnalysisMode();
                    this.cb.refreshLineChart();
                }
            });
            manualPlayTimeline.setCycleCount(Animation.INDEFINITE);

            manualButtonClick(1);
            this.cb.turnOnAnalysisMode();
            manualPlayTimeline.play();
        });
    }

    public void playButtonClick(ActionEvent event) {
        if (p == recordTable.getItems().size() - 1) {
            return;
        }
        if (manualPlayTimeline != null && manualPlayTimeline.getStatus() == Animation.Status.RUNNING) {
            manualPlayTimeline.stop();
            return;
        }
        manualPlayTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            int size = recordTable.getItems().size();
            if (p < size - 1) {
                manualButtonClick(9);
            } else {
                manualPlayTimeline.stop();
            }
        }));
        manualPlayTimeline.setCycleCount(Animation.INDEFINITE);
        manualPlayTimeline.play();
    }

    public void setScore(Integer score, Integer mate) {
        setScoreAt(p, score, mate);
    }

    public void setScoreBySearchPly(int searchPly, Integer score, Integer mate) {
        int target = resolveScoreIndex(searchPly);
        if (target >= 0) {
            setScoreAt(target, score, mate);
        }
    }

    /**
     * 引擎分析的是「当前光标」对应局面，分数应写在棋谱第 searchPly 行（与 getP() 一致）。
     * 旧逻辑在有多步棋谱且 p=0 时错误地写到第 1 行，导致起始局面分析分数不显示或错位。
     */
    private int resolveScoreIndex(int searchPly) {
        int size = recordTable.getItems().size();
        if (size <= 0) {
            return -1;
        }
        int index = searchPly;
        if (index < 0) {
            index = 0;
        }
        if (index >= size) {
            index = size - 1;
        }
        return index;
    }

    private void setScoreAt(int index, Integer score, Integer mate) {
        if (index < 0 || index >= recordTable.getItems().size()) {
            return;
        }
        if (mate == null && score == null) {
            System.err.println("[ChessManualHandle.setScoreAt] score 与 mate 均为 null，index=" + index);
            return;
        }
        int s;
        if (mate != null) {
            int scVal = score != null ? score : 0;
            s = (scVal < 0 ? -30000 : 30000) - scVal;
        } else {
            s = score;
        }
        ManualRecord currentRecord = recordTable.getItems().get(index);
        currentRecord.setScore(s);
        refreshRecordView(currentRecord, null);
    }

    public List<XYChart.Data> getScoreList() {
        List<XYChart.Data> res = new ArrayList<>();
        for (int i = 0; i < recordTable.getItems().size(); i++) {
            ManualRecord mr = recordTable.getItems().get(i);
            if (mr.getScore() != null) {
                int score = mr.getScore();
                res.add(new XYChart.Data(mr.getId(), score > 1000 ? 1000 : (score < -1000 ? -1000 : score)));
            }
        }
        return res;
    }

    private boolean getRedGo() {
        boolean redGo = fenCode.contains("w");
        if (p % 2 != 0) {
            redGo = !redGo;
        }
        return redGo;
    }

    public List<String> getMoveList() {
        return p > 0 ? recordTable.getItems().stream()
                .map(ManualRecord::getMove).toList().subList(1, p + 1)
                : Collections.emptyList();
    }

    public int getP() {
        return this.p;
    }

    public String getFenCode() {
        return this.fenCode;
    }

    public void openChessManualFile(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(new File(
                StringUtils.isNotEmpty(prop.getChessManualPath()) ? prop.getChessManualPath() : PathUtils.getJarPath()));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("txq(*.txq)", "*.txq"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("pgn(*.pgn)", "*.pgn"));
        File file = fileChooser.showOpenDialog(App.getMainStage());
        if (file != null) {
            openFromFile(file);
        }
    }

    private void openFromFile(File file) {
        String ext = PathUtils.getDotExtension(file);
        ChessManual cm = manualServices.get(ext).openChessManual(file);

        this.fenCode = cm.getFenCode();
        this.manualHead = cm.getHead();
        if (this.manualHead != null && this.manualHead.getId() == 0) {
            this.manualHead.setScore(0);
        }
        this.manualFile = file;
        this.p = 0;

        manualTitleLabel.setText(file.getName());
        remarkText.setText(manualHead.getRemark());

        competitionNameText.setText(cm.getName());
        competitionCityText.setText(cm.getCity());
        competitionDateText.setText(cm.getDate());
        competitionRedText.setText(cm.getRed());
        competitionBlackText.setText(cm.getBlack());

        recordTable.getItems().clear();
        ManualRecord h = manualHead;
        while (h != null) {
            recordTable.getItems().add(h);
            h = h.getList().isEmpty() ? null : h.getList().get(h.getNext());
        }

        subRecordTable.getItems().clear();
        subRecordTable.getItems().addAll(manualHead.getList());

        this.cb.newChessBoardFromManual(this.fenCode);
    }

    public void saveAsChessManualFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(new File(
                StringUtils.isNotEmpty(prop.getChessManualPath()) ? prop.getChessManualPath() : PathUtils.getJarPath()));
        fileChooser.setInitialFileName("未命名");
        FileChooser.ExtensionFilter txq = new FileChooser.ExtensionFilter("txq(*.txq)", "*.txq");
        FileChooser.ExtensionFilter pgn = new FileChooser.ExtensionFilter("pgn(*.pgn)", "*.pgn");
        fileChooser.getExtensionFilters().addAll(txq, pgn);
        File file = fileChooser.showSaveDialog(App.getMainStage());
        if (file != null) {
            if (StringUtils.isEmpty(PathUtils.getDotExtension(file))) {
                String ext = fileChooser.getSelectedExtensionFilter() == txq ? ".txq" : ".pgn";
                file = new File(file.getParent(), file.getName() + ext);
            }
            saveToFile(file);

            manualTitleLabel.setText(file.getName());
            this.manualFile = file;
            refreshManualTree();
        }
    }

    public void saveChessManualFile(ActionEvent event) {
        if (manualFile == null || !Files.exists(manualFile.toPath())) {
            saveAsChessManualFile(event);
        } else {
            saveToFile(manualFile);
        }
    }

    private void saveToFile(File file) {
        ChessManual cm = new ChessManual();
        cm.setFenCode(this.fenCode);
        cm.setHead(this.manualHead);
        cm.setName(competitionNameText.getText());
        cm.setCity(competitionCityText.getText());
        cm.setDate(competitionDateText.getText());
        cm.setRed(competitionRedText.getText());
        cm.setBlack(competitionBlackText.getText());

        String ext = PathUtils.getDotExtension(file).toLowerCase();
        manualServices.get(ext).saveChessManual(cm, file);
    }

    public void openChessNotationFolder(ActionEvent event) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择棋谱目录");
        File init = new File(System.getProperty("user.home"));
        if (init.exists() && init.isDirectory()) {
            chooser.setInitialDirectory(init);
        }
        File dir = chooser.showDialog(App.getMainStage());
        if (dir != null && dir.exists() && dir.isDirectory()) {
            prop.setChessManualPath(dir.getAbsolutePath());
            openChessNotationFolder(dir.getAbsolutePath());
        }
    }

    private void showChessManualPane(boolean isShow) {
        chessManualPane.setVisible(isShow);
        chessManualPane.setManaged(isShow);
    }

    private void reLocationTable() {
        recordTable.getSelectionModel().select(p);
        recordTable.scrollTo(p);
    }

    public void openChessNotationFolder(String path) {
        File rootDir = path == null ? null : new File(path);
        if (rootDir == null || !rootDir.exists() || !rootDir.isDirectory()) {
            notationTree.setRoot(null);
            return;
        }
        TreeItem<File> root = buildTree(rootDir);
        root.setExpanded(true);
        notationTree.setShowRoot(false);
        notationTree.setRoot(root);
    }

    private void initTreeView() {
        notationTree.setCellFactory(v -> new TreeCell<>() {
            private ContextMenu ctx;
            {
                MenuItem open = new MenuItem("打开");
                MenuItem rename = new MenuItem("重命名");
                MenuItem delete = new MenuItem("删除");
                open.setOnAction(e -> {
                    doOpen();
                });
                rename.setOnAction(e -> {
                    File f = getItem();
                    if (f == null) return;
                    TextInputDialog d = new TextInputDialog(f.getName());
                    d.setTitle("重命名");
                    d.setHeaderText("输入新名称");
                    d.setContentText("");
                    d.initOwner(App.getMainStage());
                    d.showAndWait().ifPresent(s -> {
                        if (s.trim().isEmpty()) return;
                        File target = new File(f.getParentFile(), s);
                        try {
                            Files.move(f.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
                            getTreeItem().setValue(target);
                            if (getTreeItem().isLeaf()) {
                                updateItem(target, false);
                            }
                        } catch (Exception ex) {
                            try {
                                boolean ok = f.renameTo(target);
                                if (ok) {
                                    getTreeItem().setValue(target);
                                    updateItem(target, false);
                                }
                            } catch (Exception ignore) {
                            }
                        }
                    });
                });
                delete.setOnAction(e -> {
                    File f = getItem();
                    if (f == null) return;
                    deleteFile(f);
                    TreeItem<File> p = getTreeItem().getParent();
                    if (p != null) {
                        p.getChildren().remove(getTreeItem());
                    } else {
                        notationTree.setRoot(null);
                    }
                });
                ctx = new ContextMenu(open, rename, delete);
                selectedProperty().addListener((obs, oldV, newV) -> {
                    if (getItem() != null && !isEmpty() && getTreeItem() != null) {
                        int row = notationTree.getRow(getTreeItem());
                        if (newV) {
                            setStyle("");
                        } else {
                            setStyle(row % 2 == 0 ? "-fx-background-color: #F5F7FA;" : "-fx-background-color: #FFFFFF;");
                        }
                    } else {
                        setStyle("");
                    }
                });
                addEventHandler(MouseEvent.MOUSE_CLICKED, evt -> {
                    if (evt.getButton() == MouseButton.PRIMARY && evt.getClickCount() == 2 && !isEmpty()) {
                        doOpen();
                    }
                });
            }
            private void doOpen() {
                File f = getItem();
                if (f == null) return;
                if (f.isDirectory()) {
                    TreeItem<File> ti = getTreeItem();
                    ti.setExpanded(!ti.isExpanded());
                } else if (isManualFile(f)) {
                    openFromFile(f);
                }
            }
            @Override
            protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setContextMenu(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    setText(item.getName());
                    setContextMenu(ctx);
                    if (getTreeItem() != null) {
                        int row = notationTree.getRow(getTreeItem());
                        if (isSelected()) {
                            setStyle("");
                        } else {
                            setStyle(row % 2 == 0 ? "-fx-background-color: #F5F7FA;" : "-fx-background-color: #FFFFFF;");
                        }
                    }
                }
            }
        });
    }

    private TreeItem<File> buildTree(File dir) {
        TreeItem<File> root = dir.isDirectory() ? new DirTreeItem(dir) : new TreeItem<>(dir);
        File[] children = dir.listFiles();
        if (children == null) return root;
        Arrays.sort(children, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        for (File f : children) {
            if (f.isDirectory()) {
                root.getChildren().add(buildTree(f));
            } else if (isManualFile(f)) {
                root.getChildren().add(new TreeItem<>(f));
            }
        }
        return root;
    }

    private static class DirTreeItem extends TreeItem<File> {
        DirTreeItem(File dir) {
            super(dir);
        }
        @Override
        public boolean isLeaf() {
            File f = getValue();
            return f != null && f.isFile();
        }
    }

    private boolean isManualFile(File f) {
        return f.isFile() && manualServices.containsKey(PathUtils.getDotExtension(f));
    }

    private void deleteFile(File f) {
        if (f.isDirectory()) {
            File[] list = f.listFiles();
            if (list != null) {
                for (File c : list) {
                    deleteFile(c);
                }
            }
        }
        try {
            Files.deleteIfExists(f.toPath());
        } catch (Exception e) {
            f.delete();
        }
    }
}
