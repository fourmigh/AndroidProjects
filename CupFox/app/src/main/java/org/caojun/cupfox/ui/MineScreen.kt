package org.caojun.cupfox.ui

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
import org.caojun.cupfox.R
import org.caojun.cupfox.data.SettingsStore
import org.caojun.cupfox.model.HistoryEntry
import org.caojun.cupfox.web.BrowserController
import org.caojun.player.DefaultPlayerSetting
import org.caojun.traffic.TrafficPanel
import org.caojun.widgets.segmented.SegmentedOption
import org.caojun.widgets.segmented.SegmentedSelector
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
                controller = controller,
                onOpen = onOpenHistory,
                onLongPress = onLongPressHistory,
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
private fun HistoryPanel(
    controller: BrowserController,
    onOpen: (HistoryEntry) -> Unit,
    onLongPress: (HistoryEntry) -> Unit,
) {
    val history = controller.history
    var confirmClear by remember { mutableStateOf(false) }

    if (history.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(R.drawable.ic_history),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text("还没有观看记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    "在播放页看过一集后会自动记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "最近观看 ${history.size} 条",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { confirmClear = true }) { Text("清空") }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(history, key = { it.key }) { entry ->
                HistoryRow(
                    entry = entry,
                    onClick = { onOpen(entry) },
                    onLongClick = { onLongPress(entry) },
                    onDelete = { controller.removeHistory(entry.key) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空观看历史") },
            text = { Text("将删除全部历史记录，确定吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        controller.clearHistory()
                        confirmClear = false
                    },
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_history),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title.ifBlank { "未命名" },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            val detail = buildString {
                if (entry.episodeName.isNotBlank()) append(entry.episodeName)
                if (entry.durationMs > 0L) {
                    if (isNotEmpty()) append(" · ")
                    append(formatDuration(entry.positionMs))
                    append(" / ")
                    append(formatDuration(entry.durationMs))
                }
            }
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (entry.durationMs > 0L) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = {
                        (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            Spacer(Modifier.height(2.dp))
            Text(
                text = formatter.format(Date(entry.updatedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
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
                    "茶杯狐 1.0",
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
