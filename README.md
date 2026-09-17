# OBpartner (Android 客户端)

**OBpartner** 是一款面向 Markdown / Obsidian 用户的本地优先（Local-First）日程与任务管理 Android 应用程序。

本项目与同目录下的 **Freepace** 插件深度协同，完整复刻 Freepace 的 Frontmatter 属性解析规范、周视图与月视图核心算法，并提供移动端桌面小部件（Widget）与系统通知。

---

## 🌟 核心特性

### 1. 桌面组件 (AppWidgets)
- **日周月日历视图组件 (Calendar View Widget)**：
  - 桌面直观展示当前日期、农历与节气、法定调休班休角标、本周各天日历指示。
  - 滚动查看今日及近期日程列表。
  - **点击日程直达 Obsidian**：通过 `obsidian://open` 深度链接，在手机桌面上点击任意一条日程即可秒开 Obsidian 并直接定位到该笔记！
- **当日任务组件 (Today Tasks Widget)**：
  - 醒目区分展示「超期任务」与「今日进行中待办」。
  - **桌面直接交互**：在桌面上点击任务左侧复选框，直接将任务标记完成，毫秒级响应并自动局部回写更新对应 Markdown 文件的 Frontmatter。
  - 点击任务标题直达 Obsidian 查看详情。

### 2. 周视图 (Week View) —— 原生左右滑动手势
- **左右滑动翻周**：使用 Jetpack Compose `HorizontalPager`，手势丝滑，支持惯性回弹无缝切换上一周与下一周。
- **24小时垂直时间轴**：00:00 - 24:00 刻度，自动平滑滚动定位到默认起始时间（如 08:00）。
- **当前时间红线指示器**：每分钟实时更新当前时间水平线。
- **全天日程置顶栏**：持续时间超过 20 小时或跨天的事件独立吸顶展示。
- **防遮挡重叠分栏算法**：1:1 严格复刻 Freepace 源码中的 `calculateEventOverlaps` 算法，在同一时段有多个日程冲突时动态计算泳道与宽度比例，并列排布互不遮挡。

### 3. 月视图 (Month View) —— 农历与节假日集成
- **经典 7x6 完整月网格**：依据 `(firstDay.getDay() - weekStartsOn + 7) % 7` 精确计算补齐单元格，支持周一或周日作为起始日。
- **官方同款农历与二十四节气**：采用与 Freepace `lunar-javascript` 完全同源的 `cn.6tail:lunar` 算法，精准展示传统节日、二十四节气与农历日期。
- **法定节假日调休角标**：在日期旁显示国家法定调休「班」与法定放假「休」彩色小角标。
- **单日抽屉展开**：点击月历中任意一天，在下方展开当日详细日程卡片，一键呼起 Obsidian。

### 4. 系统级通知功能 (System Notifications)
- 基于事件开始时间（`start_time`）通过系统底层 `AlarmManager.setExactAndAllowWhileIdle()` 注册精准闹钟。
- 日程开始时弹出高优先级通知，并在通知栏提供「打开 Obsidian」快捷按钮。

---

## 🛠️ 技术架构

- **开发语言**：Kotlin 2.0+ (JVM 17)
- **UI 框架**：Jetpack Compose + Material 3
- **桌面微件**：Jetpack Glance (Compose for AppWidgets)
- **农历算法**：`cn.6tail:lunar` (与 Freepace 插件算法 100% 同源)
- **元数据解析**：SnakeYAML + 流式轻量只读头部提取器（避免加载大文件正文）
- **后台与定时**：Android `AlarmManager` + `BroadcastReceiver`

---

## 🚀 如何在 Android Studio 中打开与运行

1. 打开 **Android Studio** (推荐 Hedgehog / Iguana / Jellyfish 或更新版本)。
2. 选择 **Open**，定位并打开 `/Users/stuart/Qsync/project/obpartner` 目录。
3. 等待 Gradle 自动完成依赖同步（Sync）。
4. 连接 Android 手机或启动模拟器，点击顶部绿色的 **Run** (运行) 按钮即可编译安装。
