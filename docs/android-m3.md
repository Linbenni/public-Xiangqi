# TCHESS 安卓版 — M3 分析功能实现说明

> 对应 `ANDROID_PLAN.md` §4 M3。本文记录模块结构、关键实现与验证方式。

## 功能清单（对照计划）

| 计划项 | 实现 |
|---|---|
| 分析面板：MultiPV 变化列表 | 分析页 `LazyColumn`：每条变化一行（#序号 / 分数 / 深度 / NPS / 时间 / 中文着法串），点击行在盘面画出该变化前两着箭头（桌面 `setTip` 对应物） |
| 评估柱 | 盘面左侧垂直评估柱，红方占比自下而上；sigmoid(cp/400) 映射，绝杀直接给满/空 |
| 思考数据表格 | 「思考记录」表：主变每次迭代一行（深度/分数/NPS/时间），按实质变化去重，上限 128 条（与桌面 `analysisHistory` 一致） |
| 时间控制页 | 设置中心「时间控制」：固定时间/固定深度/固定节点/无限 + 数值输入；校验文案与桌面 `TimeSettingController` 一致（层数错误/节点数错误/时间错误） |
| 引擎管理页 v1 | 设置中心「引擎」：启用开关、线程数 1~4 滑杆、Hash 16/32/64/128/256MB、MultiPV 1~5；默认 Threads=2 / Hash=64（计划建议值） |
| 手机性能档位 | 省电（1 线程/32MB）、均衡（2/64）、全力（4/128）；档位是线程/内存预设，写入后可微调 |
| 发热降频提示 | API 29+ `PowerManager` 热状态监听：SEVERE+ 一律提示、MODERATE 仅全力档提示（顶栏横幅） |

验收标准落实：

- **同一 FEN 与桌面版 MultiPV 输出一致**：变化行文案直接来自 core `ThinkData.generate()`
  （与桌面同一套翻译/标题逻辑），安卓侧只做展示；单测覆盖分数/绝杀/红黑视角换算。
- **列表滚动不掉帧**：reader 线程只写 `PvBoard`（每 pv 保留最新），100ms 节流 flush 后
  才构建 UI 快照（复刻桌面 `pendingAnalysisByPv` + `ANALYSIS_UI_REFRESH_MILLIS` 模式）。

## 模块结构（M3 新增/改动）

```
android-app/src/main/java/com/sojourners/tchess/
├── TchessApp.kt                    # +engineSession（全局唯一引擎会话）
├── MainActivity.kt                 # 底部导航（对弈/分析/设置）+ 发热横幅 + 页面切换时引擎换绑
├── analysis/
│   ├── PvBoard.kt                  # 纯 JVM：PvRow/PvBoard/HistoryEntry/EvalBar/generatePvRow
├── engine/
│   ├── EngineSession.kt            # 引擎会话：懒创建 + 消费方绑定分发 + 设置下发 + 停止
│   └── PikafishProvider.kt         # （注释修正：官方引擎需要外部 NNUE）
├── settings/
│   └── AppSettings.kt              # 纯 JVM：EngineSettings/PerfProfile/TimeControl
├── system/
│   └── ThermalMonitor.kt           # 热状态 StateFlow（API 29+）
├── config/AndroidConfigStore.kt    # +M3 设置持久化（同 JSON 文件扩展字段）
├── game/GameLogic.kt               # +loadPosition（FEN 载入）
└── ui/
    ├── analysis/AnalysisViewModel.kt   # 分析流程 + 节流 flush（桌面同款模式）
    ├── analysis/AnalysisScreen.kt      # 盘面/评估柱/MultiPV 列表/思考记录表
    ├── analysis/AnalysisHandoff.kt     # 对弈→分析 局面交接单槽
    ├── settings/SettingsViewModel.kt   # 设置状态（即时持久化+下发）
    ├── settings/SettingsScreen.kt      # 档位/引擎/时间控制/关于
    └── game/GameScreen.kt              # +「分析」按钮（交接当前局面）
```

## 关键设计

### 引擎共享（对弈/分析同一进程）
- `EngineSession` 持有唯一 core `Engine`；`bind/unbind` 决定回调路由，切页即换绑；
- 离开对弈页若引擎在思考：先 `stop()` 并记 `suspended`，回到对弈页且轮到引擎时自动续算
  （与 M2「进程被杀恢复续算」同一入口 `requestEngineMove`）；
- 离开分析页若在无限分析：自动停止（省电、避免与对弈争抢）；
- core 的代际机制保证旧搜索的 info/bestmove 不会串页（`analysisGeneration` 校验）。

### 设置下发时机
与桌面 `configureEngineForSearch()` 同序：每次 `analysis()` 前
`applySettings(Threads/Hash/MultiPV)` → `setAnalysisModel(模型, 值)` → go。
core 在 go 前统一 setoption，搜索中改设置在下一手生效。

### 对弈与分析的时间策略
- 对弈：沿用 M2 难度档（快棋 1s / 均衡 3s / 深思 8s，固定时间制）；
- 分析：设置中心的四档时间控制（与桌面全局设置语义一致）。

## 引擎二进制（M2 遗留项收尾）

- 官方 release（`Pikafish-2026-01-02`）含 `Android/pikafish-armv8`（静态 AArch64 ELF，
  已验证 `\x7FELF` / `e_machine=0xB7`），改名为 `libpikafish.so` 打入 `jniLibs/arm64-v8a/`；
- **官方引擎需要外部 NNUE 权重**（1.7MB 的引擎不含 43MB 网络）：`pikafish.nnue` 打入
  `assets/`，首启释放到 filesDir 后经 `EvalFile` 注入（`PikafishProvider`）；
- 二进制不入 git（体积原因），克隆后 `scripts/fetch-engine.ps1|sh` 一键从官方 release
  恢复（CI android job 已接入）；x86_64 模拟器版官方未发布，需要时 NDK 自编。

## 验证

```bash
./gradlew :core:test :core:verifyPureJava   # core 回归 + 纯净性（M3 未改 core）
./gradlew :android-app:testDebugUnitTest    # PvBoard/EvalBar/AppSettings 单测
./gradlew :android-app:assembleDebug        # 出 APK（含引擎 + NNUE，约 74MB）
```

APK 抽查：`lib/arm64-v8a/libpikafish.so` 与 `assets/pikafish.nnue` 均在包内。

## 已知边界（后续里程碑）

- 分析页盘面暂不支持点击走子/变招（`analysis(fen, moves, tacticList)` 变招接口已备，M4 复盘接入）；
- 开局库挂载与云库 → M4；主题/横屏平板适配 → M5；
- 发热提示仅横幅（不自动降线程），档位切换由用户决定。
