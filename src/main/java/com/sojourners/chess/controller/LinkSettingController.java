package com.sojourners.chess.controller;

import com.sojourners.chess.App;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.util.DialogUtils;
import com.sojourners.chess.util.StringUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;


public class LinkSettingController {

    @FXML
    private TextField linkScanTime;
    @FXML
    private TextField linkThreadNum;

    @FXML
    private TextField mouseClickDelayStart;

    @FXML
    private TextField mouseClickDelayEnd;

    @FXML
    private TextField mouseMoveDelayStart;

    @FXML
    private TextField mouseMoveDelayEnd;

    @FXML
    private CheckBox linkUseManualBoardRegion;

    private Properties prop;

    @FXML
    void cancelButtonClick(ActionEvent e) {
        App.closeLinkSetting();
    }

    @FXML
    void okButtonClick(ActionEvent e) {

        String txt = linkScanTime.getText();
        if (!StringUtils.isPositiveInt(txt)) {
            DialogUtils.showErrorDialog("失败", "输入扫描时间错误");
            return;
        }
        prop.setLinkScanTime(Long.parseLong(txt));
        txt = linkThreadNum.getText();
        if (!StringUtils.isPositiveInt(txt)) {
            DialogUtils.showErrorDialog("失败", "输入扫描扫描线程数量错误");
            return;
        }
        prop.setLinkThreadNum(Integer.parseInt(txt));

        txt = mouseClickDelayStart.getText();
        if (!StringUtils.isNonNegativeInt(txt)) {
            DialogUtils.showErrorDialog("失败", "输入鼠标点击最小延迟错误");
            return;
        }
        int clickStart = Integer.parseInt(txt);
        txt = mouseClickDelayEnd.getText();
        if (!StringUtils.isNonNegativeInt(txt)) {
            DialogUtils.showErrorDialog("失败", "输入鼠标点击最大延迟错误");
            return;
        }
        int clickEnd = Integer.parseInt(txt);
        if (clickStart > clickEnd) {
            DialogUtils.showErrorDialog("失败", "鼠标点击延迟区间错误（最小值不能大于最大值）");
            return;
        }
        prop.setMouseClickDelayStart(clickStart);
        prop.setMouseClickDelayEnd(clickEnd);

        txt = mouseMoveDelayStart.getText();
        if (!StringUtils.isNonNegativeInt(txt)) {
            DialogUtils.showErrorDialog("失败", "输入鼠标走子最小延迟错误");
            return;
        }
        int moveStart = Integer.parseInt(txt);
        txt = mouseMoveDelayEnd.getText();
        if (!StringUtils.isNonNegativeInt(txt)) {
            DialogUtils.showErrorDialog("失败", "输入鼠标走子最大延迟错误");
            return;
        }
        int moveEnd = Integer.parseInt(txt);
        if (moveStart > moveEnd) {
            DialogUtils.showErrorDialog("失败", "鼠标走子延迟区间错误（最小值不能大于最大值）");
            return;
        }
        prop.setMouseMoveDelayStart(moveStart);
        prop.setMouseMoveDelayEnd(moveEnd);
        prop.setLinkUseManualBoardRegion(linkUseManualBoardRegion.isSelected());

        App.closeLinkSetting();
    }

    @FXML
    void clearManualBoardAreaClick(ActionEvent e) {
        prop.clearLinkBoardArea();
        DialogUtils.showInfoDialog("提示", "已清除手工棋盘区域，下次连线将重新框选。");
    }

    public void initialize() {

        prop = Properties.getInstance();

        linkScanTime.setText(String.valueOf(prop.getLinkScanTime()));
        linkThreadNum.setText(String.valueOf(prop.getLinkThreadNum()));

        mouseClickDelayStart.setText(String.valueOf(prop.getMouseClickDelayStart()));
        mouseClickDelayEnd.setText(String.valueOf(prop.getMouseClickDelayEnd()));
        mouseMoveDelayStart.setText(String.valueOf(prop.getMouseMoveDelayStart()));
        mouseMoveDelayEnd.setText(String.valueOf(prop.getMouseMoveDelayEnd()));
        linkUseManualBoardRegion.setSelected(prop.isLinkUseManualBoardRegion());

    }

}
