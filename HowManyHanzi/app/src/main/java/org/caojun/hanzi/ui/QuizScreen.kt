package org.caojun.hanzi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.caojun.hanzi.ui.theme.Accent
import org.caojun.hanzi.ui.theme.Border
import org.caojun.hanzi.ui.theme.Ink
import org.caojun.hanzi.ui.theme.Muted
import org.caojun.hanzi.ui.theme.Soft

@Composable
fun QuizScreen(vm: AppViewModel, route: Screen.Quiz) {
    val c = remember(route) { QuizController(vm, route) }

    if (c.blocked) {
        PageScaffold(
            title = "测字",
            subtitle = "单字测级",
            onBack = { vm.back() },
            footer = { PrimaryButton("回首页", onClick = { vm.home() }) },
        ) { modifier ->
            ScrollColumn(modifier) {
                EmptyState("该字不在 8105 字库中", "请返回首页换一个字。", Modifier.padding(top = 32.dp))
            }
        }
        return
    }

    val q = c.question
    if (q == null) {
        PageScaffold(
            title = "测字",
            subtitle = c.progressText,
            onBack = { vm.back() },
        ) { modifier ->
            Box(modifier, contentAlignment = Alignment.Center) {
                Text("正在准备题目…", color = Muted, fontSize = 14.sp)
            }
        }
        return
    }

    val footerText = when {
        c.answered -> c.nextText
        q.type == "multi" -> "提交答案"
        else -> "请选择答案"
    }
    val footerEnabled = c.answered || q.type == "multi"

    PageScaffold(
        title = "测字",
        subtitle = c.progressText,
        onBack = { vm.back() },
        headerTrailing = {
            LevelMark(c.level, size = 28, fontSize = 13)
            Spacer(Modifier.width(6.dp))
            Text(c.levelName(c.level), fontSize = 12.sp, color = Soft)
        },
        footer = {
            PrimaryButton(
                text = footerText,
                enabled = footerEnabled,
                onClick = { if (c.answered) c.next() else c.submit() },
            )
        },
    ) { modifier ->
        ScrollColumn(modifier) {
            Box(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = c.charText,
                    fontSize = 88.sp,
                    color = Ink,
                    fontWeight = FontWeight.Medium,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LevelMark(q.level, size = 24, fontSize = 12)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${q.levelName} · ${q.prompt}",
                    fontSize = 14.sp,
                    color = Soft,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(16.dp))

            val longText = q.level == 3
            if (longText) {
                q.options.forEach { opt ->
                    OptionRow(
                        text = opt,
                        state = optionState(q.options, opt, c.selected, c.answered, q.answer),
                        fontSize = 14,
                        modifier = Modifier.padding(vertical = 4.dp),
                        onClick = { c.toggleOption(opt) },
                    )
                }
            } else {
                q.options.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { opt ->
                            OptionCell(
                                text = opt,
                                state = optionState(q.options, opt, c.selected, c.answered, q.answer),
                                fontSize = if (q.level == 1) 26 else 15,
                                modifier = Modifier.weight(1f),
                                onClick = { c.toggleOption(opt) },
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (c.answered) {
                Spacer(Modifier.height(16.dp))
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            c.feedbackTitle(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (c.correct) Color(0xFF2F6B4F) else Accent,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(c.feedbackSub(), fontSize = 12.sp, color = Muted)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "正确答案：${q.answer.joinToString(" / ")}",
                        fontSize = 12.sp,
                        color = Soft,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private enum class OptionState { Idle, Selected, Right, Wrong, Dim }

private fun optionState(
    options: List<String>,
    value: String,
    selected: List<String>,
    answered: Boolean,
    answer: List<String>,
): OptionState = when {
    !answered && selected.contains(value) -> OptionState.Selected
    answered && answer.contains(value) -> OptionState.Right
    answered && selected.contains(value) -> OptionState.Wrong
    answered -> OptionState.Dim
    else -> OptionState.Idle
}

@Composable
private fun OptionCell(
    text: String,
    state: OptionState,
    fontSize: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = when (state) {
        OptionState.Selected -> Color(0xFFEFE9DE)
        OptionState.Right -> Color(0xFFE8F2EA)
        OptionState.Wrong -> Color(0xFFFBEAE6)
        else -> Color.White
    }
    val border = when (state) {
        OptionState.Selected -> Ink
        OptionState.Right -> Color(0xFF5F9B7F)
        OptionState.Wrong -> Accent
        else -> Border
    }
    val fg = when (state) {
        OptionState.Right -> Color(0xFF2F6B4F)
        OptionState.Wrong -> Color(0xFFA33A28)
        OptionState.Dim -> Muted
        else -> Ink
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = fontSize.sp,
            color = fg,
            textAlign = TextAlign.Center,
            fontWeight = if (state == OptionState.Right) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun OptionRow(
    text: String,
    state: OptionState,
    fontSize: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = when (state) {
        OptionState.Selected -> Color(0xFFEFE9DE)
        OptionState.Right -> Color(0xFFE8F2EA)
        OptionState.Wrong -> Color(0xFFFBEAE6)
        else -> Color.White
    }
    val border = when (state) {
        OptionState.Selected -> Ink
        OptionState.Right -> Color(0xFF5F9B7F)
        OptionState.Wrong -> Accent
        else -> Border
    }
    val fg = when (state) {
        OptionState.Right -> Color(0xFF2F6B4F)
        OptionState.Wrong -> Color(0xFFA33A28)
        OptionState.Dim -> Muted
        else -> Ink
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(text, fontSize = fontSize.sp, color = fg, lineHeight = 21.sp)
    }
}
