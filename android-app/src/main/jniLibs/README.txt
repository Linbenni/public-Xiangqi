此目录存放 Pikafish 引擎二进制（以 lib*.so 形式打包，安装后落在可执行的
nativeLibraryDir，见 docs/android-engine.md）：

  jniLibs/arm64-v8a/libpikafish.so   （真机 arm64）

二进制不入 git 仓库，克隆后一键恢复：

  Windows:   pwsh -File scripts/fetch-engine.ps1     （官方 release 预编译）
  Linux/mac: ./scripts/fetch-engine.sh
  自编：     ./scripts/build-pikafish.sh <Pikafish 源码目录>（需 NDK r27+）

配套 NNUE 权重 pikafish.nnue 放 android-app/src/main/assets/（同一脚本完成）。
注意：官方引擎需要外部 NNUE 权重（EvalFile），缺权重时人机功能不可用。

未放置引擎时 App 可正常构建运行，但人机/分析模式不可用（界面会提示，仅双人对弈）。
