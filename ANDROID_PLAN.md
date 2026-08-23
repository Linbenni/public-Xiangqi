# TCHESS 安卓版开发计划（B 方案：共享核心 + 原生 UI）

> 方案 B：抽出纯 Java 逻辑层供桌面/安卓共用，UI 用 Kotlin + Jetpack Compose 重写。
> 本计划基于对当前代码库的分析（69 个 Java 文件 / 约 1.2 万行 / JavaFX 23 + JDK 21）。
> 状态：已定稿（关键决策已确认，见 §7）。

---

## 1. 目标与范围

### v1 目标
在安卓（arm64 为主）上提供与桌面版对等的**对弈、分析、棋谱、开局库**四大核心能力，
UI 为原生触屏体验。

### 明确不做（v1）
| 功能 | 原因 |
|---|---|
| 连线（截屏识别外部棋软 + 注入点击） | 安卓需 MediaProjection + AccessibilityService，政策风险高，体验无保证 |
| YOLO 棋盘识别 | 依附于连线功能，一并延后 |
| 用户任意导入引擎 | targetSdk ≥ 29 禁止执行应用数据目录文件，技术上不可行（见 §5.1） |
| 桌面专属：窗口置顶、全局鼠标钩子、剪贴板图片互操作 | 安卓无对应概念 |

### 可选扩展（v2，另行立项）
- **远程引擎桥**：手机作为界面，引擎跑在局域网 PC（复用 UCI 协议层，工作量小，推荐优先）
- **完整连线**：MediaProjection + 无障碍服务 + onnxruntime-android 重写 YOLO 推理

---

## 2. 技术选型

| 项 | 选择 | 说明 |
|---|---|---|
| 语言 | Kotlin 2.x + Java 17（core 保持纯 Java，便于双向调用） | |
| UI | Jetpack Compose + Material 3 | 棋盘用 Canvas 自绘 |
| 构建 | Gradle（Kotlin DSL）；AGP 8.x | 现有桌面 Maven 工程不动 ⚠️ |
| minSdk / targetSdk | 24 / 最新稳定 ⚠️ | minSdk 24 ≈ Android 7，覆盖 97%+ 设备 |
| ABI | arm64-v8a（正式）+ x86_64（模拟器调试） | |
| 引擎 | Pikafish（官方持续发版，GPL，NDK 可编译）以 lib*.so 内置 | |
| NN 推理（v2 才需要） | onnxruntime-android AAR | 替代桌面版 onnxruntime jar |
| 存储 | 引擎配置/界面设置 → DataStore(JSON)；开局库索引 → 框架 SQLite | 替代 Java 序列化 + sqlite-jdbc |
| DI/架构 | 轻量手写（不引 Hilt），MVVM + StateFlow | |

---

## 3. 总体架构

```
┌────────────────────────────┐     ┌──────────────────────────────┐
│  desktop（现有工程，Maven） │     │  android-app（新建，Gradle）   │
│  JavaFX UI / 控制器         │     │  Compose UI / ViewModel       │
│  linker / yolo / jna ...    │     │  SAF / SoundPool / Service    │
│  sqlite-jdbc / 序列化配置    │     │  framework SQLite / DataStore │
└──────────────┬─────────────┘     └──────────────┬───────────────┘
               │      实现平台接口(SPI)             │
               └──────────────┬────────────────────┘
                              ▼
                ┌──────────────────────────┐
                │  core（纯 Java library）  │
                │  引擎协议 / 棋谱解析        │
                │  开局库解析 / 局面与工具    │
                └──────────────────────────┘
```

### 3.1 core 模块内容（从现代码迁入）

