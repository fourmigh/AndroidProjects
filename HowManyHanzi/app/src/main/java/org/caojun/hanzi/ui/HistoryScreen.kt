package org.caojun.hanzi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.caojun.hanzi.data.RecentRecord
import org.caojun.hanzi.data.UserStore
import org.caojun.hanzi.engine.Levels
import org.caojun.hanzi.ui.theme.Accent
import org.caojun.hanzi.ui.theme.Border
import org.caojun.hanzi.ui.theme.Ink
import org.caojun.hanzi.ui.theme.Muted
import org.caojun.hanzi.ui.theme.Paper
import org.caojun.hanzi.ui.theme.Soft
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun HistoryScreen(vm: AppViewModel) {
    var tick by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<Int?>(null) }
    var showClear by remember { mutableStateOf(false) }

    val history = remember(tick) { vm.store.getHistory() }
    val recent = remember(tick) { vm.store.getRecentChars() }
    val days = remember(tick) {
        val map = LinkedHashMap<String, MutableList<RecentRecord>>()
        recent.forEach { r ->
            val key = dayKey(r.dateText)
            map.getOrPut(key) { ArrayList() }.add(r)
        }
        map.map { (key, list) -> key to list }
    }

    PageScaffold(
        title = "历史记录",
        subtitle = "按日期看测字流水与自测成绩",
        onBack = { vm.back() },
        footer = { GhostButton("回首页", onClick = { vm.home() }) },
    ) { modifier ->
        ScrollColumn(modifier) {
            if (days.isEmpty() && history.isEmpty()) {
                EmptyState("还没有测字记录", "测一个字或做一次整体自测，就会出现在这里。")
            }

            days.forEach { (key, list) ->
                AppCard(modifier = Modifier.padding(bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(dayLabel(key), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Spacer(Modifier.weight(1f))
                        Text("${list.size} 个字", fontSize = 12.sp, color = Muted)
                    }
                    Spacer(Modifier.height(10.dp))
                    list.chunked(8).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            row.forEach { r ->
                                HistoryCell(r, Modifier.weight(1f)) {
                                    vm.go(Screen.Quiz("single", 4, r.char, 1))
                                }
                            }
                            repeat(8 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            history.forEachIndexed { index, h ->
                AppCard(modifier = Modifier.padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LevelMark(
                                    Math.round(h.avgLevel).toInt().coerceIn(0, 6),
                                    size = 24,
                                    fontSize = 12,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "等 ${String.format(Locale.ROOT, "%.1f", h.avgLevel)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Ink,
                                )
                            }
                            Text(
                                "${h.scopeName} · ${h.count} 字",
                                fontSize = 12.sp,
                                color = Muted,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${h.mastery}%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Accent)
                            Text("掌握度", fontSize = 11.sp, color = Muted)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "×",
                            fontSize = 20.sp,
                            color = Muted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { pendingDelete = index }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("估算认识 ${h.estimated} 字", fontSize = 11.sp, color = Muted)
                        Text(h.dateText, fontSize = 11.sp, color = Muted)
                    }
                }
            }

            if (days.isNotEmpty() || history.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                GhostButton("清空全部", onClick = { showClear = true })
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    pendingDelete?.let { index ->
        ConfirmDialog(
            title = "删除记录",
            message = "确定删除这条自测记录吗？（不影响已测字库）",
            onConfirm = {
                vm.store.removeHistoryAt(index)
                pendingDelete = null
                tick++
            },
            onDismiss = { pendingDelete = null },
        )
    }
    if (showClear) {
        ConfirmDialog(
            title = "清空全部",
            message = "将清空自测历史、最近测过的字与全部字库记录（隐藏收藏会保留），确定吗？",
            onConfirm = {
                vm.store.clearAll()
                showClear = false
                tick++
            },
            onDismiss = { showClear = false },
        )
    }
}

@Composable
private fun HistoryCell(record: RecentRecord, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(record.char, fontSize = 20.sp, color = Ink)
        Spacer(Modifier.height(3.dp))
        LevelMark(record.level, size = 17, fontSize = 9)
    }
}

private fun dayKey(dateText: String): String =
    if (dateText.length >= 10) dateText.substring(0, 10) else ""

private fun dayLabel(key: String): String {
    if (key.isEmpty()) return "未知日期"
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    val calendar = Calendar.getInstance()
    val today = format.format(calendar.time)
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = format.format(calendar.time)
    val parts = key.split("-")
    val md = if (parts.size == 3) "${parts[1].toIntOrNull() ?: 0}月${parts[2].toIntOrNull() ?: 0}日" else key
    return when (key) {
        today -> "今天 · $md"
        yesterday -> "昨天 · $md"
        else -> md
    }
}
