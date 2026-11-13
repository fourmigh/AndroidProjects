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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.caojun.hanzi.engine.Levels
import org.caojun.hanzi.engine.Progress
import org.caojun.hanzi.ui.theme.Accent
import org.caojun.hanzi.ui.theme.Border
import org.caojun.hanzi.ui.theme.ChipBg
import org.caojun.hanzi.ui.theme.Ink
import org.caojun.hanzi.ui.theme.Muted
import org.caojun.hanzi.ui.theme.Paper
import org.caojun.hanzi.ui.theme.Soft

@Composable
fun CharsScreen(vm: AppViewModel) {
    val index = vm.index ?: return
    var tick by remember { mutableIntStateOf(0) }
    var manage by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var showClear by remember { mutableStateOf(false) }

    val charLevels = remember(tick) { vm.store.getCharLevels() }
    val rareCount = remember(tick) { vm.store.getRareChars().size }

    val list = remember(tick) {
        charLevels.entries
            .filter { index.getTierOf(it.key) >= 0 }
            .map { it.key to it.value }
            .sortedBy { it.first }
    }
    val validLevels = remember(tick) {
        list.associate { it.first to it.second }
    }
    val progress = remember(tick) {
        Progress.calcProgress(validLevels, index.allChars.size)
    }
    val counts = remember(tick) {
        IntArray(7).also { arr ->
            list.forEach { (_, lv) -> if (lv in 0..6) arr[lv]++ }
        }
    }

    PageScaffold(
        title = "我的字库",
        subtitle = "${list.size} 个字的最高等级记录",
        onBack = { vm.back() },
        headerTrailing = {
            Text(
                if (manage) "完成" else "管理",
                fontSize = 13.sp,
                color = Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { manage = !manage }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        },
        footer = { GhostButton("回首页", onClick = { vm.home() }) },
    ) { modifier ->
        ScrollColumn(modifier) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("当前功名", fontSize = 12.sp, color = Muted)
                        Spacer(Modifier.height(5.dp))
                        RankBadge(progress.rank)
                    }
                    StatBox("覆盖", "${progress.coverage}%")
                    StatBox("掌握", "${progress.mastery}%")
                }
            }

            Spacer(Modifier.height(12.dp))
            AppCard {
                SectionTitle("等级分布")
                Spacer(Modifier.height(10.dp))
                val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
                for (lv in 6 downTo 0) {
                    LevelBarRow(lv, counts[lv], max, Levels.NAMES[lv])
                }
            }

            Spacer(Modifier.height(12.dp))
            AppCard(modifier = Modifier.clickable { vm.go(Screen.CharsList(-1)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("隐藏", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(
                            if (rareCount > 0) "字库外的收藏 · $rareCount 字" else "字库外的收藏",
                            fontSize = 12.sp,
                            color = Muted,
                        )
                    }
                    Text("›", fontSize = 20.sp, color = Muted)
                }
            }

            if (list.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                EmptyState("还没有测过字", "回到首页测一个字，或做一次整体自测。")
            } else {
                for (lv in 6 downTo 0) {
                    val group = list.filter { it.second == lv }
                    if (group.isEmpty()) continue
                    Spacer(Modifier.height(12.dp))
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LevelMark(lv, size = 24, fontSize = 12)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                Levels.NAMES[lv],
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Ink,
                            )
                            Spacer(Modifier.weight(1f))
                            Text("${group.size} 字", fontSize = 12.sp, color = Muted)
                        }
                        Spacer(Modifier.height(10.dp))
                        group.chunked(8).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                row.forEach { (ch, _) ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(1.dp, Border, RoundedCornerShape(10.dp))
                                            .clickable {
                                                if (manage) pendingDelete = ch
                                                else vm.go(Screen.Quiz("single", 4, ch, 1))
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            ch,
                                            fontSize = 20.sp,
                                            color = if (manage) Accent else Ink,
                                        )
                                    }
                                }
                                repeat(8 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                GhostButton("清空字库记录", onClick = { showClear = true })
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    pendingDelete?.let { ch ->
        ConfirmDialog(
            title = "删除记录",
            message = "确定删除「$ch」的记录吗？",
            onConfirm = {
                vm.store.removeChar(ch)
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
                manage = false
                tick++
            },
            onDismiss = { showClear = false },
        )
    }
}

@Composable
private fun StatBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 20.dp)) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(label, fontSize = 11.sp, color = Muted)
    }
}
