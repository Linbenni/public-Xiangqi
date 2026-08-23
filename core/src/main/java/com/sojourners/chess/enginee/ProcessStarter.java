package com.sojourners.chess.enginee;

import java.io.File;
import java.io.IOException;

/**
 * 引擎进程启动 SPI。
 * 桌面版直接用默认实现（ProcessBuilder）；
 * 安卓版可注入从 nativeLibraryDir 解析路径后的启动逻辑。
 */
public interface ProcessStarter {

    Process start(String executablePath, File workDir) throws IOException;
}
