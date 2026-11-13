package org.caojun.hanzi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.caojun.hanzi.engine.Levels
import org.caojun.hanzi.ui.theme.Border
import org.caojun.hanzi.ui.theme.Ink
import org.caojun.hanzi.ui.theme.Muted
import org.caojun.hanzi.ui.theme.Paper
import org.caojun.hanzi.ui.theme.Soft

@Composable
fun CharsListScreen(vm: AppViewModel, route: Screen.CharsList) {
    val index = vm.index ?: return
    var scope by remember(route.scope) { mutableStateOf(route.scope) }
    var keyword by remember { mutableStateOf("") }

    val isHidden = scope == -1
    val pool = remember(scope) {
        if (isHidden) vm.store.getRareChars().map { it.char }
        else index.getCharsByScope(scope)
    }
    val levels = remember(scope) { vm.store.getCharLevels() }

    val scopeInfo = index.listScopes()
    val chips = scopeInfo.map { it.name } + "隐藏"

    val subText = when {
        isHidden -> if (pool.isEmpty()) "还没有隐藏字。在首页输入 8105 字库外的汉字试试。" else "隐藏 · 已收集 ${pool.size} 字"
        scope == 0 -> "${scopeInfo[0].name} · 共 ${scopeInfo[0].count} 字"
        scope >= 4 -> "全部 · 共 ${index.allChars.size} 字"
        else -> "${scopeInfo[scope].name} · 共 ${scopeInfo[scope].count} 字（本档新字 ${index.getTierChars(scope).size}）"
    }

    val searchHit = remember(keyword, scope) {
        val ch = keyword.trim().firstOrNull()?.toString()
        if (ch.isNullOrEmpty() || !isHanChar(ch)) null else pool.indexOf(ch)
    }
    val showNotFound = keyword.trim().isNotEmpty() && searchHit == null

    PageScaffold(
        title = "字库",
        subtitle = subText,
        onBack = { vm.back() },
        headerContent = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips.forEachIndexed { i, name ->
                val value = if (i == chips.lastIndex) -1 else i
                SelectableChip(
                    text = name,
                    selected = scope == value,
                    onClick = {
                        if (scope != value) {
                            scope = value
                            keyword = ""
                        }
                    },
                )
            }
        }

        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            singleLine = true,
            placeholder = { Text("输入一个字查找", fontSize = 13.sp, color = Muted) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ink,
                unfocusedBorderColor = Border,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
        )
        },
        footer = { GhostButton("回首页", onClick = { vm.home() }) },
    ) { modifier ->
        if (showNotFound) {
            Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
                EmptyState(
                    title = if (isHidden) "该字尚未收藏" else "该字不在本档位",
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        } else {
            val showAll = !isHidden && scope >= 4 && keyword.trim().isEmpty()
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (searchHit != null) {
                    item {
                        CharGridCell(
                            char = pool[searchHit],
                            level = if (isHidden) -1 else levels[pool[searchHit]],
                            onClick = {
                                vm.go(Screen.CharDetail(pool[searchHit], scope, searchHit))
                            },
                        )
                    }
                } else if (showAll) {
                    var cursor = 0
                    for (tier in index.tiers.indices.reversed()) {
                        val count = index.tiers[tier].list.size
                        val start = cursor
                        item(key = "divider-$tier", span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    index.tiers[tier].name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Ink,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("$count 字", fontSize = 11.sp, color = Muted)
                            }
                        }
                        items(count, key = { "c-${start + it}" }) { offset ->
                            val idx = start + offset
                            val ch = pool[idx]
                            CharGridCell(ch, levels[ch]) {
                                vm.go(Screen.CharDetail(ch, scope, idx))
                            }
                        }
                        cursor += count
                    }
                } else {
                    items(pool.size, key = { "c-$it" }) { idx ->
                        val ch = pool[idx]
                        CharGridCell(ch, if (isHidden) -1 else levels[ch]) {
                            vm.go(Screen.CharDetail(ch, scope, idx))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharGridCell(char: String, level: Int?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .clip(RoundedCornerShape(9.dp))
            .border(1.dp, Border.copy(alpha = 0.7f), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(char, fontSize = 19.sp, color = Ink)
        if (level != null && level >= 0) {
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(org.caojun.hanzi.ui.theme.levelColor(level)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    Levels.markOf(level),
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
