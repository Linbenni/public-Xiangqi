package com.sojourners.chess.config;

import com.sojourners.chess.openbook.MoveRule;

import java.util.List;

/**
 * 核心层所需的应用配置 SPI。
 * 桌面版由 {@code com.sojourners.chess.config.Properties} 实现；
 * 安卓版后续由 DataStore 等机制实现后注入。
 */
public interface AppConfig {

    int getEngineDelayStart();

    int getEngineDelayEnd();

    int getBookDelayStart();

    int getBookDelayEnd();

    Boolean getBookSwitch();

    Integer getOffManualSteps();

    List<String> getOpenBookList();

    Boolean getUseCloudBook();

    Boolean getLocalBookFirst();

    MoveRule getMoveRule();

    Boolean getOnlyCloudFinalPhase();

    Integer getCloudBookTimeout();
}
