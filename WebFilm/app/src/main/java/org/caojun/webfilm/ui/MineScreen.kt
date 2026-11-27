package org.caojun.webfilm.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.caojun.webfilm.R
import org.caojun.webfilm.data.SettingsStore
import org.caojun.webfilm.web.BrowserController
import org.caojun.player.DefaultPlayerSetting
import org.caojun.player.history.HistoryEntry
import org.caojun.player.history.HistoryPanel
import org.caojun.traffic.TrafficPanel
import org.caojun.widgets.segmented.SegmentedOption
import org.caojun.widgets.segmented.SegmentedSelector
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt

@Composable
fun MineScreen(
    controller: BrowserController,
    maxNavTabs: Int,
    onOpenHistory: (HistoryEntry) -> Unit,
    onLongPressHistory: (HistoryEntry) -> Unit,
    onOpenPath: (String) -> Unit,
    onOpenWeb: () -> Unit,
) {
    var section by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        SegmentedSelector(
            options = listOf(
                SegmentedOption(0, "观看历史"),
                SegmentedOption(1, "栏目"),
                SegmentedOption(2, "流量"),
                SegmentedOption(3, "设置"),
            ),
            selected = section,
            onSelected = { section = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        when (section) {
            0 -> HistoryPanel(
                entries = controller.history,
                onOpen = onOpenHistory,
                onLongPress = onLongPressHistory,
                onRemove = { controller.removeHistory(it.key) },
                onClear = { controller.clearHistory() },
            )
            1 -> NavPanel(
                controller = controller,
                maxNavTabs = maxNavTabs,
                onOpenPath = onOpenPath,
            )
            2 -> TrafficPanel()
            else -> SettingsPanel(
                controller = controller,
                onOpenWeb = onOpenWeb,
            )
        }
    }
}

@Composable
private fun NavPanel(
    controller: BrowserController,
    maxNavTabs: Int,
    onOpenPath: (String) -> Unit,
) {
    val selectedCount = controller.navSelected.size
    val alpha = controller.navAlpha
    val lazyListState = rememberLazyListState()
    var ordered by remember(controller.navItems, controller.navOrder) {
        mutableStateOf(controller.orderedNavItems())
    }
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        ordered = ordered.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("导航栏透明度", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${(alpha * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = alpha,
                onValueChange = { controller.updateNavAlpha(it) },
                onValueChangeFinished = { controller.persistNavAlpha() },
                valueRange = 0.3f..1f,
            )
            Text(
                if (selectedCount > maxNavTabs) {
                    "当前屏幕最多显示 $maxNavTabs 个，已选 $selectedCount 个，超出的不会显示。"
                } else {
                    "按屏幕宽度最多显示 $maxNavTabs 个，已选 $selectedCount 个。勾选控制显示，拖动右侧手柄调整顺序。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.weight(1f),
        ) {
            items(ordered, key = { it.path }) { item ->
                val checked = controller.navSelected.contains(item.path)
                val canCheck = checked || selectedCount < maxNavTabs

                ReorderableItem(reorderableState, key = item.path) { isDragging ->
                    ListItem(
                        leadingContent = {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    controller.toggleNav(item.path, it, maxNavTabs)
                                },
                                enabled = canCheck,
                            )
                        },
                        headlineContent = { Text(item.label) },
                        supportingContent = {
                            Text(
                                text = item.path,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .longPressDraggableHandle(
                                        onDragStopped = {
                                            controller.applyNavOrder(ordered.map { it.path })
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_drag_handle),
                                    contentDescription = "拖动排序",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        modifier = Modifier
                            .background(
                                if (isDragging) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable { onOpenPath(item.path) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    controller: BrowserController,
    onOpenWeb: () -> Unit,
) {
    var input by remember { mutableStateOf(controller.settings.baseUrl) }
    var adBlock by remember { mutableStateOf(controller.settings.adBlockEnabled) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("网站域名", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "站点换域名时在这里修改。分类路径沿用原站规则，换到不同模板的站点可能失效。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("https://…") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val normalized = SettingsStore.normalize(input)
                                if (normalized == null) {
                                    controller.toast("域名格式不正确")
                                } else {
                                    input = normalized
                                    controller.changeBaseUrl(normalized)
                                    controller.toast("已切换域名")
                                    onOpenWeb()
                                }
                            },
                        ) { Text("保存") }
                        OutlinedButton(
                            onClick = {
                                input = SettingsStore.DEFAULT_BASE_URL
                                controller.changeBaseUrl(SettingsStore.DEFAULT_BASE_URL)
                                controller.toast("已恢复默认域名")
                                onOpenWeb()
                            },
                        ) { Text("恢复默认") }
                    }
                }
            }
        }

        item {
            DefaultPlayerSetting()
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("广告拦截", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "隐藏广告位与广告样式，不影响播放器",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = adBlock,
                        onCheckedChange = { enabled ->
                            adBlock = enabled
                            controller.settings.adBlockEnabled = enabled
                            controller.reload()
                            controller.toast(if (enabled) "已开启广告拦截" else "已关闭广告拦截")
                        },
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("关于", style = MaterialTheme.typography.titleMedium)
                Text(
                    "影视浏览器 1.0",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "只做网页封装，不存储视频、不修改影片资源。视频内容由站点提供，版权归其合法持有人所有。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