| 来源 | 迁入后职责 | 需要的改造 |
|---|---|---|
| `enginee/Engine.java` | 拆为 `EngineIo`（进程+流，启动方式可注入）+ 协议状态机（uci/ucci 命令与 info/bestmove 解析） | `Runtime.exec` 收敛到 `ProcessStarter` SPI；虚拟线程→线程池 |
| `manual/` 全部（Cbr/Pgn/Txq/Xqf + ChessManualService） | 原样迁入 | 仅用 java.io/nio，基本免改；确认 GBK 编码显式声明 |
| `openbook/`（OpenBook、OpenBookManager、Bh/Pf/Xqb/Cloud、MoveRule） | 格式解析与查询逻辑 | JDBC 访问收敛为 `SqliteAccess` SPI（仅少量只读查询）；`ChessBoard.fenCode()` 下沉进来 |
| `model/` 全部（BookData、EngineConfig、LocalBook、ManualRecord、ThinkData） | 原样迁入 | 免改 |
| `util/`：XiangqiUtils、ZobristUtils、MathUtils、StringUtils、DateUtils、HttpUtils、ExecutorsUtils | 原样迁入 | ExecutorsUtils 去虚拟线程化 |
| `lock/`（SingleLock、WorkerTask） | 原样迁入 | 免改 |
| `board/ChessBoard.java` 中的纯静态函数（fenCode、坐标换算等） | 下沉为 `Fen` / `BoardGeometry` | 从 JavaFX 类剥离 |

### 3.2 留在 desktop 的代码（不迁移）
`App/Main`、`controller/*`、`board/*`（JavaFX 渲染）、`menu`、`linker/*`、`jna/*`、`mouse/*`、
`yolo/*`、`media/SoundPlayer`、`config/Properties`（改为实现 core 的 `ConfigStore` SPI）、
`util/` 中 PathUtils、ClipboardUtils、ShellUtils、SystemUtils、DialogUtils、LinkDiagnostics。

### 3.3 core 必须遵守的约束（CI 强制检查）
禁止 import：`javafx.*`、`java.awt.*`、`javax.swing.*`、`com.sun.jna`、`org.jnativehook`、
`java.sql.*`（SQLite 经 SPI）、`Thread.ofVirtual/startVirtualThread`。

---

## 4. 里程碑计划

总周期估算：**v1 约 7~12 周**（单人全职）。每个里程碑结束都有可运行产物。

### M0 准备与骨架（0.5 周）
- [x] 关键决策已确认（§7）
- [x] 环境：Android Studio、SDK 35、NDK r27+、arm64 真机一台（构建环境已就绪：JDK21/Maven/Gradle 8.14.3；NDK 与真机在 M2 前备齐）
- [x] 建仓：`core/`（Gradle java-library）+ 现有工程原地不动（android-app 于 M2 创建）
- [x] CI：GitHub Actions —— core 单测 + 桌面 `mvn package` 回归（`.github/workflows/ci.yml`）
- 交付物：空壳 App 可装机、CI 绿 → 已交付 core 模块 + wrapper + CI

### M1 core 抽取（1~2 周）—— ✅ 已完成
- [x] 按 §3.1 迁类；`Engine` 经 `ProcessStarter` SPI 注入启动方式（协议逻辑保持原实现）
- [x] 虚拟线程 6 处 → 平台线程/守护线程
- [x] `SqliteAccess` SPI + desktop 的 sqlite-jdbc 实现（`JdbcSqliteAccess`）
- [x] `AppConfig` SPI + desktop 序列化实现（旧 properties 文件保持兼容）
- [x] `fenCode`/`stepForEngine`/`translateMoves` 下沉为 `util.FenUtils`；`ChessBoard.Point/Step` 下沉为顶层 `BoardPoint`/`MoveStep`
- [x] 单测：FEN round-trip、UCI 协议端到端（FakeProcessStarter）、translateMoves 快照不变式；`verifyPureJava` 依赖约束任务挂入 check
- 验收结果：core 8 测试全绿；桌面 `mvn clean package` 全量回归通过；core 通过纯净性检查（无 javafx/awt/jna/jnativehook/java.sql/虚拟线程）

### M2 安卓 MVP：对弈闭环（2~3 周）★ 最大技术风险在此消化 —— 代码完成 ✅（真机验收待引擎二进制）
- [x] Pikafish NDK 编译脚本（`scripts/build-pikafish.sh`，arm64-v8a/x86_64），strip 后改名
      `libpikafish.so` 放 `jniLibs/`；打包开启 `useLegacyPackaging=true`（保证解压出可执行位）
- [x] `AndroidProcessStarter`：从 `applicationInfo.nativeLibraryDir` 启动引擎，
      stdin/stdout 接入 core 协议层；NNUE 可选 assets 释放 + EvalFile 注入；引擎缺失兜底（仅双人模式）
- [x] 主界面：Compose Canvas 棋盘（复用 `ui/board.png` 与棋子 PNG），触屏选子/
      合法着法提示/吃子环/将军警示/走子动画；几何移植自 `BaseBoardRender`（`ui/board/BoardGeometry.kt`）
