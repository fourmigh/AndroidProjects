# PlayerKit

独立的 Compose 播放器工程，一个模块 `:player`，同时包含播放能力与观看历史。

## 模块

| 模块 | 说明 |
|---|---|
| `player/`（`:player`） | Media3 播放页、默认播放器设置、播放方式菜单、观看历史 |

依赖同级的 `../ComposeWidgets/`：

- `:widgets-theme`：控件配色；
- `:widgets-segmented`：默认播放器设置用的 `SegmentedSelector`。

## 包结构

```
org.caojun.player
├── Media3PlayerActivity.kt     # ExoPlayer 播放页（进度、倍速、全屏）
├── PlayerLauncher.kt           # 打开 Media3 / 外部播放器
├── PlayerPreferences.kt        # 默认播放器（player_settings / default_player）
├── DefaultPlayerSetting.kt     # 默认播放器设置 UI
├── PlaybackMethodDialog.kt     # 播放方式菜单
├── PlayerProgressBus.kt        # 播放进度回传宿主
└── history/
    ├── HistoryEntry.kt         # 历史条目 + 单集进度
    ├── HistoryStore.kt         # SharedPreferences 读写
    └── HistoryPanel.kt         # 历史列表 UI + 进度条
```

## 观看历史

`HistoryPanel` 只负责显示，数据与动作由宿主提供：

```kotlin
HistoryPanel(
    entries = controller.history,
    onOpen = { entry -> /* 按 entry.lastPlayer 续播 */ },
    onLongPress = { entry -> /* 弹播放方式菜单 */ },
    onRemove = { entry -> store.remove(entry.key) },
    onClear = { store.clear() },
)
```

进度通过 `PlayerProgressBus` 从播放器回传；`PlayerLauncher` 负责按
`lastPlayer` 打开 Web / Media3 / 外部播放器。

## 构建

```powershell
cd PlayerKit
.\gradlew.bat :player:assembleDebug
```

WebFilm 通过 `projectDir` 引用：

```kotlin
include(":player")
project(":player").projectDir = file("../PlayerKit/player")
```
