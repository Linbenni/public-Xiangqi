# TCHESS
TCHESS 是一款支持 uci 和 ucci 协议引擎的跨平台象棋界面程序，具有功能：
+ 加载引擎
+ 对弈
+ 分析
+ 棋谱
+ 连线
+ 开局库

使用说明请参考 [MANUAL.md](https://github.com/Linbenni/public-Xiangqi/blob/master/MANUAL.md)，访问 [Release](https://github.com/Linbenni/public-Xiangqi/releases) 下载最新版本。

# Linbenni V1.9.8 修改说明
+ 支持将 XQF、CBR、PGN、TXQ 棋谱直接拖放到整个主窗口打开，复用菜单“棋谱 -> 打开棋谱”的原有加载逻辑。
+ 增加棋谱前进、后退、开局、终局、自动播放、悔棋及引擎执红/执黑快捷键。
+ 固定时间和固定深度可以同时启用，任一限制先到即采用当前最佳着法。
+ 补全皮卡鱼 UCI 初始化、新局通知和就绪确认流程，并向引擎传递完整棋局历史。
+ 修正循环将军处理：允许前三次连续将军；准备第四次继续循环将军时，通过 `searchmoves` 排除该着法并让当前引擎变招。被将军方只有唯一合法应手时不再被错误停止。
+ 提供与原版相同结构的 Windows 便携包，解压后双击 `tchess.exe` 运行，`ui` 目录仍可替换棋盘界面。

# 运行环境和依赖
+ JDK 21  
+ JavaFx 23

# 交流反馈
Q群：1094058444

# 声明
本项目基于 [GPLv3](https://github.com/sojourners/public-Xiangqi/blob/master/LICENSE) 协议开源，你可以自由下载、使用、复制修改，但需遵守开源协议内容，禁止未经授权用于商业用途！
