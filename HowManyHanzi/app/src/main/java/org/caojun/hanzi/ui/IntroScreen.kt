package org.caojun.hanzi.ui

import androidx.compose.foundation.background
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

@Composable
fun IntroScreen(vm: AppViewModel) {
    val index = vm.index ?: return
    val scopes = index.listScopes()

    PageScaffold(
        title = "等级说明",
        subtitle = "从识形到通源，六级汉字能力自测 · 共 ${index.allChars.size} 字",
        onBack = { vm.back() },
        footer = { GhostButton("回首页", onClick = { vm.home() }) },
    ) { modifier ->
        ScrollColumn(modifier) {
            AppCard(modifier = Modifier.padding(bottom = 12.dp)) {
                SectionTitle("六级能力")
                Spacer(Modifier.height(10.dp))
                for (lv in 0..6) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        LevelMark(lv, size = 24, fontSize = 12)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                Levels.NAMES[lv],
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Ink,
                            )
                            Text(
                                Levels.INTRO_DESC[lv],
                                fontSize = 11.sp,
                                color = Muted,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "甲/乙/丙/丁/戊/己 为传统分级用字，甲最高。",
                    fontSize = 11.sp,
                    color = Muted,
                )
            }

            AppCard(modifier = Modifier.padding(bottom = 12.dp)) {
                SectionTitle("科举功名")
                Text(
                    "字库页的「掌握度」以科举功名表示：",
                    fontSize = 11.sp,
                    color = Muted,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                Levels.RANKS.forEach { rank ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(54.dp)
                                .clip(RoundedCornerShape(50))
                                .background(ChipBg)
                                .padding(vertical = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(rank.name, fontSize = 11.sp, color = Ink, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("≥ ${rank.min}%", fontSize = 11.sp, color = Muted, modifier = Modifier.width(48.dp))
                        Text(rank.desc, fontSize = 11.sp, color = Soft)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "测字不足 ${Levels.MIN_CHARS} 个时，不论成绩一律为「${Levels.RANK_ZERO}」。",
                    fontSize = 11.sp,
                    color = Muted,
                    lineHeight = 17.sp,
                )
                Text(
                    "掌握度 = 已测字平均等级 × 字库覆盖率的平方根。",
                    fontSize = 11.sp,
                    color = Muted,
                    lineHeight = 17.sp,
                )
                Text(
                    "取自科举晋升序列：童生 → 秀才 → 举人 → 贡士 → 进士 → 状元（另加「蒙童」启蒙档）。",
                    fontSize = 11.sp,
                    color = Muted,
                    lineHeight = 17.sp,
                )
            }

            AppCard(modifier = Modifier.padding(bottom = 12.dp)) {
                SectionTitle("字库档位")
                Spacer(Modifier.height(8.dp))
                scopes.forEach { s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { vm.go(Screen.CharsList(s.index)) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.name, fontSize = 12.sp, color = Ink, modifier = Modifier.width(48.dp))
                        Text("${s.count} 字", fontSize = 12.sp, color = Soft, modifier = Modifier.weight(1f))
                        Text("›", fontSize = 16.sp, color = Muted)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "全部字库共 ${index.allChars.size} 字，按启蒙、小学、初中、高中逐级累进。",
                    fontSize = 11.sp,
                    color = Muted,
                    lineHeight = 17.sp,
                )
            }

            AppCard(modifier = Modifier.padding(bottom = 12.dp)) {
                SectionTitle("计分方式")
                Spacer(Modifier.height(6.dp))
                listOf(
                    "单字测级从甲开始逐级出题，答错即停在上一级。",
                    "整体自测按档位抽字，每个字独立测级。",
                    "平均等级越高、掌握度越高，说明识字水平越好。",
                    "某个等级数据缺失时会自动跳过，不记为答错。",
                ).forEach { line ->
                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                        Text("·", fontSize = 12.sp, color = Muted, modifier = Modifier.width(10.dp))
                        Text(line, fontSize = 11.sp, color = Soft, lineHeight = 18.sp)
                    }
                }
            }

            AppCard(modifier = Modifier.padding(bottom = 20.dp)) {
                SectionTitle("数据与许可")
                Spacer(Modifier.height(6.dp))
                Text(
                    "本应用完全离线运行，不申请网络权限，也不收集任何数据。",
                    fontSize = 11.sp,
                    color = Soft,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "笔顺字模：hanzi-writer-data（数据源自 Make Me a Hanzi），采用 Arphic Public License。",
                    fontSize = 11.sp,
                    color = Soft,
                    lineHeight = 18.sp,
                )
                Text(
                    "字义、读音：汉典（zdic.net）。组词与笔顺名称：cnchar（MIT）。",
                    fontSize = 11.sp,
                    color = Soft,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Arphic Public License 允许自由使用、修改与再分发，但须保留版权与许可声明。原始数据版权归 Arphic Technology 及 Make Me a Hanzi 贡献者所有。",
                    fontSize = 10.sp,
                    color = Muted,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("版本 1.0", fontSize = 10.sp, color = Muted)
                    Text("·", fontSize = 10.sp, color = Muted)
                    Text("8105 字字库 · 6866 字笔顺", fontSize = 10.sp, color = Muted)
                }
            }
        }
    }
}
