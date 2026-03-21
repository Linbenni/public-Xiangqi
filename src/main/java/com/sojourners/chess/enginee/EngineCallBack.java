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
}
