package com.sojourners.chess.controller;

import com.sojourners.chess.App;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.enginee.Engine;
import com.sojourners.chess.util.DialogUtils;
import com.sojourners.chess.util.StringUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;


public class TimeSettingController {

    @FXML
    private CheckBox fixTimeButton;

    @FXML
    private TextField timeText;

    @FXML
    private CheckBox fixDepthButton;

    @FXML
    private TextField depthText;

    @FXML
    private CheckBox fixNodeButton;

    @FXML
    private TextField nodeText;

    @FXML
    private TextField engineDelayStart;

    @FXML
    private TextField engineDelayEnd;

    @FXML
    private TextField bookDelayStart;

    @FXML
    private TextField bookDelayEnd;


    private Properties prop;

    @FXML
    void cancelButtonClick(ActionEvent e) {
        App.closeTimeSetting();
    }

    @FXML
    void okButtonClick(ActionEvent e) {
        if (fixNodeButton.isSelected()) {
            String txt = nodeText.getText();
            if (!StringUtils.isPositiveInt(txt)) {
                DialogUtils.showErrorDialog("失败", "节点数错误");
                return;
            }
            prop.setAnalysisModel(Engine.AnalysisModel.FIXED_NODES);
            prop.setAnalysisValue(Long.parseLong(txt));
        } else if (fixTimeButton.isSelected() && fixDepthButton.isSelected()) {
            String time = timeText.getText();
            if (!StringUtils.isPositiveInt(time)) {
                DialogUtils.showErrorDialog("失败", "时间错误");
                return;
            }
            String depth = depthText.getText();
            if (!StringUtils.isPositiveInt(depth)) {
                DialogUtils.showErrorDialog("失败", "层数错误");
                return;
            }
            long timeValue = Long.parseLong(time);
            long depthValue = Long.parseLong(depth);
            prop.setAnalysisModel(Engine.AnalysisModel.FIXED_TIME_AND_STEPS);
            prop.setAnalysisValue(timeValue);
            prop.setAnalysisTimeValue(timeValue);
            prop.setAnalysisDepthValue(depthValue);
        } else if (fixDepthButton.isSelected()) {
            String txt = depthText.getText();
            if (!StringUtils.isPositiveInt(txt)) {
                DialogUtils.showErrorDialog("失败", "层数错误");
                return;
            }
            long depthValue = Long.parseLong(txt);
            prop.setAnalysisModel(Engine.AnalysisModel.FIXED_STEPS);
            prop.setAnalysisValue(depthValue);
            prop.setAnalysisDepthValue(depthValue);
        } else if (fixTimeButton.isSelected()) {
            String txt = timeText.getText();
            if (!StringUtils.isPositiveInt(txt)) {
                DialogUtils.showErrorDialog("失败", "时间错误");
                return;
            }
            long timeValue = Long.parseLong(txt);
            prop.setAnalysisModel(Engine.AnalysisModel.FIXED_TIME);
            prop.setAnalysisValue(timeValue);
            prop.setAnalysisTimeValue(timeValue);
        } else {
            DialogUtils.showErrorDialog("失败", "请至少选择一种限制");
            return;
        }

        String txt = engineDelayStart.getText();
        if (!StringUtils.isNonNegativeInt(txt)) {
            DialogUtils.showErrorDialog("失败", "输入引擎出招延迟错误");
            return;
        }
        prop.setEngineDelayStart(Integer.parseInt(txt));
        txt = engineDelayEnd.getText();
        if (!StringUtils.isNonNegativeInt(txt)) {
            DialogUtils.showErrorDialog("失败", "输入引擎出招延迟错误");
            return;
        }
        prop.setEngineDelayEnd(Integer.parseInt(txt));

        txt = bookDelayStart.getText();
        if (!StringUtils.isNonNegativeInt(txt)) {
            DialogUtils.showErrorDialog("失败", "输入库招出招延迟错误");
            return;
        }
        prop.setBookDelayStart(Integer.parseInt(txt));
        txt = bookDelayEnd.getText();
        if (!StringUtils.isNonNegativeInt(txt)) {
            DialogUtils.showErrorDialog("失败", "输入库招出招延迟错误");
            return;
        }
        prop.setBookDelayEnd(Integer.parseInt(txt));

        App.closeTimeSetting();
    }


    public void initialize() {
        prop = Properties.getInstance();

        fixTimeButton.selectedProperty().addListener((observable, oldValue, selected) -> {
            if (selected) {
                fixNodeButton.setSelected(false);
            }
        });
        fixDepthButton.selectedProperty().addListener((observable, oldValue, selected) -> {
            if (selected) {
                fixNodeButton.setSelected(false);
            }
        });
        fixNodeButton.selectedProperty().addListener((observable, oldValue, selected) -> {
            if (selected) {
                fixTimeButton.setSelected(false);
                fixDepthButton.setSelected(false);
            }
        });

        timeText.setText(String.valueOf(prop.getAnalysisTimeValue()));
        depthText.setText(String.valueOf(prop.getAnalysisDepthValue()));

        if (prop.getAnalysisModel() == Engine.AnalysisModel.FIXED_TIME_AND_STEPS) {
            fixTimeButton.setSelected(true);
            fixDepthButton.setSelected(true);
        } else if (prop.getAnalysisModel() == Engine.AnalysisModel.FIXED_TIME) {
            fixTimeButton.setSelected(true);
        } else if (prop.getAnalysisModel() == Engine.AnalysisModel.FIXED_NODES) {
            fixNodeButton.setSelected(true);
            nodeText.setText(String.valueOf(prop.getAnalysisValue()));
        } else {
            fixDepthButton.setSelected(true);
        }

        engineDelayStart.setText(String.valueOf(prop.getEngineDelayStart()));
        engineDelayEnd.setText(String.valueOf(prop.getEngineDelayEnd()));

        bookDelayStart.setText(String.valueOf(prop.getBookDelayStart()));
        bookDelayEnd.setText(String.valueOf(prop.getBookDelayEnd()));

    }

}
