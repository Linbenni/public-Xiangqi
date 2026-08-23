# 内置引擎（Pikafish）接入说明 — M2

## 为什么是 `libpikafish.so`

targetSdk ≥ 29 后应用数据目录禁止 exec（W^X）。把引擎二进制以 **SO 库** 形式放进
`jniLibs/`，安装时系统会解压到只读的 `nativeLibraryDir`——该目录允许执行。
配合 `android-app/build.gradle.kts` 中的：

```kotlin
packaging { jniLibs { useLegacyPackaging = true } }
```

确保 SO 真正落盘（而非直接从 APK 映射），`AndroidProcessStarter` 即可从
`applicationInfo.nativeLibraryDir/libpikafish.so` 启动引擎并把 stdin/stdout 接入 core 协议层。

## 编译步骤

```bash
git clone --depth 1 https://github.com/official-pikafish/Pikafish.git
export ANDROID_NDK_HOME=/path/to/android-ndk-r27   # r27+
./scripts/build-pikafish.sh ./Pikafish
```

产物：

- `android-app/src/main/jniLibs/arm64-v8a/libpikafish.so`（真机）
- `android-app/src/main/jniLibs/x86_64/libpikafish.so`（模拟器）

## NNUE 权重（可选）

较新的官方 Pikafish 发行版已内嵌 NNUE；若你自编的版本需要外部权重，把
`pikafish.nnue` 放到 `android-app/src/main/assets/pikafish.nnue`，
首次启动会自动释放到 filesDir 并通过 `EvalFile` 选项传给引擎。

## 引擎缺失兜底

未打包 `libpikafish.so` 时 App 正常启动并在界面提示“仅可双人对弈”，
人机模式按钮禁用——便于无引擎环境下的 UI 开发与冒烟测试。

## 许可提醒

Pikafish 为 GPLv3，随 APK 分发须遵守相应义务（见 ANDROID_PLAN.md §8/M5 合规项）。
