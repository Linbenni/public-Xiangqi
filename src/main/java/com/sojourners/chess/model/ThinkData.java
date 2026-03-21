package com.sojourners.chess.model;

import com.sojourners.chess.board.ChessBoard;

import java.util.List;

/**
 * 思考细节数据显示
 */
public class ThinkData {

    private Integer depth;

    private Integer score;

    private Integer mate;

    private Integer pv;

    private Long nps;

    private Long time;

    private List<String> detail;

    private String title;

    private String body;

    private Boolean isValid;

    public ThinkData() {

    }

    public void generate(boolean redGo, boolean isReverse, ChessBoard board) {
        if (pv == null) {
            pv = 1;
        }
        // 无分数则无法参与展示与棋谱列，避免 depth/score 为 null 时拼接出 "null" 或 NPE
        if (score == null && mate == null) {
            title = "（引擎未返回分数）";
            body = "";
            isValid = false;
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("深度: ").append(depth != null ? depth : "—").append("  ");
        sb.append("PV: ").append(pv).append("  ");
        boolean mateLine = false;
        if (score == null) {
            sb.append("绝杀: ");
            score = mate;
            mateLine = true;
        } else {
            sb.append("分数: ");
        }
        if (redGo && isReverse || !redGo && !isReverse) {
            score = -score;
        }
        sb.append(score).append(mateLine ? "步  " : "  ");
        if (nps == null) {
            nps = 0L;
        }
        sb.append("NPS: ").append(nps / 1000).append("K  ");
        if (time == null) {
            time = 0L;
        }
        sb.append("时间: ").append(String.format("%.1fs", time / 1000D));
        title = sb.toString();

        if (detail == null || detail.isEmpty()) {
            body = "";
        } else {
            body = board.translate(detail);
        }
        // 有 PV 文本但解析失败时不刷思考列表，仍允许仅更新棋谱分数（由 Controller 处理）
        isValid = body.isEmpty() || !body.contains("null");
    }

    public Boolean getValid() {
        return isValid;
    }

    public void setValid(Boolean valid) {
        isValid = valid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Integer getMate() {
        return mate;
    }

    public void setMate(Integer mate) {
        this.mate = mate;
    }

    public Integer getDepth() {
        return depth;
    }

    public void setDepth(Integer depth) {
        this.depth = depth;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Long getNps() {
        return nps;
    }

    public void setNps(Long nps) {
        this.nps = nps;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public List<String> getDetail() {
        return detail;
    }

    public void setDetail(List<String> detail) {
        this.detail = detail;
    }

    public Integer getPv() {
        return pv;
    }

    public void setPv(Integer pv) {
        this.pv = pv;
    }
}
