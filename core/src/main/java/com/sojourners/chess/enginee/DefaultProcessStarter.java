package com.sojourners.chess.enginee;

import java.io.File;
import java.io.IOException;

/**
 * 默认进程启动实现：与原 Runtime.exec(path, null, dir) 行为等价。
 */
public class DefaultProcessStarter implements ProcessStarter {

    @Override
    public Process start(String executablePath, File workDir) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(executablePath);
        if (workDir != null) {
            pb.directory(workDir);
        }
        return pb.start();
    }
}
