# TCHESS 安卓版 — M4 棋谱 + 开局库实现说明

> 对应 `ANDROID_PLAN.md` §4 M4。本文记录模块结构、关键实现与验证方式。

## 功能清单（对照计划）

| 计划项 | 实现 |
|---|---|
| SAF 导入 xqf/pgn/cbr/txq | 棋谱页「打开」：`OpenDocument` → cacheDir 暂存 → core 各格式解析器（与桌面同一套 `ChessManualService`） |
| SAF 导出 / 分享 | 「存PGN / 存TXQ」走 `CreateDocument`；「分享」导出 PGN 到 `cacheDir/manual_share` 经 FileProvider 以 `ACTION_SEND` 发出 |
| 棋谱浏览页 | 新增底部导航「棋谱」Tab：盘面（复用对弈页 Canvas 绘制层）+ 着法列表（点击跳转，多分支标 ◆）+ 变着切换 chips + 开局/上变/后退/前进/下变/终局 + 复盘自动播放（1 秒/着，桌面同款节奏）+ 批注展示 + 复制 FEN |
| 开局库文件导入 | 设置页「导入本地库」：SAF → 复制到 `filesDir/books/`（后缀归一化 `.xqb/.obk/.pfBook`，重名追加序号）→ 加入配置 → `OpenBookManager.reloadIfChanged()` |
| 云开局库 | core `CloudOpenBook` 直连 chessdb.cn 不改（`HttpUtils`）；设置页云库开关 / 仅终局着法 / 超时档位；INTERNET 权限 M2 已预留 |
| 本地库管理 UI | 列表（↑↓ 排序 = 查询优先级、删除即删文件）+ 总开关 / 云库 / 本地优先 / 选着规则（取最高分·取最高胜率·正分数随机·完全随机，文案与桌面一致）/ 脱离步数滑杆 |
| 对弈自动挂库 | `GameViewModel.requestEngineMove` 改传 `allowBookMove=true`（桌面 engineGo 的 `!分析模式` 同语义）：库命中时 core 直接以 bestMove 回调走库着 |
| 分析挂库展示 | 分析页新增「开局库」区块：core `showBookResults` 回调 → 当前局面翻译中文着法（`XiangqiUtils.translate` 与桌面同源）→ 分数/胜率/来源/备注列表；`allowBookMove=false` 只展示不代走 |

验收标准落实：

- **桌面棋谱手机端打开一致**：单测 `ManualReplayConsistencyTest` 用桌面「另存为 PGN」的输出形态
  （Chinese 格式 + FEN 头 + 中文着法）经 core 解析 → ManualNavigator 主线物化 → GameLogic 逐着重放，
  最终 FEN 与「直接按 ICCS 坐标搬子」的独立推演逐字节比对一致；行棋方奇偶与桌面
  `getRedGo()` 公式等价。真机抽样对照待装机验证。
- **桌面开局库手机端可查**：xqb/obk/pfBook 均为 SQLite 文件，安卓侧以框架
  `SQLiteDatabase(OPEN_READONLY)` 实现 core `SqliteAccess` SPI（查询 SQL 与桌面 jdbc 版完全相同）。

## 模块结构（M4 新增/改动）

```
android-app/src/main/java/com/sojourners/tchess/
├── TchessApp.kt                      # +SqliteAccessProvider 工厂注册（启动时注入框架 SQLite 实现）
├── sqlite/
│   └── FrameworkSqliteAccess.kt      # core SqliteAccess SPI 安卓实现（只读 rawQuery → List<Map>）
├── manual/
│   ├── ManualFormats.kt              # 纯 JVM：扩展名路由 txq/pgn/xqf/cbr（与桌面 manualServices 表一致）
│   └── ManualNavigator.kt            # 纯 JVM：行导航模型（复刻桌面 ChessManualHandle 的 line/p/next 语义）
├── book/
│   └── BookImporter.kt               # 纯 JVM 命名规则（BookNames）+ SAF Uri → filesDir/books 导入器
├── config/AndroidConfigStore.kt      # +snapshotBookSettings/updateBookSettings/updateOpenBooks/currentOpenBooks
├── settings/BookSettings.kt          # 纯 JVM：开局库设置快照（总开关/云库/本地优先/规则/脱离步数/超时）
└── ui/
    ├── manual/                       # 棋谱页：ManualViewModel（装载/导航/复盘/导出分享）+ ManualScreen
    ├── settings/                     # SettingsViewModel +M4 开局库区；SettingsScreen +BookSection
    ├── game/GameViewModel.kt         # 对弈 allowBookMove=true（自动挂库走棋）
    └── analysis/AnalysisViewModel.kt # +onBookResults（库着法列表，节流外直发）；AnalysisScreen +开局库区块
```

