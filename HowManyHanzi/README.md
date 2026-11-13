# HowManyHanzi · 我到底认识几个汉字

从微信小程序 `how-many-hanzi` 移植的原生 Android 版：用 6 级测验（形 / 音 / 义 / 写 / 用 / 源）
估算识字量，**完全离线**，安装后不需要任何网络权限。

## 功能

- **单字测级**：输入或随机选一个字，从「识形」逐级向上考，答错即停在上一级
- **整体自测**：按字库档位（启蒙 / 小学 / 初中 / 高中 / 全部）随机抽 10 / 20 / 30 字，估算整体识字量
- **结果统计**：平均等级、掌握度、估算认识字数、等级分布
- **历史记录**：按日期看测字流水与自测成绩，可单条删除或清空
- **我的字库**：已测字的最高等级、覆盖率、掌握度、科举功名
- **字库浏览**：8105 字按档位浏览、按字搜索、进入字详情
- **字详情**：读音、多音义项、释义、部首、笔画数、组词，以及 **4 列逐笔笔顺网格**
- **生僻字收藏**：输入 8105 字库之外的汉字会收入「隐藏」

## 技术栈

| 项 | 版本 / 说明 |
|---|---|
| 语言 | Kotlin 2.2.10 |
| UI | Jetpack Compose + Material 3，单 Activity |
| 构建 | AGP 9.4.1 / Gradle 9.8.0 |
| SDK | compileSdk 37 / minSdk 26 / targetSdk 36 |
| 存储 | SharedPreferences（设置、历史、等级、收藏） |
| 数据 | assets 内置 TSV + 单字笔顺 JSON，`org.json` 解析 |

- 无第三方网络、数据库、序列化依赖。
- 笔顺用自研 SVG path 解析器（`strokes/SvgPath.kt`）绘制到 Compose Canvas。
- **Manifest 不声明 `INTERNET` 权限**，App 物理上无法联网。

## 离线数据

```
app/src/main/assets/
├── data/
│   ├── chars.tsv      # 8105 字：释义 / 部首 / 笔画 / 读音 / 义项 / 笔顺名 / 组词
│   ├── tiers.tsv      # 5 个档位的字表
│   └── poly.txt       # 多音字集合
└── strokes/
    └── <码点十六进制>.json   # 6866 个字的笔顺轮廓 {"s":["M ...", ...]}
```

- 字模来自 `hanzi-writer-data@2.0.1`，只保留字表命中的 6866 字，去掉 medians，约 15 MB。
- 1239 个生僻字在数据源中没有字模，详情页显示「该字暂无笔顺数据」，其余功能正常。
- 笔顺名称与组词在构建期用 cnchar 预计算，App 运行时不再执行 cnchar。
- 生成的数据不打进 git，需要时可重建。

### 重建数据

```powershell
# 1. 从小程序源码生成字库 TSV（默认读取 F:\Github\WeChatMiniProgram\how-many-hanzi）
#    可用 HOW_MANY_HANZI_SRC 环境变量覆盖路径
node tools/build-data.mjs

# 2. 下载 hanzi-writer-data 并提取 6866 个笔顺字模
node tools/fetch-strokes.mjs
```

依赖 Node 18+、`curl`、`tar`（Windows 10+ 自带）。缺失字清单输出到 `tools/report-missing.txt`。

## 构建与安装

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Debug / Release 均复用工作区共享证书 `../keystore/shared.p12`，可与其它 `org.caojun.*` App 互相覆盖升级。

单元测试覆盖数据解析、6 级出题、答对升级 / 答错停止、汇总公式、掌握度公式与抽样权重：

```powershell
.\gradlew.bat testDebugUnitTest
```

## 目录结构

```
app/src/main/java/org/caojun/hanzi/
├── MainActivity.kt              # 单 Activity、加载页、页面分发
├── data/                        # 字库索引、SharedPreferences、模型
├── engine/                      # 6 级出题、干扰项、评分、进度、功名
├── strokes/                     # SVG path 解析、笔顺字模读取
└── ui/                          # 8 个页面、主题、通用组件
tools/
├── build-data.mjs               # 小程序数据 + cnchar → chars.tsv
└── fetch-strokes.mjs            # hanzi-writer-data → assets/strokes
```

## 数据来源与许可

- **笔顺字模**：[hanzi-writer-data](https://github.com/chanind/hanzi-writer-data)，数据源自
  [Make Me a Hanzi](https://github.com/skishore/makemeahanzi)，采用 **Arphic Public License**。
  该许可允许使用、修改与再分发，须保留版权与许可声明。原始字形版权归 Arphic Technology
  及 Make Me a Hanzi 贡献者所有。
- **字义、读音**：汉典（zdic.net），沿用原小程序数据。
- **组词、笔顺名称**：[cnchar](https://github.com/theajack/cnchar)（MIT）。

App 内「等级说明」页底部也附有上述声明。
