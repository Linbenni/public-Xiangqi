package com.sojourners.chess.enginee;


import com.sojourners.chess.config.Properties;
import com.sojourners.chess.model.BookData;
import com.sojourners.chess.model.EngineConfig;
import com.sojourners.chess.model.ThinkData;
import com.sojourners.chess.openbook.OpenBookManager;
import com.sojourners.chess.util.StringUtils;

import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 引擎封装
 */
public class Engine {

    private Process process;

    private String protocol;

    private AnalysisModel analysisModel;
    private long analysisValue;

    private volatile boolean threadNumChange;
    private int threadNum;

    private volatile boolean hashSizeChange;
    private int hashSize;

    /**
     * 停止标志位
     */
    private volatile boolean stopFlag;
    private volatile long time;

    private BufferedReader reader;

    private BufferedWriter writer;

    private EngineCallBack cb;

    private Thread thread;

    private final Object cmdLock = new Object();
    private final Object analysisLock = new Object();

    private Random random;

    private int multiPV;

    private volatile long activeSearchId;
    private volatile boolean searching;
    private volatile CountDownLatch readyLatch;

    public enum AnalysisModel {
        FIXED_TIME,
        FIXED_STEPS,
        INFINITE;
    }

    public Engine(EngineConfig ec, EngineCallBack cb) throws IOException {
        this.protocol = ec.getProtocol();
        this.cb = cb;
        this.random = new SecureRandom();

        this.time = Integer.MAX_VALUE;

        if (ec.getOptions().get("MultiPV") != null) {
            multiPV = Integer.parseInt(ec.getOptions().get("MultiPV"));
        } else {
            multiPV = 1;
        }
        List<String> command = buildCommand(ec.getCommand());
        ProcessBuilder pb = new ProcessBuilder(command);
        // Python/uv 等常把日志打到 stderr，与协议探测 testCommand 行为一致，避免读不到 uciok / info
        pb.redirectErrorStream(true);
        File workDir = resolveWorkDir(ec.getWorkDir(), command);
        if (workDir != null) {
            pb.directory(workDir);
        }
        process = pb.start();
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

        thread = Thread.startVirtualThread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                    if ("readyok".equals(line.trim()) && readyLatch != null) {
                        readyLatch.countDown();
                        continue;
                    }
                    if (isThinkInfoLine(line)) {
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

    /**
     * 与思考相关的 info 行：需包含分数时即使没有 pv 也要解析，否则棋谱「分数」列不会更新。
     */
    private static boolean isThinkInfoLine(String line) {
        if (line == null) {
            return false;
        }
        String t = line.trim();
        // UCI/UCCI 均为 info 前缀；个别引擎输出大写 INFO
        if (!t.toLowerCase(Locale.ROOT).startsWith("info")) {
            return false;
        }
        String u = t.toLowerCase(Locale.ROOT);
        return u.contains("depth") || u.contains("nps") || u.contains("score") || u.contains(" pv")
                || u.contains("multipv") || u.contains("nodes");
    }

    private static List<String> splitArgs(String args) {
        List<String> res = new ArrayList<>();
        if (StringUtils.isEmpty(args)) {
            return res;
        }
        StringBuilder sb = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (quote != 0) {
                if (c == '\\' && i + 1 < args.length()) {
                    char next = args.charAt(i + 1);
                    if (next == quote || next == '\\') {
                        sb.append(next);
                        i++;
                        continue;
                    }
                }
                if (c == quote) {
                    quote = 0;
                } else {
                    sb.append(c);
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (!sb.isEmpty()) {
                    res.add(sb.toString());
                    sb.setLength(0);
                }
                continue;
            }
            sb.append(c);
        }
        if (!sb.isEmpty()) {
            res.add(sb.toString());
        }
        return res;
    }

    /**
     * 将完整启动命令拆成与 ProcessBuilder 一致的参数列表（供「启动文件 + 参数」界面使用）。
     */
    public static List<String> parseCommandLine(String command) {
        if (StringUtils.isEmpty(command)) {
            return new ArrayList<>();
        }
        return splitArgs(command.trim());
    }

    private static List<String> buildCommand(String command) {
        if (StringUtils.isEmpty(command)) {
            throw new IllegalArgumentException("engine command is empty");
        }
        return splitArgs(command.trim());
    }

    private static File resolveWorkDir(String workDir, List<String> command) {
        if (StringUtils.isNotEmpty(workDir)) {
            File dir = new File(workDir);
            if (dir.exists() && dir.isDirectory()) {
                return dir;
            }
        }
        for (String item : command) {
            File f = new File(item);
            if (f.exists()) {
                if (f.isDirectory()) {
                    return f;
                }
                if (f.getParentFile() != null) {
                    return f.getParentFile();
                }
            }
        }
        return null;
    }

    private void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static String test(String filePath, LinkedHashMap<String, String> options) {
        return test(filePath, "", options);
    }

    public static String test(String filePath, String args, LinkedHashMap<String, String> options) {
        return testCommand(StringUtils.isEmpty(args) ? filePath : filePath + " " + args, "", options);
    }

    public static String testCommand(String commandText, String workDir, LinkedHashMap<String, String> options) {
        Process p = null;
        Thread h = null;
        BufferedWriter bw = null;
        BufferedReader br = null;
        try {
            List<String> command = buildCommand(commandText);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            File resolvedWorkDir = resolveWorkDir(workDir, command);
            if (resolvedWorkDir != null) {
                pb.directory(resolvedWorkDir);
            }
            p = pb.start();
            bw = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
            br = new BufferedReader(new InputStreamReader(p.getInputStream()));

            AtomicBoolean f = new AtomicBoolean(false);
            BufferedReader finalBr = br;
            (h = Thread.ofVirtual().unstarted(() -> {
                try {
                    String line;
                    while ((line = finalBr.readLine()) != null) {
                        String trimmed = line.trim();
                        if ("uciok".equals(trimmed) || "ucciok".equals(trimmed)) {
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
                } catch (IOException e) {
                    // 协议探测结束时主线程会关闭流，这里安静退出即可。
                } catch (Exception e) {
                    e.printStackTrace();
                }
            })).start();

            if (!writeProbeCommand(bw, p, "uci")) {
                System.err.println("[Engine.testCommand] 无法写入 uci 探测命令，进程存活=" + (p != null && p.isAlive()));
                return null;
            }
            // uv run / python 冷启动常超过 1s，轮询等待 uciok
            if (waitForProbeOk(f, 25_000)) {
                return "uci";
            }
            System.err.println("[Engine.testCommand] 在超时内未收到 uciok，将尝试 ucci。命令=" + commandText);

            f.set(false);
            if (!writeProbeCommand(bw, p, "ucci")) {
                System.err.println("[Engine.testCommand] 无法写入 ucci 探测命令");
                return null;
            }
            if (waitForProbeOk(f, 25_000)) {
                return "ucci";
            }
            System.err.println("[Engine.testCommand] 协议探测失败：未收到 uciok/ucciok。请检查 PATH、工作目录与命令。退出值="
                    + (p.isAlive() ? "进程仍运行" : String.valueOf(p.exitValue())));

            return null;

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[Engine.testCommand] 异常: " + e.getMessage());
            return null;
        } finally {
            if (p != null) {
                p.destroy();
            }
            if (h != null && h.isAlive()) {
                h.interrupt();
            }
            try {
                if (h != null) {
                    h.join(200);
                }
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

    private static boolean writeProbeCommand(BufferedWriter bw, Process p, String command) throws IOException {
        if (bw == null || p == null || !p.isAlive()) {
            return false;
        }
        bw.write(command + System.lineSeparator());
        bw.flush();
        return true;
    }

    /** 等待引擎返回 uciok / ucciok（由读线程将 f 置 true） */
    private static boolean waitForProbeOk(AtomicBoolean f, long maxWaitMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(maxWaitMs);
        while (System.nanoTime() < deadline) {
            if (f.get()) {
                return true;
            }
            Thread.sleep(50);
        }
        return f.get();
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
        long searchId = this.activeSearchId;

        if (!searching && !stopFlag) {
            return;
        }
        if (stopFlag) {
            stopFlag = false;
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
        searching = false;
        cb.bestMove(str[1], str.length == 4 ? str[3] : null, searchId);
    }
    private void thinkDetail(String msg) {
        if (!searching) {
            return;
        }

        long searchId = this.activeSearchId;
        String[] str = msg.trim().split("\\s+");
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
                        String num = str[i].replace("+", "");
                        if (flag == 1) {
                            td.setNps(Long.parseLong(num));

                        } else if (flag == 2) {
                            td.setTime(Long.parseLong(num));

                        } else if (flag == 3) {
                            td.setDepth(Integer.parseInt(num));

                        } else if (flag == 4) {
                            td.setMate(Integer.parseInt(num));

                        } else if (flag == 5) {
                            td.setScore(Integer.parseInt(num));

                        } else if (flag == 7) {
                            td.setPv(Integer.parseInt(num));
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
                    if (i + 1 >= str.length) {
                        continue;
                    }
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
                }
            }
        }

        if (td.getDepth() != null && td.getDepth() < 5) {
            stopFlag = false;
        }
        if (td.getTime() != null) {
            if (td.getTime() < this.time || td.getTime() > 0 && td.getTime() < 70) {
                stopFlag = false;
            }
            this.time = td.getTime();
        }

        boolean hasPvMoves = td.getDetail() != null && !td.getDetail().isEmpty();
        boolean hasEval = td.getScore() != null || td.getMate() != null;
        if (hasPvMoves || hasEval) {
            cb.thinkDetail(td, searchId);
        }
    }

    public void analysis(String fenCode, List<String> moves, char[][] board, boolean redGo) {
        analysis(fenCode, moves, board, redGo, System.nanoTime());
    }

    public void analysis(String fenCode, List<String> moves, char[][] board, boolean redGo, long searchId) {
        Thread.startVirtualThread(() -> {
            if (Properties.getInstance().getBookSwitch()) {
                long s = System.currentTimeMillis();
                int moveSize = moves == null ? 0 : moves.size();
                List<BookData> results = OpenBookManager.getInstance().queryBook(board, redGo, moveSize / 2 >= Properties.getInstance().getOffManualSteps());
                System.out.println("查询库时间" + (System.currentTimeMillis() - s));
                this.cb.showBookResults(results);
                if (results.size() > 0 && this.analysisModel != AnalysisModel.INFINITE) {
                    if (Properties.getInstance().getBookDelayEnd() > 0 && Properties.getInstance().getBookDelayEnd() >= Properties.getInstance().getBookDelayStart()) {
                        int t = random.nextInt(Properties.getInstance().getBookDelayStart(), Properties.getInstance().getBookDelayEnd());
                        sleep(t);
                    }
                    this.cb.bestMove(results.get(0).getMove(), null, searchId);
                    return;
                }

            }
            this.analysis(fenCode, moves, null, searchId);
        });
    }

    public void analysis(String fenCode, List<String> moves, List<String> tacticList) {
        analysis(fenCode, moves, tacticList, System.nanoTime());
    }

    public void analysis(String fenCode, List<String> moves, List<String> tacticList, long searchId) {
        synchronized (analysisLock) {
            boolean wasSearching = this.searching;
            stop();
            if (wasSearching) {
                waitReady();
            }
            stopFlag = false;

            if (threadNumChange) {
                cmd(("uci".equals(this.protocol) ? "setoption name Threads value " : "setoption Threads ") + threadNum);
                this.threadNumChange = false;
            }
            if (hashSizeChange) {
                cmd(("uci".equals(this.protocol) ? "setoption name Hash value " : "setoption Hash ") + hashSize);
                this.hashSizeChange = false;
            }

            this.activeSearchId = searchId;
            this.searching = true;
            this.time = Integer.MAX_VALUE;

            StringBuilder sb = new StringBuilder();
            sb.append("position fen ").append(fenCode);
            if (moves != null && moves.size() > 0) {
                sb.append(" moves");
                for (String move : moves) {
                    sb.append(" ").append(move);
                }
            }
            cmd(sb.toString());

            boolean hasTactics = tacticList != null && !tacticList.isEmpty();
            if (hasTactics) {
                sb = new StringBuilder();
                sb.append(" searchmoves");
                for (String tactic : tacticList) {
                    sb.append(" ").append(tactic);
                }
            }
            if (analysisModel == AnalysisModel.FIXED_STEPS) {
                cmd("go depth " + analysisValue + (hasTactics ? sb.toString() : ""));
            } else if (analysisModel == AnalysisModel.FIXED_TIME) {
                cmd("go movetime " + analysisValue + (hasTactics ? sb.toString() : ""));
            } else {
                cmd("go infinite" + (hasTactics ? sb.toString() : ""));
            }
        }
    }

    private void waitReady() {
        CountDownLatch latch = new CountDownLatch(1);
        this.readyLatch = latch;
        cmd("isready");
        try {
            latch.await(120, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (this.readyLatch == latch) {
                this.readyLatch = null;
            }
        }
    }

    public void moveNow() {
        cmd("stop");
    }

    public void stop() {
        this.searching = false;
        stopFlag = true;
        cmd("stop");
    }

    private void cmd(String command) {
        System.out.println(command);
        synchronized (cmdLock) {
            try {
                writer.write(command + System.getProperty("line.separator"));
                writer.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
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

    public void setAnalysisModel(AnalysisModel model, long v) {
        this.analysisModel = model;
        this.analysisValue = v;
    }

    public void close() {
        try {
            this.searching = false;

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