- [x] 对局流程：先后手选择（执红/执黑/双人）、人机双方、悔棋、新局、认输、胜负判定音效
      （难度=固定时间制：快棋1s/均衡3s/深思8s）
- [x] 音效 SoundPool（复用 wav）、触感反馈
- [x] 生命周期：对局中前台 Service 保引擎进程；被杀后恢复局面（JSON 存档+自动续算）
- [x] 构建验证：`:android-app:assembleDebug` 通过、安卓 JVM 单测与 `:core:test` 回归通过；
      CI 增加 android job；实现说明见 `docs/android-m2.md`
- [x] 引擎上机：官方 release（2026-01-02）Android/armv8 二进制改名 `libpikafish.so` 打包，
      外部 NNUE 权重随 assets 释放（`scripts/fetch-engine.ps1|sh` 一键恢复，二进制不入库）——
      真机整局验收仍待 arm64 真机

### M3 分析功能（1~2 周）—— ✅ 已完成（真机回归待引擎装机验证）
- [x] 分析面板：MultiPV 变化列表、评估柱、思考数据表格（ThinkData 直接复用，
      文案与桌面同源保证输出一致；100ms 节流 flush 保证滚动流畅）
- [x] 时间控制页：固定时间/固定步数/固定节点/无限（`Engine.AnalysisModel` 复用）
- [x] 引擎管理页 v1：内置引擎参数（Threads 1~4、Hash 16~256MB 默认 2/64）、MultiPV、启用开关
- [x] 手机性能档位：省电/均衡/全力（线程+Hash 预设）；发热降频提示（API29+ 热状态监听横幅）
- 验收结果：core 测试与纯净性检查全绿；安卓侧新增 PvBoard/EvalBar/AppSettings 单测全绿；
  `assembleDebug` 出包且含 libpikafish.so + pikafish.nnue；实现说明见 `docs/android-m3.md`

### M4 棋谱 + 开局库（2~3 周）—— ✅ 代码完成（真机回归待装机验证）
- [x] SAF 导入/导出 xqf/pgn/cbr/txq；FileProvider 分享
- [x] 棋谱浏览页：着法列表、前进/后退、局面跳转、复盘模式
- [x] 开局库：xqb/pf/bh 文件导入（SAF→filesDir）、云开局库（HttpUtils 直连）、
      本地库（框架 SQLite 实现 `SqliteAccess` + LocalBook 管理 UI）
- [x] 对弈/分析自动挂库（OpenBookManager 复用）
- 验收结果：core 测试与纯净性检查全绿；安卓侧新增 ManualNavigator/FEN 一致性/
  格式路由/库文件命名单测全绿；`assembleDebug` 出包；实现说明见 `docs/android-m4.md`
- 验收标准：桌面版产生的棋谱/库文件在手机端打开结果一致（抽样 FEN 对照——
  PGN 解析→导航重放→FEN 与独立坐标推演一致性已入单测，真机抽样待验）

### M5 打磨与发布（1~2 周）
- [ ] 设置中心：明暗主题（沿用现有 CSS 色板）、棋盘配色、时间设置
- [ ] 深色模式跟随系统；横屏/平板双栏适配
- [ ] 崩溃日志落盘 + 分享；README/MANUAL 增补安卓章节
- [ ] GPLv3 合规：Pikafish 许可声明、源码获取指引；签发 release APK 到 GitHub Releases
- 验收标准：内测包发 Release，用户群（Q群 1094058444）收集一轮反馈

### M6 可选扩展（v2 立项，不占 v1 排期）
- 远程引擎桥（约 1 周，推荐先做）
- 完整连线（MediaProjection + AccessibilityService + onnxruntime-android，3~4 周起，商店审核风险）

---

## 5. 关键技术方案

### 5.1 引擎上机（最大风险点）
- **限制**：targetSdk ≥ 29 后，应用无法 exec 数据目录中的文件（W^X）。
- **方案**：把引擎二进制伪装成 SO 库（`libpikafish.so`）放进 `jniLibs`，系统安装时
  解压到只读的 `nativeLibraryDir`（该目录允许执行）；配合 `useLegacyPackaging=true`
  确保 SO 真正落盘而非直接从 APK 映射。
- **影响**：只能内置预编译引擎，"用户导入引擎"不可行 → v1 提供内置 Pikafish +
  参数调优即可；进阶需求走远程引擎桥。
