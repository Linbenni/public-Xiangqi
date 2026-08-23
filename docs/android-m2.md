# TCHESS 安卓版 — M2 对弈闭环实现说明

> 对应 `ANDROID_PLAN.md` §4 M2。本文记录模块结构、关键实现与验证方式。

## 模块结构

```
android-app/
├── build.gradle.kts            # AGP 8.13 / Kotlin 2.2 / Compose BOM；useLegacyPackaging=true
├── src/main/
│   ├── AndroidManifest.xml     # 前台 Service(dataSync)、通知权限
│   ├── assets/
│   │   ├── board/*.png         # 复用桌面 ui/board.png + 14 枚棋子 PNG
│   │   ├── sound/*.wav         # 复用桌面 click/move/capture/check/win
│   │   └── font/chessman.ttf   # 备用字体（当前棋子走 PNG 方案）
│   ├── jniLibs/                # 放置 libpikafish.so（见 docs/android-engine.md）
│   └── java/com/sojourners/tchess/
│       ├── TchessApp.kt        # 注入 ConfigProvider（core SPI）
│       ├── MainActivity.kt     # 前后台 Service 切换、通知权限
│       ├── config/AndroidConfigStore.kt   # AppConfig SPI 实现（JSON 持久化）
│       ├── engine/AndroidProcessStarter.kt# nativeLibraryDir 启动引擎
│       ├── engine/PikafishProvider.kt     # 引擎装配 + NNUE 可选释放 + 缺失兜底
│       ├── service/EngineService.kt       # 对局中前台保活
│       ├── sound/SoundManager.kt          # SoundPool + 触感
│       ├── game/GameLogic.kt              # 规则/局面/胜负（纯 JVM 可测）
│       └── ui/
│           ├── board/BoardGeometry.kt     # 几何换算（与桌面同一套数学）
│           ├── board/BoardAssets.kt       # 素材加载
│           ├── board/BoardPainter.kt      # Canvas 绘制（标记/提示/动画层）
│           └── game/GameViewModel.kt      # 对局流程状态机 + 引擎对接 + 存档恢复
└── src/test/java/...           # BoardGeometry / GameLogic 单测（JVM）
```

## 关键实现对照（M2 清单）

| 计划项 | 实现 |
|---|---|
| Pikafish NDK 编译脚本 | `scripts/build-pikafish.sh`（arm64-v8a + x86_64，strip 后改名 libpikafish.so）|
| useLegacyPackaging | `android { packaging { jniLibs { useLegacyPackaging = true } } }` |
| AndroidProcessStarter | 从 `applicationInfo.nativeLibraryDir` 启动，stdin/stdout 接入 core `Engine` 协议层 |
| Compose Canvas 棋盘 | 复用桌面 PNG 素材；几何移植自 `BaseBoardRender`；选中/上一步四角括号、合法着法圆点、吃子圆环、将军红环、150ms 走子插值动画 |
| 对局流程 | 先后手选择（执红/执黑/双人）、悔棋（自动撤到人类行棋）、新局、认输、绝杀/困毙判定 |
| 音效/触感 | SoundPool 加载桌面 wav；选子/走子/吃子/将军/胜利分级震动 |
| 生命周期 | 切后台启动前台 Service 保活引擎进程；对局状态 JSON 落盘，进程被杀后自动恢复并续算 |
| 引擎缺失兜底 | 未打包 .so 时界面提示，人机模式禁用，双人对弈可用 |

## 难度档位

固定时间制（`Engine.AnalysisModel.FIXED_TIME`）：快棋 1s / 均衡 3s / 深思 8s。
引擎参数 Threads=2、Hash=64MB（M3 设置页开放调节）。

## 验证

```bash
./gradlew :core:test :core:verifyPureJava   # core 回归 + 纯净性
./gradlew :android-app:testDebugUnitTest    # 安卓侧 JVM 单测
./gradlew :android-app:assembleDebug        # 出 APK（无引擎也可构建）
```

真机验收（按计划）：完成一整局人机对弈、切后台返回正常、无崩溃——需在放入
libpikafish.so 的包上进行。

## 已知边界（后续里程碑）

- MultiPV 提示箭头、思考数据面板 → M3
- 开局库挂载（bookSwitch 默认关）→ M4
- 主题/横屏平板适配 → M5
