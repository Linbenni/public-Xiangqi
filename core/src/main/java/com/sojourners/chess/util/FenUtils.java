package com.sojourners.chess.util;

import java.util.ArrayList;
import java.util.List;

/**
 * FEN 与着法坐标编码工具（从桌面 ChessBoard 的纯静态逻辑下沉）。
 */
public class FenUtils {

    private FenUtils() {
    }

    /**
     * 将棋盘二维数组编码为 FEN；redGo 为 null 时不附带走子方段。
     */
    public static String fenCode(char[][] board, Boolean redGo) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < board.length; i++) {
            int count = 0;
            for (int j = 0; j < board[0].length; j++) {
                if (board[i][j] != ' ') {
                    if (count != 0) {
                        sb.append(count);
                        count = 0;
                    }
                    sb.append(board[i][j]);
                } else {
                    count++;
                }
            }
            if (count != 0) {
                sb.append(count);
            }
            if (i != board.length - 1) {
                sb.append("/");
            }
        }
        if (redGo != null) {
            if (redGo) {
                sb.append(" w - - 0 1");
            } else {
                sb.append(" b - - 0 1");
            }
        }
        return sb.toString();
    }

    /**
     * 引擎坐标格式：(col 'a'-'i')(row 9-0)(col)(row)。
     */
    public static String stepForEngine(int x1, int y1, int x2, int y2) {
        StringBuffer sb = new StringBuffer();
        sb.append((char) ('a' + x1));
        sb.append(9 - y1);
        sb.append((char) ('a' + x2));
        sb.append(9 - y2);
        return sb.toString();
    }

    /**
     * 按引擎主变顺序逐步翻译着法（逻辑取自桌面 ChessBoard.translateMoves）。
     *
     * @param board 当前局面快照（方法内部复制，不修改入参）
     */
    public static List<String> translateMoves(char[][] board, List<String> moveList) {
        char[][] analysisBoard = new char[10][9];
        for (int i = 0; i < board.length; i++) {
            System.arraycopy(board[i], 0, analysisBoard[i], 0, analysisBoard[i].length);
        }
        List<String> translatedMoves = new ArrayList<>();
        for (String move : moveList) {
            char a = move.charAt(0), b = move.charAt(1), c = move.charAt(2), d = move.charAt(3);
            int fromJ = a - 'a', toJ = c - 'a';
            int fromI = 9 - Integer.parseInt(String.valueOf(b)), toI = 9 - Integer.parseInt(String.valueOf(d));
            StringBuilder translatedMove = new StringBuilder();
            XiangqiUtils.translate(analysisBoard, translatedMove, move, false);
            translatedMoves.add(translatedMove.toString());
            analysisBoard[toI][toJ] = analysisBoard[fromI][fromJ];
            analysisBoard[fromI][fromJ] = ' ';
        }
        return translatedMoves;
    }
}
