package org.caojun.cupfox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.caojun.cupfox.R
import org.caojun.cupfox.data.SettingsStore
import org.caojun.cupfox.model.HistoryEntry
import org.caojun.cupfox.web.BrowserController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MineScreen(
    controller: BrowserController,
    maxNavTabs: Int,
    onOpenHistory: (HistoryEntry) -> Unit,
    onOpenPath: (String) -> Unit,
    onOpenWeb: () -> Unit,
) {
    var section by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = section == 0,
                onClick = { section = 0 },
                label = { Text("观看历史") },
            )
            FilterChip(
                selected = section == 1,
                onClick = { section = 1 },
                label = { Text("栏目") },
            )
            FilterChip(
                selected = section == 2,
                onClick = { section = 2 },
                label = { Text("设置") },
            )
        }
        when (section) {
            0 -> HistoryPanel(
                controller = controller,
                onOpen = onOpenHistory,
            )
            1 -> NavPanel(
                controller = controller,
                maxNavTabs = maxNavTabs,
                onOpenPath = onOpenPath,
            )
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
    val ordered = controller.orderedNavItems()
    val selectedCount = controller.navSelected.size
    val alpha = controller.navAlpha

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
                    "按屏幕宽度最多显示 $maxNavTabs 个，已选 $selectedCount 个。勾选控制显示，箭头调整顺序。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(ordered, key = { _, item -> item.path }) { index, item ->
                val checked = controller.navSelected.contains(item.path)
                val canCheck = checked || selectedCount < maxNavTabs

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
                        Row {
                            IconButton(
                                onClick = { controller.moveNav(item.path, -1) },
                                enabled = index > 0,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_up),
                                    contentDescription = "上移",
                                )
                            }
                            IconButton(
                                onClick = { controller.moveNav(item.path, 1) },
                                enabled = index < ordered.lastIndex,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_down),
                                    contentDescription = "下移",
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable { onOpenPath(item.path) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

@Composable
private fun HistoryPanel(
    controller: BrowserController,
    onOpen: (HistoryEntry) -> Unit,
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

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
