package org.caojun.webfilm.ui

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.caojun.webfilm.R
import org.caojun.webfilm.model.NavItem
import org.caojun.webfilm.model.VideoLine
import org.caojun.webfilm.web.BrowserController
import org.caojun.player.PlaybackMethodDialog
import org.caojun.player.PlayerType
import org.caojun.player.history.HistoryEntry
import org.caojun.player.history.formatDuration
import org.caojun.widgets.bottomnav.BottomNavBar
import org.caojun.widgets.bottomnav.BottomNavDefaults
import org.caojun.widgets.bottomnav.BottomNavItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(controller: BrowserController, onExit: () -> Unit) {
    val configuration = LocalConfiguration.current
    // 每个栏目至少 50dp，再留给「我的」
    val maxNavTabs = ((configuration.screenWidthDp / 50).coerceIn(3, 10) - 1).coerceAtLeast(1)
    val bottomItems = remember(
        controller.navItems,
        controller.navOrder,
        controller.navSelected,
        maxNavTabs,
    ) {
        controller.bottomNavItems(maxNavTabs)
    }

    fun homeItem(): NavItem? = bottomItems.firstOrNull()

    var selectedPath by remember { mutableStateOf(homeItem()?.path ?: "/index.html") }
    var showMine by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showLines by remember { mutableStateOf(false) }
    var historyEntry by remember { mutableStateOf<HistoryEntry?>(null) }

    val navigationBarInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val contentBottomInset = BottomNavDefaults.ContentHeight + navigationBarInset

    LaunchedEffect(maxNavTabs, controller.navItems) {
        controller.limitNavSelection(maxNavTabs)
    }

    LaunchedEffect(controller.navReady, contentBottomInset) {
        controller.setContentBottomInset(
            if (controller.navReady) contentBottomInset.value else 0f
        )
    }

    // 网页导航更新后，如果当前选中项已不存在，回退到第一个勾选栏目
    LaunchedEffect(bottomItems) {
        if (bottomItems.none { it.path == selectedPath }) {
            selectedPath = homeItem()?.path ?: selectedPath
        }
    }

    val currentLabel = bottomItems.firstOrNull { it.path == selectedPath }?.label
        ?: bottomItems.firstOrNull()?.label
        ?: "首页"

    fun select(item: NavItem) {
        val target = controller.tabUrl(item.path)
        val currentUrl = controller.webView.url.orEmpty()
        showMine = false
        selectedPath = item.path
        if (!currentUrl.startsWith(target)) {
            controller.loadTab(item.path)
        } else {
            controller.webView.scrollTo(0, 0)
        }
    }

    fun resetToHome() {
        homeItem()?.let { selectedPath = it.path }
    }

    BackHandler {
        val home = homeItem()
        when {
            controller.isFullscreen -> controller.exitFullscreen()
            showSearch -> showSearch = false
            historyEntry != null -> historyEntry = null
            showLines -> showLines = false
            showMine -> showMine = false
            controller.goBack() -> Unit
            home != null && selectedPath != home.path -> select(home)
            else -> onExit()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BrowserTopBar(
                controller = controller,
                showMine = showMine,
                currentLabel = currentLabel,
                onSearch = { showSearch = true },
                onLines = {
                    if (controller.lines.isEmpty()) {
                        controller.toast("还没解析到视频地址，请稍后再点")
                    } else {
                        showLines = true
                    }
                },
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = controller.navReady,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
            ) {
                BottomNavBar(
                    items = bottomItems.map { item ->
                        BottomNavItem(
                            value = item.path,
                            label = item.label,
                            icon = painterResource(navIconRes(item.iconKey)),
                        )
                    } + BottomNavItem(
                        value = MINE_VALUE,
                        label = "我的",
                        icon = painterResource(R.drawable.ic_person),
                    ),
                    selected = if (showMine) MINE_VALUE else selectedPath,
                    onSelected = { value ->
                        if (value == MINE_VALUE) {
                            showMine = true
                        } else {
                            controller.navItems.firstOrNull { it.path == value }?.let { select(it) }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = controller.navAlpha),
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            AndroidView(
                factory = { controller.webView },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.visibility = if (showMine) View.INVISIBLE else View.VISIBLE
                },
            )
            if (showMine) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                bottom = if (controller.navReady) contentBottomInset else 0.dp,
                            ),
                    ) {
                        MineScreen(
                            controller = controller,
                            maxNavTabs = maxNavTabs,
                            onOpenHistory = { entry ->
                                showMine = false
                                resetToHome()
                                controller.openHistory(entry)
                            },
                            onLongPressHistory = { entry -> historyEntry = entry },
                            onOpenPath = { path ->
                                controller.navItems.firstOrNull { it.path == path }?.let { select(it) }
                            },
                            onOpenWeb = {
                                showMine = false
                                resetToHome()
                            },
                        )
                    }
                }
            }
        }
    }

    if (showSearch) {
        SearchDialog(
            onDismiss = { showSearch = false },
            onSearch = { keyword ->
                showSearch = false
                showMine = false
                resetToHome()
                controller.search(keyword)
            },
        )
    }

    if (showLines) {
        LinesSheet(
            controller = controller,
            onLineSelected = { line ->
                when (controller.playerPreferences.defaultPlayer) {
                    PlayerType.MEDIA3 -> controller.openWithMedia3(line)
                    PlayerType.EXTERNAL -> {
                        controller.setCurrentPlayer("external")
                        controller.openExternally(line)
                    }
                    PlayerType.WEB -> controller.setCurrentPlayer("web")
                }
                showLines = false
            },
            onDismiss = { showLines = false },
        )
    }

    historyEntry?.let { entry ->
        PlaybackMethodDialog(
            title = "继续观看",
            subtitle = "${entry.title} · ${entry.episodeName}",
            progressText = if (entry.durationMs > 0L) {
                "上次看到 ${formatDuration(entry.positionMs)} / ${formatDuration(entry.durationMs)}"
            } else {
                null
            },
            hasVideo = entry.videoUrl.isNotBlank(),
            onDismiss = { historyEntry = null },
            onWeb = {
                historyEntry = null
                showMine = false
                resetToHome()
                controller.openHistoryInWeb(entry)
            },
            onMedia3 = {
                historyEntry = null
                showMine = false
                controller.openHistoryWithMedia3(entry)
            },
            onExternal = {
                historyEntry = null
                showMine = false
                controller.openHistoryExternally(entry)
            },
            onCopy = {
                historyEntry = null
                controller.copyHistoryUrl(entry)
            },
        )
    }
}

