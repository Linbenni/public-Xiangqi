package com.sojourners.chess.controller;

import com.sojourners.chess.App;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.enginee.Engine;
import com.sojourners.chess.model.EngineConfig;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.util.Callback;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EngineAddController {


    private Properties prop;

    @FXML
    private TextField nameText;

    @FXML
    private TextField protocolText;

    @FXML
    private TextField executableText;

    @FXML
    private TextField argsText;

    @FXML
    private ListView<Map.Entry<String, String>> optionsListView;

    public static EngineConfig ec;

    private LinkedHashMap<String, String> options;

    /** 若参数内含空格，与命令行一致加引号，便于再拼回一条 command */
    private static String quoteToken(String t) {
        if (t == null || t.isEmpty()) {
            return "\"\"";
        }
        if (t.indexOf(' ') < 0 && t.indexOf('\t') < 0) {
            return t;
        }
        return "\"" + t.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** 从已保存的 command 拆到「启动文件」「参数」两框 */
    private void loadCommandIntoFields(String storedCommand) {
        List<String> parts = Engine.parseCommandLine(storedCommand);
        if (parts.isEmpty()) {
            executableText.clear();
            argsText.clear();
            return;
        }
        executableText.setText(quoteToken(parts.get(0)));
        if (parts.size() == 1) {
            argsText.clear();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < parts.size(); i++) {
            if (i > 1) {
                sb.append(' ');
            }
            sb.append(quoteToken(parts.get(i)));
        }
        argsText.setText(sb.toString());
    }

    /** 合并为持久化的完整启动命令；工作目录不再由用户填写，由引擎侧根据可执行文件路径推断 */
    private String buildFullCommand() {
        String exe = executableText.getText() == null ? "" : executableText.getText().trim();
        String args = argsText.getText() == null ? "" : argsText.getText().trim();
        if (exe.isEmpty()) {
            return "";
        }
        if (args.isEmpty()) {
            return exe;
        }
        return exe + " " + args;
    }

    @FXML
    void selectExecutableClick(ActionEvent e) {
        FileChooser fileChooser = new FileChooser();
        String cur = executableText.getText() == null ? "" : executableText.getText().trim();
        List<String> toks = Engine.parseCommandLine(cur);
        if (!toks.isEmpty()) {
            File f = new File(toks.get(0));
            File par = f.getParentFile();
            if (par != null && par.isDirectory()) {
                fileChooser.setInitialDirectory(par);
            }
        }
        File file = fileChooser.showOpenDialog(App.getEngineAdd());
        if (file != null) {
            String path = file.getAbsolutePath();
            executableText.setText(quoteToken(path));
            if (nameText.getText() == null || nameText.getText().isBlank()) {
                nameText.setText(file.getName());
            }
            detectEngineProtocol();
        }
    }

    @FXML
    void detectButtonClick(ActionEvent e) {
        detectEngineProtocol();
    }

    private void detectEngineProtocol() {
        String command = buildFullCommand();
        if (command.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("提示");
            alert.setHeaderText("请先选择启动文件（可再填写参数）");
            alert.showAndWait();
            return;
        }
        String protocol = Engine.testCommand(command, "", options = new LinkedHashMap<>());
        if (protocol == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("提示");
            alert.setHeaderText("无效的引擎启动命令（请检查路径、参数与 PATH）");
            alert.showAndWait();
            System.err.println("[EngineAdd] 检测失败 command=" + command);
            return;
        }
        protocolText.setText(protocol);
        showOptions();
    }

    private void showOptions() {
        optionsListView.getItems().clear();
        for (Map.Entry<String, String> entry : options.entrySet()) {
            optionsListView.getItems().add(entry);
        }
    }

    @FXML
    void cancelButtonClick(ActionEvent event) {
        App.closeEngineAdd();
    }

    @FXML
    void okButtonClick(ActionEvent event) {
        String protocol = protocolText.getText();
        if (!"uci".equals(protocol) && !"ucci".equals(protocol)) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("提示");
            alert.setHeaderText("引擎协议不正确");
            alert.showAndWait();
            return;
        }
        String command = buildFullCommand();
        if (command.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("提示");
            alert.setHeaderText("请先选择启动文件");
            alert.showAndWait();
            return;
        }
        if (options == null) {
            options = new LinkedHashMap<>();
        }
        if (ec == null) {
            prop.getEngineConfigList().add(new EngineConfig(nameText.getText(), "", command, protocolText.getText(), options));
        } else {
            ec.setName(nameText.getText());
            ec.setWorkDir("");
            ec.setCommand(command);
            ec.setProtocol(protocolText.getText());
            ec.setOptions(options);
        }
        App.closeEngineAdd();
    }

    public void initialize() {
        prop = Properties.getInstance();

        initListView();

        if (ec != null) {
            nameText.setText(ec.getName());
            loadCommandIntoFields(ec.getCommand());
            protocolText.setText(ec.getProtocol());

            this.options = ec.getOptions() == null ? new LinkedHashMap<>() : (LinkedHashMap<String, String>) ec.getOptions().clone();
            showOptions();
        } else {
            this.options = new LinkedHashMap<>();
        }
    }

    private void initListView() {
        optionsListView.setSelectionModel(new MultipleSelectionModel<>() {
            private ObservableList emptyList = FXCollections.emptyObservableList();

            @Override
            public ObservableList<Integer> getSelectedIndices() {
                return emptyList;
            }

            @Override
            public ObservableList<Map.Entry<String, String>> getSelectedItems() {
                return emptyList;
            }

            @Override
            public void selectIndices(int i, int... ints) {

            }

            @Override
            public void selectAll() {

            }

            @Override
            public void selectFirst() {

            }

            @Override
            public void selectLast() {

            }

            @Override
            public void clearAndSelect(int i) {

            }

            @Override
            public void select(int i) {

            }

            @Override
            public void select(Map.Entry<String, String> stringStringEntry) {

            }

            @Override
            public void clearSelection(int i) {

            }

            @Override
            public void clearSelection() {

            }

            @Override
            public boolean isSelected(int i) {
                return false;
            }

            @Override
            public boolean isEmpty() {
                return true;
            }

            @Override
            public void selectPrevious() {

            }

            @Override
            public void selectNext() {

            }
        });
        optionsListView.setCellFactory(new Callback() {
            @Override
            public Object call(Object param) {
                ListCell<Map.Entry<String, String>> cell = new ListCell<>() {
                    @Override
                    protected void updateItem(Map.Entry<String, String> item, boolean bln) {
                        super.updateItem(item, bln);
                        if (!bln) {
                            HBox box = new HBox();

                            Label label = new Label();
                            label.setText(item.getKey());
                            label.setAlignment(Pos.CENTER_LEFT);
                            label.setPrefHeight(27);
                            label.setPrefWidth(100);
                            box.getChildren().add(label);

                            TextField input = new TextField();
                            input.setText(item.getValue());
                            input.setPrefWidth(120);
                            input.textProperty().addListener(new ChangeListener<String>() {
                                @Override
                                public void changed(ObservableValue<? extends String> observableValue, String s, String t1) {
                                    options.put(item.getKey(), t1);
                                }
                            });
                            box.getChildren().add(input);

                            setGraphic(box);
                        }
                    }
                };
                return cell;
            }

        });
    }
}
