package com.sojourners.chess.enginee;


import com.sojourners.chess.config.Properties;
import com.sojourners.chess.model.BookData;
import com.sojourners.chess.model.EngineConfig;
import com.sojourners.chess.model.ThinkData;
import com.sojourners.chess.openbook.OpenBookManager;
import com.sojourners.chess.util.PathUtils;
import com.sojourners.chess.util.StringUtils;

import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 引擎封装
 */
public class Engine {

    private static final boolean ENGINE_VERBOSE = Boolean.getBoolean("xiangqi.engine.verbose");

    private Process process;

    private String protocol;

    private AnalysisModel analysisModel;
    private long analysisValue;

    private volatile boolean threadNumChange;
    private int threadNum;

    private volatile boolean hashSizeChange;
    private int hashSize;

    private static final long STOP_WAIT_MILLIS = 1500L;

    /**
     * 每次新的分析请求都会递增。异步开局库查询或旧搜索输出只有代际仍然匹配时才允许生效。
     */
    private final AtomicLong analysisGeneration = new AtomicLong();

    /**
     * 串行化 stop -> position -> go 切换，避免多个异步请求交叉写入引擎 stdin。
     */
    private final Object analysisCommandLock = new Object();

    /**
     * 当前真正已经发送 go 的搜索状态。bestmove 会在 reader 线程中将其标记为结束。
     */
    private final Object searchStateLock = new Object();
    private volatile boolean searchActive;
    private volatile long runningGeneration;

    private BufferedReader reader;

    private BufferedWriter writer;

    private EngineCallBack cb;

    private Thread thread;

    private Random random;

    private final String multiPVOptionName;
    private volatile boolean multiPVChange;
    private int multiPV = 1;

    public enum AnalysisModel {
        FIXED_TIME,
        FIXED_STEPS,
        FIXED_NODES,
        INFINITE;
    }

