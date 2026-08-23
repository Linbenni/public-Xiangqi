package com.sojourners.chess.enginee;

import com.sojourners.chess.config.AppConfig;
import com.sojourners.chess.config.ConfigProvider;
import com.sojourners.chess.model.BookData;
import com.sojourners.chess.model.EngineConfig;
import com.sojourners.chess.model.ThinkData;
import com.sojourners.chess.openbook.MoveRule;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用 FakeProcessStarter 端到端验证 UCI 协议层：握手、setoption、position/go 组装、bestmove 回调。
 * 不启动真实引擎进程，桌面/安卓通用。
 */
class EngineProtocolTest {

    /** 记录应用写入引擎 stdin 的全部命令。 */
    static class CapturingOutputStream extends OutputStream {
        private final StringBuilder sb = new StringBuilder();

        @Override
        public synchronized void write(int b) {
            sb.append((char) b);
        }

        @Override
        public synchronized String toString() {
            return sb.toString();
        }
    }

    /** 假引擎进程：stdin 被捕获，stdout 由测试线程注入。 */
    static class FakeEngineProcess extends Process {
        final CapturingOutputStream stdin = new CapturingOutputStream();
        final java.io.PipedInputStream out = new java.io.PipedInputStream(65536);
        final java.io.PipedOutputStream outWriter = new java.io.PipedOutputStream();
        volatile boolean alive = true;

        FakeEngineProcess() throws IOException {
            outWriter.connect(out);
        }

        @Override
        public OutputStream getOutputStream() {
            return stdin;
        }

        @Override
        public InputStream getInputStream() {
            return out;
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public void destroy() {
            alive = false;
        }
    }

    static class StubConfig implements AppConfig {
        @Override public int getEngineDelayStart() { return 0; }
        @Override public int getEngineDelayEnd() { return 0; }
        @Override public int getBookDelayStart() { return 0; }
        @Override public int getBookDelayEnd() { return 0; }
        @Override public Boolean getBookSwitch() { return false; }
        @Override public Integer getOffManualSteps() { return 16; }
        @Override public List<String> getOpenBookList() { return Collections.emptyList(); }
        @Override public Boolean getUseCloudBook() { return false; }
        @Override public Boolean getLocalBookFirst() { return true; }
        @Override public MoveRule getMoveRule() { return MoveRule.BEST_SCORE; }
        @Override public Boolean getOnlyCloudFinalPhase() { return false; }
        @Override public Integer getCloudBookTimeout() { return 1000; }
    }

    private static void writeLines(FakeEngineProcess p, String... lines) throws IOException {
        for (String line : lines) {
            p.outWriter.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            p.outWriter.flush();
        }
    }

    private static void awaitStdin(FakeEngineProcess p, String needle, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!p.stdin.toString().contains(needle)) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("等待命令超时: " + needle + "\n已收到:\n" + p.stdin);
            }
            Thread.sleep(10);
        }
    }

    @Test
    void handshakeSetoptionSearchAndBestMoveFlow() throws Exception {
        ConfigProvider.set(new StubConfig());
        FakeEngineProcess proc = new FakeEngineProcess();

        CountDownLatch bestMoveLatch = new CountDownLatch(1);
        AtomicReference<String> bestMoveRef = new AtomicReference<>();
        EngineCallBack cb = new EngineCallBack() {
            @Override
            public void bestMove(String first, String second) {
                bestMoveRef.set(first);
                bestMoveLatch.countDown();
            }

            @Override
            public void thinkDetail(ThinkData td) {
            }

            @Override
            public void showBookResults(List<BookData> list) {
            }
        };

        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        options.put("Threads", "2");
        options.put("Hash", "64");
        Engine engine = new Engine(new EngineConfig("fake", "fake-engine", "uci", options), cb,
                (path, dir) -> proc);

        // 引擎握手应答
        writeLines(proc, "uciok");

        // 触发固定深度分析
        engine.setAnalysisModel(Engine.AnalysisModel.FIXED_STEPS, 5);
        engine.analysis("rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1",
                List.of(), Collections.emptyList());

        awaitStdin(proc, "uci", 5000);
        awaitStdin(proc, "setoption name Threads value 2", 5000);
        awaitStdin(proc, "position fen rnbakabnr", 5000);
        awaitStdin(proc, "go depth 5", 5000);

        // 引擎输出思考与最佳着法
        writeLines(proc,
                "info depth 5 score cp 30 nodes 100 nps 20000 time 5 pv b0c2",
                "bestmove b0c2 ponder h2e2");

        assertTrue(bestMoveLatch.await(5, TimeUnit.SECONDS), "应在超时前收到 bestmove 回调");
        assertEquals("b0c2", bestMoveRef.get());

        engine.close();
    }

    @Test
    void invalidBestMoveIsIgnored() throws Exception {
        ConfigProvider.set(new StubConfig());
        FakeEngineProcess proc = new FakeEngineProcess();

        CountDownLatch latch = new CountDownLatch(1);
        EngineCallBack cb = new EngineCallBack() {
            @Override
            public void bestMove(String first, String second) {
                latch.countDown();
            }

            @Override
            public void thinkDetail(ThinkData td) {
            }

            @Override
            public void showBookResults(List<BookData> list) {
            }
        };

        Engine engine = new Engine(new EngineConfig("fake", "fake-engine", "uci", new LinkedHashMap<>()), cb,
                (path, dir) -> proc);
        writeLines(proc, "uciok");

        // 非法着法（坐标越界）不应触发回调
        writeLines(proc, "bestmove z9z9");
        assertEquals(false, latch.await(300, TimeUnit.MILLISECONDS), "非法 bestmove 不应回调");

        // 合法着法正常回调
        writeLines(proc, "bestmove a0a1");
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        engine.close();
    }
}
