package com.sojourners.chess.openbook;

import com.sojourners.chess.model.BookData;
import com.sojourners.chess.util.ZobristUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * .obk（兵河）开局库。
 */
public class BhOpenBook implements OpenBook {

    private final SqliteAccess access;

    private String name;

    public BhOpenBook(String bookPath) throws Exception {
        this.access = SqliteAccessProvider.open(bookPath);
        this.name = new File(bookPath).getName();
    }

    /**
     * SqliteAccess 行值统一转 int（兼容 Integer/Long 等数值类型）。
     */
    static int intValue(Object v) {
        return v == null ? 0 : ((Number) v).intValue();
    }

    @Override
    public List<BookData> get(char[][] board, boolean redGo) {

        long zobrist = ZobristUtils.getZobristFromBoard(board, redGo, false);
        List<BookData> results = get(zobrist, false);

        zobrist = ZobristUtils.getZobristFromBoard(board, redGo, true);
        results.addAll(get(zobrist, true));

        return results;
    }

    private List<BookData> get(long zobrist, boolean leftRightSwap) {
        List<BookData> results = new ArrayList<>();

        String sql;
        if (zobrist < 0) {
            double zobristDouble = Double.longBitsToDouble(zobrist);
            sql = "SELECT * FROM bhobk WHERE cast(vkey as double) = " + zobristDouble + " and vvalid = 1;";
        } else {
            sql = "SELECT * FROM bhobk WHERE cast(vkey as integer) = " + zobrist + " and vvalid = 1;";
        }

        try {
            for (Map<String, Object> row : access.query(sql)) {
                BookData bd = new BookData();
                bd.setScore(intValue(row.get("vscore")));
                bd.setWinNum(intValue(row.get("vwin")));
                bd.setDrawNum(intValue(row.get("vdraw")));
                bd.setLoseNum(intValue(row.get("vlost")));
                int winRate = (int) (10000 * (bd.getWinNum() + bd.getDrawNum() / 2.0d) / (bd.getWinNum() + bd.getDrawNum() + bd.getLoseNum()));
                bd.setWinRate(winRate / 100d);
                Object memo = row.get("vmemo");
                bd.setNote(memo == null ? null : memo.toString());
                int vmove = intValue(row.get("vmove"));
                bd.setMove(ZobristUtils.getMoveFromVmove(vmove, leftRightSwap));

                bd.setSource(this.name);
                results.add(bd);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return results;
    }

    @Override
    public List<BookData> get(String fenCode, boolean onlyFinalPhase) {
        return null;
    }

    @Override
    public void close() {
        try {
            this.access.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