    public Engine(EngineConfig ec, EngineCallBack cb) throws IOException {
        this.protocol = ec.getProtocol();
        this.cb = cb;
        this.random = new SecureRandom();

        multiPVOptionName = ec.getOptions().keySet().stream()
                .filter(name -> "MultiPV".equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
        multiPVChange = supportsMultiPV();

        process = Runtime.getRuntime().exec(ec.getPath(), null, PathUtils.getParentDir(ec.getPath()));
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

        thread = Thread.startVirtualThread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (ENGINE_VERBOSE || !line.startsWith("info ")) {
                        System.out.println(line);
                    }
                    if (line.contains("depth") || line.contains("nps")) {
                        thinkDetail(line);
                    } else if (line.contains("bestmove")) {
                        bestMove(line);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        cmd(protocol);

        for (Map.Entry<String, String> entry : ec.getOptions().entrySet()) {
            // MultiPV 由全局时间设置统一管理，旧的单引擎值仅保留用于配置兼容。
            if (entry.getKey().equalsIgnoreCase("MultiPV")) {
                continue;
            }
            if ("uci".equals(this.protocol)) {
                cmd("setoption name " + entry.getKey() + " value " + entry.getValue());
            } else if ("ucci".equals(this.protocol)) {
                cmd("setoption " + entry.getKey() + " " + entry.getValue());
            }
        }
    }

    public int getMultiPV() {
        return multiPV;
    }

    public boolean supportsMultiPV() {
        return multiPVOptionName != null;
    }

    private void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static String test(String filePath, LinkedHashMap<String, String> options) {
        Process p = null;
        Thread h = null;
        BufferedWriter bw = null;
        BufferedReader br = null;
        try {
            p = Runtime.getRuntime().exec(filePath);
            bw = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
            br = new BufferedReader(new InputStreamReader(p.getInputStream()));

            AtomicBoolean f = new AtomicBoolean(false);
            BufferedReader finalBr = br;
            (h = Thread.ofVirtual().unstarted(() -> {
                try {
                    String line;
                    while ((line = finalBr.readLine()) != null) {
                        if ("uciok".equals(line) || "ucciok".equals(line) ) {
                            f.set(true);
                        }
                        if (line.startsWith("option") && line.contains("name") && line.contains("type") && line.contains("default")
                                && !line.contains("Threads") && !line.contains("Hash")) {

                            String[] str = line.split("name|type|default");
                            String key = str[1].trim();
                            String value = str[3].trim().split(" ")[0];
                            options.put(key, value);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            })).start();

            bw.write("uci" + System.getProperty("line.separator"));
            bw.flush();
            Thread.sleep(1000);
            if (f.get()) {
                return "uci";
            }

            bw.write("ucci" + System.getProperty("line.separator"));
            bw.flush();
            Thread.sleep(1000);
            if (f.get()) {
                return "ucci";
            }

            return null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (p != null) {
                p.destroy();
            }
            if (h.isAlive()) {
                h.interrupt();
            }
            try {
                if (bw != null) {
                    bw.close();
                }
                if (br != null) {
                    br.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private boolean validateMove(String move) {
        if (StringUtils.isEmpty(move) || move.length() != 4) {
            return false;
        }
        if (move.charAt(0) < 'a' || move.charAt(0) > 'i' || move.charAt(2) < 'a' || move.charAt(2) > 'i') {
            return false;
        }
        if (move.charAt(1) < '0' || move.charAt(1) > '9' || move.charAt(3) < '0' || move.charAt(3) > '9') {
            return false;
        }
        return true;
    }
    private void bestMove(String msg) {
        long completedGeneration;
        synchronized (searchStateLock) {
            completedGeneration = runningGeneration;
            searchActive = false;
            searchStateLock.notifyAll();
        }

        if (completedGeneration != analysisGeneration.get()) {
            return;
        }

        String[] str = msg.split(" ");
        if (str.length < 2 || !validateMove(str[1])) {
            return;
        }
        if (Properties.getInstance().getEngineDelayEnd() > 0 && Properties.getInstance().getEngineDelayEnd() >= Properties.getInstance().getEngineDelayStart()) {
            int t = random.nextInt(Properties.getInstance().getEngineDelayStart(), Properties.getInstance().getEngineDelayEnd());
            sleep(t);
        }
        if (completedGeneration == analysisGeneration.get()) {
            cb.bestMove(str[1], str.length == 4 ? str[3] : null);
        }
    }
    private void thinkDetail(String msg) {
        long detailGeneration = runningGeneration;
        if (detailGeneration != analysisGeneration.get()) {
            return;
        }

        String[] str = msg.split(" ");
        ThinkData td = new ThinkData();
        List<String> detail = new ArrayList<>();
        td.setDetail(detail);
        int flag = 0;
        for (int i = 0; i < str.length; i++) {
            if (flag != 0) {
                if (flag == 6) {
                    detail.add(str[i]);
                } else {
                    if (StringUtils.isDigit(str[i])) {
                        if (flag == 1) {
                            td.setNps(Long.parseLong(str[i]));

                        } else if (flag == 2) {
                            td.setTime(Long.parseLong(str[i]));

                        } else if (flag == 3) {
                            td.setDepth(Integer.parseInt(str[i]));

                        } else if (flag == 4) {
                            td.setMate(Integer.parseInt(str[i]));

                        } else if (flag == 5) {
                            td.setScore(Integer.parseInt(str[i]));

                        } else if (flag == 7) {
                            td.setPv(Integer.parseInt(str[i]));

                        } else if (flag == 8) {
                            td.setNodes(Long.parseLong(str[i]));
                        }
                        flag = 0;
                    } else {
                        continue;
                    }
                }
            } else {
                if ("depth".equals(str[i])) {
                    flag = 3;
                } else if ("score".equals(str[i])) {
                    if ("mate".equals(str[i + 1])) {
                        flag = 4;
                    } else {
                        flag = 5;
                    }
                } else if ("mate".equals(str[i])) {
                    flag = 4;
                } else if ("nps".equals(str[i])) {
                    flag = 1;
                } else if ("time".equals(str[i])) {
                    flag = 2;
                } else if ("pv".equals(str[i])) {
                    flag = 6;
                } else if ("multipv".equals(str[i])) {
                    flag = 7;
                } else if ("nodes".equals(str[i])) {
                    flag = 8;
                }
            }
        }

        if (td.getDetail().size() > 0 && detailGeneration == analysisGeneration.get()) {
            cb.thinkDetail(td);
        }
    }

    public void analysis(String fenCode, List<String> moves, char[][] board, boolean redGo, boolean allowBookMove) {
        long generation = beginAnalysisRequest();
        List<String> movesSnapshot = moves == null ? Collections.emptyList() : List.copyOf(moves);
        char[][] boardSnapshot = copyBoard(board);

        Thread.startVirtualThread(() -> {
            if (Properties.getInstance().getBookSwitch()) {
                long s = System.currentTimeMillis();
                List<BookData> results = OpenBookManager.getInstance().queryBook(
                        boardSnapshot,
                        redGo,
                        movesSnapshot.size() / 2 >= Properties.getInstance().getOffManualSteps());
                System.out.println("查询库时间" + (System.currentTimeMillis() - s));

                if (!isCurrentAnalysis(generation)) {
                    return;
                }

                this.cb.showBookResults(results);
                if (allowBookMove && !results.isEmpty()) {
                    if (Properties.getInstance().getBookDelayEnd() > 0
                            && Properties.getInstance().getBookDelayEnd() >= Properties.getInstance().getBookDelayStart()) {
                        int t = random.nextInt(Properties.getInstance().getBookDelayStart(), Properties.getInstance().getBookDelayEnd());
                        sleep(t);
                    }
                    if (isCurrentAnalysis(generation)) {
                        this.cb.bestMove(results.get(0).getMove(), null);
                    }
                    return;
                }
            }

            startEngineAnalysis(generation, fenCode, movesSnapshot, null);
        });
    }

    public void analysis(String fenCode, List<String> moves, List<String> tacticList) {
        long generation = beginAnalysisRequest();
        List<String> movesSnapshot = moves == null ? Collections.emptyList() : List.copyOf(moves);
        List<String> tacticSnapshot = tacticList == null ? Collections.emptyList() : List.copyOf(tacticList);
        Thread.startVirtualThread(() -> startEngineAnalysis(generation, fenCode, movesSnapshot, tacticSnapshot));
    }

    private long beginAnalysisRequest() {
        long generation = analysisGeneration.incrementAndGet();
        requestStop();
        return generation;
    }

    private boolean isCurrentAnalysis(long generation) {
        return generation == analysisGeneration.get();
    }

    private void startEngineAnalysis(long generation, String fenCode, List<String> moves, List<String> tacticList) {
        synchronized (analysisCommandLock) {
            if (!isCurrentAnalysis(generation) || !waitForPreviousSearch(generation)) {
                return;
            }

            if (threadNumChange) {
                cmd(("uci".equals(this.protocol) ? "setoption name Threads value " : "setoption Threads ") + threadNum);
                this.threadNumChange = false;
            }
            if (hashSizeChange) {
                cmd(("uci".equals(this.protocol) ? "setoption name Hash value " : "setoption Hash ") + hashSize);
                this.hashSizeChange = false;
            }
            if (multiPVChange) {
                if (supportsMultiPV()) {
                    cmd(("uci".equals(this.protocol)
                            ? "setoption name " + multiPVOptionName + " value "
                            : "setoption " + multiPVOptionName + " ") + multiPV);
                }
                this.multiPVChange = false;
            }

            if (!isCurrentAnalysis(generation)) {
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("position fen ").append(fenCode);
            if (!moves.isEmpty()) {
                sb.append(" moves");
                for (String move : moves) {
                    sb.append(" ").append(move);
                }
            }
            cmd(sb.toString());

            boolean hasTactics = tacticList != null && !tacticList.isEmpty();
            String searchMoves = "";
            if (hasTactics) {
                sb = new StringBuilder(" searchmoves");
                for (String tactic : tacticList) {
                    sb.append(" ").append(tactic);
                }
                searchMoves = sb.toString();
            }

            String goCommand;
            if (analysisModel == AnalysisModel.FIXED_STEPS) {
                goCommand = "go depth " + analysisValue + searchMoves;
            } else if (analysisModel == AnalysisModel.FIXED_TIME) {
                goCommand = "go movetime " + analysisValue + searchMoves;
            } else if (analysisModel == AnalysisModel.FIXED_NODES) {
                goCommand = "go nodes " + analysisValue + searchMoves;
            } else {
                goCommand = "go infinite" + searchMoves;
            }

            synchronized (searchStateLock) {
                if (!isCurrentAnalysis(generation)) {
                    return;
                }
                runningGeneration = generation;
                searchActive = true;
                cmd(goCommand);
            }
        }
    }

    private boolean waitForPreviousSearch(long generation) {
        long deadline = System.nanoTime() + STOP_WAIT_MILLIS * 1_000_000L;
        synchronized (searchStateLock) {
            while (searchActive && isCurrentAnalysis(generation)) {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    System.err.println("引擎 stop 超时，放弃本次新搜索以避免与旧搜索重叠");
                    return false;
                }
                try {
                    long millis = Math.max(1L, remainingNanos / 1_000_000L);
                    searchStateLock.wait(millis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return isCurrentAnalysis(generation);
    }

    private void requestStop() {
        boolean shouldStop;
        synchronized (searchStateLock) {
            shouldStop = searchActive;
        }
        if (shouldStop) {
            cmd("stop");
        }
    }

    private char[][] copyBoard(char[][] source) {
        if (source == null) {
            return null;
        }
        char[][] copy = new char[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = Arrays.copyOf(source[i], source[i].length);
        }
        return copy;
    }

    public void moveNow() {
        cmd("stop");
    }

    public void stop() {
        analysisGeneration.incrementAndGet();
        requestStop();
    }

    private void cmd(String command) {
        System.out.println(command);
        try {
            writer.write(command + System.getProperty("line.separator"));
            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setThreadNum(int threadNum) {
        if (threadNum != this.threadNum) {
            this.threadNum = threadNum;
            this.threadNumChange = true;
        }

    }

    public void setHashSize(int hashSize) {
        if (hashSize != this.hashSize) {
            this.hashSize = hashSize;
            this.hashSizeChange = true;
        }
    }

    public void setMultiPV(int multiPV) {
        int effectiveValue = supportsMultiPV() ? Math.max(1, multiPV) : 1;
        if (effectiveValue != this.multiPV) {
            this.multiPV = effectiveValue;
            this.multiPVChange = supportsMultiPV();
        }
    }

    public void setAnalysisModel(AnalysisModel model, long v) {
        this.analysisModel = model;
        this.analysisValue = v;
    }

    public void close() {
        try {
            if (process.isAlive()) {
                cmd("quit");
            }

            if (thread.isAlive()) {
                thread.interrupt();
            }

            if (process.isAlive()) {
                process.destroy();
            }

            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
