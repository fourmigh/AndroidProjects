package org.caojun.hanzi.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.caojun.hanzi.engine.Levels
import org.caojun.hanzi.ui.theme.Accent
import org.caojun.hanzi.ui.theme.ChipBg
import org.caojun.hanzi.ui.theme.Ink
import org.caojun.hanzi.ui.theme.Muted
import org.caojun.hanzi.ui.theme.Paper
import org.caojun.hanzi.ui.theme.Soft
import org.caojun.hanzi.ui.theme.levelColor

@Composable
fun ResultScreen(vm: AppViewModel) {
    val result = vm.result
    if (result == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { vm.home() }
        return
    }

    PageScaffold(
        title = if (result is ResultData.Single) "测字结果" else "自测结果",
        subtitle = if (result is ResultData.Test) {
            "${result.scopeName} · 本次抽样 ${result.summary.count} 个字"
        } else {
            null
        },
        onBack = { vm.home() },
        footer = { GhostButton("回首页", onClick = { vm.home() }) },
    ) { modifier ->
        ScrollColumn(modifier) {
            when (result) {
                is ResultData.Single -> SingleResult(vm, result)
                is ResultData.Test -> TestResult(vm, result)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SingleResult(vm: AppViewModel, result: ResultData.Single) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            result.char,
            fontSize = 92.sp,
            color = Ink,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(10.dp))
        LevelMark(result.level, size = 50, fontSize = 22)
        Spacer(Modifier.height(6.dp))
        Text(
            Levels.nameOf(result.level),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            Levels.RESULT_DESC.getOrElse(result.level) { "" },
            fontSize = 12.sp,
            color = Muted,
            textAlign = TextAlign.Center,
        )
    }

    if (result.log.isNotEmpty()) {
        AppCard {
            SectionTitle("逐级记录")
            Spacer(Modifier.height(10.dp))
            val grouped = result.log.groupBy { it.level }
            for (lv in 1..6) {
                val rows = grouped[lv] ?: continue
                val ok = rows.count { it.correct }
                val total = rows.size
                val allOk = ok == total
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LevelMark(lv, size = 22, fontSize = 11)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Levels.NAMES.getOrElse(lv) { "" },
                        fontSize = 13.sp,
                        color = Ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (total > 1) "$ok/$total" else if (allOk) "✓" else "✗",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (allOk) Color(0xFF2F6B4F) else Accent,
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    PrimaryButton("再测一次", onClick = {
        vm.go(Screen.Quiz("single", 4, result.char, 1))
    })
}

@Composable
private fun TestResult(vm: AppViewModel, result: ResultData.Test) {
    val summary = result.summary

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(summary.avgLevelText, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("平均识字等级", fontSize = 12.sp, color = Muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${summary.mastery}%", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Accent)
                Spacer(Modifier.height(4.dp))
                RankBadge(Levels.rankOf(summary.mastery))
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(ChipBg))
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("估算认识", "${summary.estimated}")
            Stat("本档总字数", "${summary.scopeTotal}")
            Stat("抽样字数", "${summary.count}")
        }
    }

    Spacer(Modifier.height(14.dp))
    AppCard {
        SectionTitle("等级分布")
        Spacer(Modifier.height(10.dp))
        val counts = summary.distribution
        val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
        for (lv in 0..6) {
            LevelBarRow(
                level = lv,
                count = counts.getOrElse(lv) { 0 },
                max = max,
                label = Levels.NAMES.getOrElse(lv) { "" },
            )
        }
    }

    Spacer(Modifier.height(18.dp))
    PrimaryButton("再测一次", onClick = {
        vm.go(Screen.Quiz("test", result.scope, size = summary.count))
    })
    Spacer(Modifier.height(10.dp))
    GhostButton("查看历史", onClick = { vm.go(Screen.History) })
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(label, fontSize = 11.sp, color = Muted)
    }
}
