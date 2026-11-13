package org.caojun.hanzi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
fun HomeScreen(vm: AppViewModel) {
    val index = vm.index ?: return
    val scopes = remember { index.listScopes() }
    var scopeIndex by remember { mutableStateOf(vm.store.scope.coerceIn(0, 4)) }
    var sampleSize by remember { mutableStateOf(vm.store.sampleSize) }
    var input by remember { mutableStateOf("") }
    var polyOnly by remember { mutableStateOf(false) }
    var showRareDialog by remember { mutableStateOf(false) }
    var rareChar by remember { mutableStateOf("") }
    var inputFocused by remember { mutableStateOf(false) }
    val inputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val progress = remember(vm.screen) {
        Progress.calcProgress(vm.store.getCharLevels(), index.allChars.size)
    }
    val last = remember(vm.screen) { vm.store.getHistory().firstOrNull() }

    fun randomChar() {
        val pool = index.getCharsByScope(scopeIndex)
        val list = if (polyOnly) index.getPolyChars(pool) else pool
        if (list.isEmpty()) {
            vm.toast("该档位没有多音字")
            return
        }
        input = list.random()
        focusManager.clearFocus()
    }

    fun startSingle() {
        val ch = input.trim()
        if (!isHanChar(ch)) {
            vm.toast("请输入一个汉字")
            return
        }
        if (index.getTierOf(ch) < 0) {
            if (!vm.store.addRareChar(ch)) {
                vm.toast("已收藏过该字")
                return
            }
            rareChar = ch
            showRareDialog = true
            return
        }
        vm.go(Screen.Quiz(mode = "single", scope = scopeIndex, char = ch, size = 1))
    }

    PageScaffold(
        title = "我到底认识几个汉字",
        subtitle = "从识形到通源，六级汉字能力自测 · 共 ${index.allChars.size} 字",
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                FooterLink("等级说明") { vm.go(Screen.Intro) }
                FooterLink("历史记录") { vm.go(Screen.History) }
                FooterLink("我的字库") { vm.go(Screen.Chars) }
            }
        },
    ) { modifier ->
        ScrollColumn(modifier) {
            AppCard(modifier = Modifier.clickable { vm.go(Screen.Intro) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("当前功名 ›", fontSize = 12.sp, color = Muted)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RankBadge(progress.rank)
                            Spacer(Modifier.size(8.dp))
                            Text("掌握度 ${progress.mastery}%", fontSize = 12.sp, color = Soft)
                        }
                    }
                    Text(
                        "${progress.count}",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                    )
                    Text(" 字已测", fontSize = 12.sp, color = Muted)
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(ChipBg),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((progress.mastery.coerceIn(0, 100)) / 100f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Accent),
                    )
                }
            }

            AppCard {
                SectionTitle("单字测级")
                Text(
                    "输入或随机选一个字，逐级挑战它的形、音、义、写、用、源。",
                    fontSize = 12.sp,
                    color = Muted,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(
                                1.dp,
                                if (inputFocused) Ink else Border,
                                RoundedCornerShape(12.dp),
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { inputFocus.requestFocus() },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicTextField(
                            value = input,
                            onValueChange = { raw ->
                                val cps = raw.codePoints().toArray()
                                val last = cps.lastOrNull()?.let { String(Character.toChars(it)) }
                                input = if (last != null && isHanChar(last)) last else ""
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(inputFocus)
                                .onFocusChanged { inputFocused = it.isFocused },
                            textStyle = TextStyle(
                                fontSize = 30.sp,
                                textAlign = TextAlign.Center,
                                color = Ink,
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(Accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                            decorationBox = { inner ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (input.isEmpty() && !inputFocused) {
                                        Text("字", fontSize = 20.sp, color = Muted)
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    GhostButton(
                        text = "随机",
                        modifier = Modifier.weight(1f),
                        onClick = { randomChar() },
                    )
                    Spacer(Modifier.width(10.dp))
                    SelectableChip(
                        text = "只选多音字",
                        selected = polyOnly,
                        onClick = { polyOnly = !polyOnly },
                    )
                }
                Spacer(Modifier.height(14.dp))
                PrimaryButton("开始测这个字", onClick = { startSingle() })
            }

            AppCard {
                SectionTitle("整体自测")
                Text(
                    "按档位随机抽字，估算你的整体识字水平。",
                    fontSize = 12.sp,
                    color = Muted,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text("字库范围", fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    scopes.forEach { s ->
                        SelectableChip(
                            text = s.name,
                            selected = scopeIndex == s.index,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scopeIndex = s.index
                                vm.store.scope = s.index
                            },
                        )
                    }
                }
                Text(
                    "档位字数为累计值，选择后只包含该档新增的字。",
                    fontSize = 11.sp,
                    color = Muted,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(14.dp))
                Text("抽样字数", fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 20, 30).forEach { size ->
                        SelectableChip(
                            text = "$size 字",
                            selected = sampleSize == size,
                            onClick = {
                                sampleSize = size
                                vm.store.sampleSize = size
                            },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("开始自测", onClick = {
                    vm.go(Screen.Quiz(mode = "test", scope = scopeIndex, size = sampleSize))
                })
            }

            if (last != null) {
                AppCard {
                    SectionTitle("上次成绩")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LevelMark(
                            level = last.avgLevel.toInt().coerceIn(0, 6),
                            size = 38,
                            fontSize = 16,
                        )
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${last.scopeName} · ${last.count} 字",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Ink,
                            )
                            Text(last.dateText, fontSize = 11.sp, color = Muted)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Levels.avgText(last.avgLevel), fontSize = 12.sp, color = Soft)
                            Text("掌握度 ${last.mastery}%", fontSize = 11.sp, color = Muted)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showRareDialog) {
        ConfirmDialog(
            title = "发现隐藏字",
            message = "「$rareChar」不在 8105 字库中，已收入隐藏。",
            confirmText = "去看看",
            cancelText = "知道了",
            onConfirm = {
                showRareDialog = false
                vm.go(Screen.CharsList(scope = -1))
            },
            onDismiss = { showRareDialog = false },
        )
    }
}

@Composable
private fun FooterLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 12.sp,
        color = Soft,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

fun isHanChar(text: String): Boolean {
    if (text.isEmpty()) return false
    if (text.codePointCount(0, text.length) != 1) return false
    val cp = text.codePointAt(0)
    return (cp in 0x3400..0x4DBF) ||
        (cp in 0x4E00..0x9FFF) ||
        (cp in 0xF900..0xFAFF) ||
        (cp in 0x20000..0x3134F) ||
        (cp in 0x2F800..0x2FA1F)
}
