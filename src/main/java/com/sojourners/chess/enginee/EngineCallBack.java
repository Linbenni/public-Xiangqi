package com.sojourners.chess.enginee;


import com.sojourners.chess.model.BookData;
import com.sojourners.chess.model.ThinkData;

import java.util.List;

/**
 * 引擎回调
 */
public interface EngineCallBack {

    void bestMove(String first, String second, long searchId);

    void thinkDetail(ThinkData td, long searchId);

    void showBookResults(List<BookData> list);

    /**
     * 因 stop 结束且未进入常规 {@link #bestMove} 逻辑时调用（如无限分析点停），用于把本轮已累计的棋谱分数一次性落表。
     */
    default void searchEndedForTableScore(long searchId) {
    }
}