Manifest：`FileProvider`（`${applicationId}.fileprovider`，paths=cache-path manual_share/、manual_export/）；版本号 `1.9-m4 (4)`。

## 关键设计

### 行导航模型（与桌面对齐的最小子集）
- `ManualNavigator.line` 是从根节点沿各节点选中分支（`ManualRecord.next` 下标）物化的当前线，
  `p` 为当前下标——前进/后退/开局/终局/点击跳转只移动 `p`；切换变着 = 改写当前节点 next 并重建线尾，
  与桌面 `boardMove` 双击子着法行为一致；
- 盘面不另建渲染逻辑：每次导航后从起始 FEN 重放到当前线（`GameLogic.loadPosition + applyEngineMove`），
  与对弈页共用同一套几何/棋规，FEN 输出天然同源；
- 打开后指针停在开始局面（p=0），与桌面 `openFromChessManual` 一致。

### 开局库链路（零 core 改动）
- core 的 `Engine.analysis` 在 `bookSwitch` 打开时已经异步查库并回调 `showBookResults`，
  命中且 `allowBookMove=true` 时直接回调 `bestMove` 走棋——安卓侧只需：
  配置注入（ConfigProvider）+ SQLite SPI 实现 + 对弈页把 allowBookMove 从 false 翻成 true；
- 库列表变化后调 `OpenBookManager.getInstance().reloadIfChanged()`（内部按配置快照判重，避免重复开库）。

### SAF 与私有目录
- 安卓无法长期持有 `content://` 读权限，而 OpenBookManager 只认真实路径：库文件复制进 `filesDir/books`，
  后缀归一化大小写（`.pfBook` 是区分大小写的路由后缀），重名追加 " (n)"；删除列表项同时删文件；
- 棋谱解析器吃 `File`：导入先落 cacheDir 再解析（解析完即删）；导出先写 cacheDir 再拷贝到 SAF 目标流；
- 分享固定走 PGN（文本格式通用性最好），经 FileProvider 授权临时读权限。

## 验证

```bash
./gradlew :core:test :core:verifyPureJava   # core 回归 + 纯净性（M4 未改 core）
./gradlew :android-app:testDebugUnitTest    # 全部单测（含 M4 新增 16 个用例）
./gradlew :android-app:assembleDebug        # 出 APK（66.8MB，含引擎 + NNUE）
```

M4 新增测试：
- `ManualNavigatorTest`：主线物化 / 行棋方奇偶 / 前后退与首尾 / 变着切换重建线尾 / 上变下变定位 / 空态安全；
- `ManualReplayConsistencyTest`：桌面 PGN 形态端到端（解析→重放→FEN 对照）、ICCS 格式 cnMove 回填、黑先起始局面；
- `ManualFormatsTest`：格式路由与扩展名归一化；
- `BookNamesTest`：三种库格式识别 / `.pfBook` 大小写归一化 / 非法字符清洗 / 重名序号。

## 已知边界（后续里程碑）

- 棋谱编辑（桌面变招添加/删除/拖拽排序）未做——v1 定位浏览与复盘；
- 棋谱打分（逐步引擎评分曲线）未做，依赖分析接口已具备，放 M5+ 评估；
- 桌面产生的 .txq 为 Java 序列化格式，跨端读写依赖同一 core 类版本（serialVersionUID 固定），
  异常时报「棋谱解析失败」而非崩溃；
- 云库请求在 book-lookup 线程同步执行（与桌面同款），弱网下首手出招受超时档位约束；
- FrameworkSqliteAccess 无桌面 JVM 单测（依赖 android.database），由真机抽样验证覆盖。