- **回退方案**：若 exec 方案在个别 ROM 异常，改为 JNI 内嵌引擎（in-process，
  fork 管道对接 stdin/stdout），改动集中在 `AndroidProcessStarter` 一处。

### 5.2 线程模型
- core 全部使用普通线程池；引擎读线程每进程一条专用平台线程；
- 安卓侧 UI 状态经 ViewModel StateFlow 暴露，回调切主线程；
- 桌面侧行为不变（协程仅存在于 android-app 模块）。

### 5.3 存储抽象
```java
// core 中定义
public interface SqliteAccess extends AutoCloseable {          // 只读查询足够
    List<Map<String,Object>> query(String sql, Object... args);
}
public interface ConfigStore {
    Map<String,Object> load();
    void save(Map<String,Object> data);
}
public interface ProcessStarter {
    Handle start(String exePath, String workDir) throws IOException;
}
```
desktop / android 各给一套实现；core 业务代码只面向接口。

### 5.4 棋盘渲染与交互
- 几何计算（格距、像素↔棋位换算、着法箭头端点）放 core `BoardGeometry`，
  桌面/安卓共用同一套数学，避免两端显示不一致；
- 安卓侧 Compose Canvas 绘制底图 + 棋子 + 选中/提示层；素材直接复用
  `src/main/resources/ui/` 与 `font/chessman.ttf`。

---

## 6. 测试与质量保障

| 层 | 手段 |
|---|---|
| core 单测（桌面 JVM 跑，秒级） | 棋谱 round-trip、UCI/UCCI 解析 golden、开局库 key、FEN 工具 |
| 桌面回归 | `mvn verify` + 关键路径手工清单（对弈/分析/棋谱/库各 1 条） |
| 安卓单测 | Robolectric：ViewModel 状态流转、SAF 路径处理 |
| 安卓仪器测试 | 模拟器（x86_64 + 内置 x86_64 引擎）跑对弈闭环冒烟 |
| 真机矩阵 | 主流 arm64 高/低端机各一，覆盖：对弈整局、后台切换、低内存、发热降频 |
| 一致性抽查 | 同一棋谱/同一 FEN，桌面与安卓输出对照表 |

## 6b. CI/CD
- PR：core 单测 + 桌面回归 + `assembleDebug` + lint；
- main：加 `assembleRelease`（签名走 secrets），产物挂 GitHub Release（draft）；
- 版本号：android `versionName` 与 `App.VERSION` 对齐。

---

## 7. 已确认决策（2026-08 评审）

| # | 问题 | 决策 |
|---|---|---|
| 1 | v1 是否包含"远程引擎桥"？ | **v1 不做**，与完整连线一起放入 v2 |
| 2 | 内置引擎 | **仅 Pikafish**（UCI），后续按需求再议 |
| 3 | 仓库结构 | **并列新目录**：新增 `core/` + `android-app/`，现有 Maven 工程原地不动 |
| 4 | 发布渠道 | **GitHub Release APK 先行**，商店上架后议 |
| 5 | minSdk | 按建议执行：24（如需调整在 M0 前提出） |
| 6 | 包名/名称/图标 | 按建议执行：`com.sojourners.tchess` / TCHESS（图标可沿用现有 icon.png） |

## 8. 风险登记册

| 风险 | 等级 | 缓解 |
|---|---|---|
| exec 限制导致引擎跑不起来 | 高 | §5.1 主方案 + JNI 回退；M2 第一周即验证 |
| 虚拟线程替换引入桌面回归 | 中 | M1 全量手工回归 + 单测先行 |
| sqlite-jdbc → 框架 SQLite 行为差异 | 低 | 查询极简单；一致性抽查覆盖 |
| GBK 棋谱编码在安卓解码差异 | 低 | 显式 charset，round-trip 单测 |
| 手机算力/发热 | 中 | 默认保守线程/Hash；档位设置；M3 验收含发热项 |
| GPLv3 合规（内置引擎、分发渠道） | 中 | M5 法务检查项；APK 直发最稳妥 |
| Pikafish 官方未发安卓预编译包 | 中 | 自建 NDK 编译脚本入 CI（社区已有成功先例） |

---

*生成于代码库分析：2026-08 当前快照（TCHESS 1.9 / BUILT_ON 20260801）*
