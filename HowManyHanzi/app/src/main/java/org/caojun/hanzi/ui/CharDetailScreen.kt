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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.caojun.hanzi.engine.Levels
import org.caojun.hanzi.ui.theme.ChipBg
import org.caojun.hanzi.ui.theme.Ink
import org.caojun.hanzi.ui.theme.Muted
import org.caojun.hanzi.ui.theme.Paper
import org.caojun.hanzi.ui.theme.Soft
import org.caojun.hanzi.ui.theme.levelColor

@Composable
fun CharDetailScreen(vm: AppViewModel, route: Screen.CharDetail) {
    val index = vm.index ?: return
    val isHidden = route.scope == -1
    val pool = remember(route.scope) {
        if (isHidden) vm.store.getRareChars().map { it.char }
        else index.getCharsByScope(route.scope)
    }
    val rareMap = remember(route.scope) {
        vm.store.getRareChars().associateBy { it.char }
    }

    var current by remember(route) { mutableStateOf(route.char) }
    var currentIndex by remember(route) { mutableIntStateOf(route.index) }

    var strokeState by remember { mutableIntStateOf(0) } // 0 loading, 1 ready, 2 missing
    var strokeData by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(current) {
        strokeState = 0
        strokeData = emptyList()
        val data = vm.strokes.load(current)
        if (data == null) {
            strokeState = 2
        } else {
            strokeData = data.strokes
            strokeState = 1
        }
    }

    val info = index.chars[current]
    val readings = info?.readings ?: emptyList()
    val senses = info?.senses ?: emptyList()
    val strokeNames = info?.strokeNames ?: emptyList()
    val words = info?.words?.take(12) ?: emptyList()
    val level = vm.store.getCharLevels()[current]
    val tierIndex = index.getTierOf(current)
    val tierName = if (tierIndex >= 0) index.tiers[tierIndex].name else ""
    val rare = rareMap[current]

    fun move(delta: Int) {
        val next = currentIndex + delta
        if (next in pool.indices) {
            current = pool[next]
            currentIndex = next
        }
    }

    PageScaffold(
        title = "字详情",
        subtitle = "第 ${currentIndex + 1} / ${pool.size} 字",
        onBack = { vm.back() },
        headerTrailing = {
            Text(
                "‹",
                fontSize = 22.sp,
                color = if (currentIndex > 0) Ink else Muted.copy(alpha = 0.35f),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(enabled = currentIndex > 0) { move(-1) }
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
            Text(
                "›",
                fontSize = 22.sp,
                color = if (currentIndex < pool.size - 1) Ink else Muted.copy(alpha = 0.35f),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(enabled = currentIndex < pool.size - 1) { move(1) }
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        },
        footer = { GhostButton("回首页", onClick = { vm.home() }) },
    ) { modifier ->
        ScrollColumn(modifier) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(current, fontSize = 84.sp, color = Ink, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (level != null) {
                        LevelMark(level, size = 26, fontSize = 13)
                        Spacer(Modifier.width(6.dp))
                        Text(Levels.NAMES.getOrElse(level) { "" }, fontSize = 13.sp, color = Ink)
                    } else if (!isHidden) {
                        Text("尚未测过", fontSize = 13.sp, color = Muted)
                    }
                    if (tierName.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Tag(tierName)
                    }
                    if (isHidden) {
                        Spacer(Modifier.width(6.dp))
                        Tag("隐藏", accent = true)
                    }
                }
                if (rare?.dateText?.isNotEmpty() == true) {
                    Spacer(Modifier.height(4.dp))
                    Text("${rare.dateText} 收录", fontSize = 11.sp, color = Muted)
                }
            }

            if (readings.isNotEmpty()) {
                AppCard(modifier = Modifier.padding(bottom = 12.dp)) {
                    SectionTitle("读音")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        readings.forEach { r -> Tag(r) }
                    }
                    if (readings.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        Text("多音字，共 ${readings.size} 个读音", fontSize = 11.sp, color = Muted)
                    }
                }
            }

            AppCard(modifier = Modifier.padding(bottom = 12.dp)) {
                SectionTitle("笔顺")
                Spacer(Modifier.height(10.dp))
                when (strokeState) {
                    0 -> Text("笔顺数据加载中…", fontSize = 12.sp, color = Muted)
                    2 -> Text("该字暂无笔顺数据", fontSize = 12.sp, color = Muted)
                    else -> {
                        StrokeGrid(strokes = strokeData, names = strokeNames)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LegendItem("已完成", Color(0xFF22333B))
                            LegendItem("当前笔", Color(0xFFC8553D))
                            LegendItem("未写", Color(0xFFE7DFD1))
                        }
                    }
                }
                if (strokeNames.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    strokeNames.chunked(4).forEachIndexed { rowIdx, row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            row.forEachIndexed { col, name ->
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "${rowIdx * 4 + col + 1}",
                                        fontSize = 9.sp,
                                        color = Muted,
                                        modifier = Modifier.width(14.dp),
                                    )
                                    Text(name, fontSize = 11.sp, color = Soft)
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            if (senses.isNotEmpty()) {
                AppCard(modifier = Modifier.padding(bottom = 12.dp)) {
                    SectionTitle("读音与字义")
                    Spacer(Modifier.height(8.dp))
                    senses.forEach { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                s.pinyin,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Ink,
                                modifier = Modifier.width(64.dp),
                            )
                            Text(s.meaning, fontSize = 12.sp, color = Soft, lineHeight = 19.sp)
                        }
                    }
                }
            }

            val explain = info?.explain.orEmpty()
            if (explain.isNotEmpty()) {
                AppCard(modifier = Modifier.padding(bottom = 12.dp)) {
                    SectionTitle("释义")
                    Spacer(Modifier.height(6.dp))
                    Text(explain, fontSize = 14.sp, color = Ink, lineHeight = 23.sp)
                }
            }

            val radical = info?.radical.orEmpty()
            val strokeCount = info?.strokeCount ?: 0
            if (radical.isNotEmpty() || strokeCount > 0) {
                AppCard(modifier = Modifier.padding(bottom = 12.dp)) {
                    SectionTitle("字理")
                    Spacer(Modifier.height(8.dp))
                    if (radical.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("部首", fontSize = 12.sp, color = Muted, modifier = Modifier.width(48.dp))
                            Text(radical, fontSize = 13.sp, color = Ink)
                        }
                    }
                    if (strokeCount > 0) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("笔画", fontSize = 12.sp, color = Muted, modifier = Modifier.width(48.dp))
                            Text("$strokeCount 画", fontSize = 13.sp, color = Ink)
                        }
                    }
                }
            }

            if (words.isNotEmpty()) {
                AppCard(modifier = Modifier.padding(bottom = 12.dp)) {
                    SectionTitle("组词")
                    Spacer(Modifier.height(8.dp))
                    words.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            row.forEach { w ->
                                Text(
                                    w,
                                    fontSize = 12.sp,
                                    color = Soft,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ChipBg)
                                        .padding(vertical = 7.dp, horizontal = 4.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Tag(text: String, accent: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (accent) Color(0xFFF2DED8) else ChipBg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            fontSize = 11.sp,
            color = if (accent) Color(0xFFA33A28) else Soft,
        )
    }
}

@Composable
private fun LegendItem(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 10.sp, color = Muted)
    }
}
