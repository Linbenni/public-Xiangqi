package com.sojourners.chess.openbook;

import com.sojourners.chess.model.BookData;
import com.sojourners.chess.util.ZobristUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * .pfBook（旋风）开局库。
 */
public class PfOpenBook implements OpenBook {

    private final SqliteAccess access;

    private String name;

    public PfOpenBook(String bookPath) throws Exception {
        this.access = SqliteAccessProvider.open(bookPath);
        this.name = new File(bookPath).getName();
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

        String sql = "SELECT * FROM pfBook WHERE vkey = " + zobrist + " and vvalid = 1;";

        try {
            for (Map<String, Object> row : access.query(sql)) {
                BookData bd = new BookData();
                bd.setScore(BhOpenBook.intValue(row.get("vscore")));
                bd.setWinNum(BhOpenBook.intValue(row.get("vwin")));
                bd.setDrawNum(BhOpenBook.intValue(row.get("vdraw")));
                bd.setLoseNum(BhOpenBook.intValue(row.get("vlost")));
                int winRate = (int) (10000 * (bd.getWinNum() + bd.getDrawNum() / 2.0d) / (bd.getWinNum() + bd.getDrawNum() + bd.getLoseNum()));
                bd.setWinRate(winRate / 100d);
                Object memo = row.get("vmemo");
                bd.setNote(memo == null ? null : memo.toString());
                int vmove = BhOpenBook.intValue(row.get("vmove"));
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
