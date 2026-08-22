package com.sojourners.chess.yolo;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.util.LinkDiagnostics;
import com.sojourners.chess.util.PathUtils;

import java.awt.image.BufferedImage;

public abstract class OnnxModel {

    public static final double PADDING = 0.8d;

    public final float CONFIDENCE = 0.75f;

    public final int SIZE = 640;

    public static final char[] labels = {'n', 'b', 'a', 'k', 'r', 'c', 'p', 'R', 'N', 'A', 'K', 'B', 'C', 'P', '0'};

    OrtSession session;

    OrtEnvironment env;

    public OnnxModel() {
        String path = null;
        try {
            env = OrtEnvironment.getEnvironment();

            OrtSession.SessionOptions opt = new OrtSession.SessionOptions();
            opt.setIntraOpNumThreads(Properties.getInstance().getLinkThreadNum());

            path = PathUtils.getJarPath() + getModelPath();

            session = env.createSession(path, opt);
            LinkDiagnostics.info("[LINK_MODEL] thread=" + Thread.currentThread().getName()
                    + " event=model_loaded path=" + path
                    + " threads=" + Properties.getInstance().getLinkThreadNum());

        } catch (Exception e) {
            LinkDiagnostics.error("[LINK_MODEL] thread=" + Thread.currentThread().getName()
                    + " event=model_load_failed path=" + path
                    + " errorType=" + e.getClass().getName()
                    + " errorMessage=" + String.valueOf(e.getMessage()).replace('\n', ' ').replace('\r', ' '), e);
        }
    }

    public abstract String getModelPath();

    public abstract java.awt.Rectangle findBoardPosition(BufferedImage img);

    public abstract boolean findChessBoard(BufferedImage img, char[][] board);

}