private const val MINE_VALUE = "__mine__"



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserTopBar(
    controller: BrowserController,
    showMine: Boolean,
    currentLabel: String,
    onSearch: () -> Unit,
    onLines: () -> Unit,
) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface,
            ),
            title = {
                Text(
                    text = if (showMine) "我的" else controller.pageTitle.ifBlank { currentLabel },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            actions = {
                IconButton(onClick = onSearch) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = "搜索")
                }
                IconButton(onClick = { controller.reload() }) {
                    Icon(painterResource(R.drawable.ic_refresh), contentDescription = "刷新")
                }
                if (!showMine && controller.isPlayingPage) {
                    IconButton(onClick = onLines) {
                        Icon(painterResource(R.drawable.ic_playlist), contentDescription = "视频线路")
                    }
                }
            },
        )
        if (!showMine && controller.progress in 1..99) {
            LinearProgressIndicator(
                progress = { controller.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchDialog(
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("搜索影片") },
        text = {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                singleLine = true,
                placeholder = { Text("输入片名或演员") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(
                enabled = keyword.isNotBlank(),
                onClick = { onSearch(keyword.trim()) },
            ) {
                Text("搜索")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinesSheet(
    controller: BrowserController,
    onLineSelected: (VideoLine) -> Unit,
    onDismiss: () -> Unit,
) {
    val lines = controller.lines

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text("视频线路", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "选择一集，再选择用哪种方式播放。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp),
        ) {
            items(lines, key = { it.url }) { line ->
                ListItem(
                    headlineContent = {
                        Text(line.name + if (line.current) "  · 当前" else "")
                    },
                    supportingContent = {
                        Text(
                            line.url.substringAfter("://").substringBefore("/"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.clickable { onLineSelected(line) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}


