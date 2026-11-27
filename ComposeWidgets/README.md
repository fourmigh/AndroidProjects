# ComposeWidgets

工作区级的 Compose 控件工程，一个控件一个模块，配色集中维护。

## 模块

| 模块 | 说明 |
|---|---|
| `theme/`（`:widgets-theme`） | 控件公共配色：`WidgetColors`、`LocalWidgetColors`、`WidgetTheme` |
| `segmented/`（`:widgets-segmented`） | N 选一分段选择器 `SegmentedSelector` |
| `bottom-nav/`（`:widgets-bottom-nav`） | 图标 + 文字的底部导航 `BottomNavBar` |

以后新增控件时，继续加同级模块并依赖 `:widgets-theme`。

## 配色

`WidgetTheme` 默认从宿主的 `MaterialTheme` 取色：

```kotlin
MaterialTheme(colorScheme = appColors) {
    WidgetTheme {
        content()
    }
}
```

也可以显式传入自定义 `WidgetColors`。控件只读 `LocalWidgetColors`，不单独写颜色。

## SegmentedSelector

```kotlin
SegmentedSelector(
    options = listOf(
        SegmentedOption(PlayerType.WEB, "App 内"),
        SegmentedOption(PlayerType.MEDIA3, "Media3"),
        SegmentedOption(PlayerType.EXTERNAL, "外部播放器"),
    ),
    selected = selected,
    onSelected = { selected = it },
)
```

外观是胶囊容器 + 选中项主色填充，跟随 `WidgetTheme` 的配色。

## BottomNavBar

```kotlin
BottomNavBar(
    items = listOf(
        BottomNavItem(Home, "首页", painterResource(R.drawable.ic_home)),
        BottomNavItem(Mine, "我的", painterResource(R.drawable.ic_person)),
    ),
    selected = selected,
    onSelected = { selected = it },
    containerColor = surface.copy(alpha = 0.82f),
)
```

- 图标 + 文字，选中项有胶囊指示器；
- 自动处理系统导航栏 insets；
- 背景色可传半透明色；
- 默认高度 `BottomNavDefaults.ContentHeight`（64dp）。

## 构建

```powershell
cd ComposeWidgets
.\gradlew.bat :widgets-segmented:assembleDebug
```

CupFox 通过 `settings.gradle.kts` 的 `projectDir` 引用：

```kotlin
include(":widgets-theme")
project(":widgets-theme").projectDir = file("../ComposeWidgets/theme")
include(":widgets-segmented")
project(":widgets-segmented").projectDir = file("../ComposeWidgets/segmented")
```
