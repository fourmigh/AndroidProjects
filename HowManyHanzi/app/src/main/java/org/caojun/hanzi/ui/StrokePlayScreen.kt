package org.caojun.hanzi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.caojun.hanzi.strokes.MedianPoint
import org.caojun.hanzi.strokes.StrokeCalc
import org.caojun.hanzi.strokes.StrokeData
import org.caojun.hanzi.strokes.SvgPath
import org.caojun.hanzi.ui.theme.Accent
import org.caojun.hanzi.ui.theme.Border
import org.caojun.hanzi.ui.theme.ChipBg
import org.caojun.hanzi.ui.theme.Ink
import org.caojun.hanzi.ui.theme.Muted
import org.caojun.hanzi.ui.theme.Soft
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

private const val BOX_RAW = 1024f / 0.9f
private const val PAUSE_MS = 600L

private fun formatDuration(ms: Int): String =
    String.format(Locale.ROOT, "%.1fs", ms / 1000f)

/** 写字动画页，移植自小程序 pages/stroke-play。 */
@Composable
fun StrokePlayScreen(vm: AppViewModel, route: Screen.StrokePlay) {
    val char = route.char
    var state by remember(char) { mutableIntStateOf(0) } // 0 loading / 1 ready / 2 missing
    var data by remember(char) { mutableStateOf<StrokeData?>(null) }

    val total = data?.strokes?.size ?: 0
    var cursor by remember(char) { mutableIntStateOf(0) }
    var doneCount by remember(char) { mutableIntStateOf(0) }
    var animating by remember(char) { mutableStateOf(false) }
    var highlight by remember(char) { mutableStateOf(false) }
    var progress by remember(char) { mutableFloatStateOf(0f) }

    var duration by remember { mutableIntStateOf(vm.store.strokeDuration.coerceIn(300, 3000)) }
    var pauseOn by remember { mutableStateOf(vm.store.strokePause) }

    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }

    fun play(index: Int, auto: Boolean) {
        val size = data?.strokes?.size ?: 0
        if (size == 0) return
        val i = index.coerceIn(0, size - 1)
        job?.cancel()
        cursor = i
        doneCount = i
        highlight = false
        progress = 0f
        animating = true
        val ms = duration.coerceIn(300, 3000)
        job = scope.launch {
            val startNanos = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val t = ((now - startNanos) / 1_000_000f) / ms
                progress = t.coerceIn(0f, 1f)
                if (t >= 1f) break
            }
            animating = false
            doneCount = i + 1
            if (auto && i + 1 < size) {
                if (pauseOn) delay(PAUSE_MS)
                job = null
                play(i + 1, true)
            }
        }
    }

    fun showStatic(index: Int) {
        job?.cancel()
        job = null
        cursor = index
        doneCount = index
        animating = false
        highlight = true
        progress = 0f
    }

    LaunchedEffect(char) {
        state = 0
        data = null
        val d = vm.strokes.load(char)
        if (d == null || d.strokes.isEmpty()) {
            state = 2
        } else {
            data = d
            state = 1
        }
    }

    LaunchedEffect(data) {
        val d = data ?: return@LaunchedEffect
        play(route.start.coerceIn(0, d.strokes.size - 1), false)
    }

    val names = remember(char, vm.index) {
        vm.index?.chars?.get(char)?.strokeNames ?: emptyList()
    }
    val parsed = remember(data) { data?.strokes?.map { SvgPath.parse(it) } ?: emptyList() }
    val medians = data?.medians ?: emptyList()
    val paths = remember(parsed) { parsed.map { buildStrokePath(it) } }
    val widths = remember(parsed, medians) {
        parsed.indices.map { i ->
            StrokeCalc.estimateWidth(parsed[i], StrokeCalc.polylineLength(medians.getOrNull(i).orEmpty()))
        }
    }

    val caption = if (state == 1 && cursor in 0 until total) {
        val name = names.getOrNull(cursor).orEmpty()
        "第 ${cursor + 1} / $total 笔" + if (name.isNotEmpty()) " · $name" else ""
    } else {
        "逐笔演示与描红"
    }

    PageScaffold(
        title = "写字动画",
        subtitle = caption,
        onBack = { vm.back() },
        footer = { GhostButton("回首页", onClick = { vm.home() }) },
    ) { modifier ->
        ScrollColumn(modifier) {
            when (state) {
                0 -> EmptyState("笔顺数据加载中…", modifier = Modifier.padding(top = 24.dp))
                2 -> EmptyState(
                    "该字暂无笔顺数据",
                    "可以返回字库换一个字。",
                    Modifier.padding(top = 24.dp),
                )
                else -> {
                    Text(
                        "点击笔画跳到该笔，从该笔开始写",
                        fontSize = 11.sp,
                        color = Muted,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(StrokeCellBg)
                            .border(1.dp, StrokeCellBorder, RoundedCornerShape(16.dp))
                            .pointerInput(char, parsed) {
                                detectTapGestures { offset ->
                                    val box = min(size.width, size.height).toFloat()
                                    val gs = box * 0.9f / 1024f
                                    val boxOffset = 512f * gs
                                    val bx = size.width / 2f
                                    val by = size.height / 2f
                                    val hit = StrokeCalc.hitTest(
                                        medians,
                                        { px, py ->
                                            MedianPoint(
                                                bx - boxOffset + px * gs,
                                                by - boxOffset + (900f - py) * gs,
                                            )
                                        },
                                        offset.x,
                                        offset.y,
                                        box * 0.12f,
                                    )
                                    if (hit >= 0) play(hit, false)
                                }
                            },
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val box = size.minDimension
                            val gs = box * 0.9f / 1024f
                            val boxOffset = 512f * gs
                            val bx = size.width / 2f
                            val by = size.height / 2f
                            withTransform({
                                translate(bx - boxOffset, by - boxOffset + 900f * gs)
                                scale(gs, -gs, pivot = Offset.Zero)
                            }) {
                                for (p in paths) drawPath(p, StrokePending)
                                for (j in 0 until doneCount.coerceAtMost(paths.size)) {
                                    drawPath(paths[j], StrokeDone)
                                }
                                if (highlight && !animating && cursor in paths.indices) {
                                    drawPath(paths[cursor], StrokeCurrent)
                                }
                                if (animating && cursor in paths.indices) {
                                    val pts = medians.getOrNull(cursor).orEmpty()
                                    var w = widths.getOrElse(cursor) { 0f } * 0.85f
                                    if (w <= 0f) w = 200f
                                    w = w.coerceIn(BOX_RAW * 0.012f, BOX_RAW * 0.2f)
                                    for ((a, b) in StrokeCalc.partialPolyline(pts, progress)) {
                                        drawLine(
                                            color = StrokeCurrent,
                                            start = Offset(a.x, a.y),
                                            end = Offset(b.x, b.y),
                                            strokeWidth = w,
                                            cap = StrokeCap.Round,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GhostButton(
                            "‹ 上一笔",
                            modifier = Modifier.weight(1f),
                            enabled = cursor > 0,
                            onClick = { showStatic((cursor - 1).coerceAtLeast(0)) },
                        )
                        Text(
                            "${cursor + 1} / $total",
                            fontSize = 13.sp,
                            color = Ink,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(64.dp),
                        )
                        GhostButton(
                            "下一笔 ›",
                            modifier = Modifier.weight(1f),
                            enabled = cursor < total - 1,
                            onClick = { play(cursor + 1, false) },
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GhostButton(
                            "重写此笔",
                            modifier = Modifier.weight(1f),
                            onClick = { play(cursor, false) },
                        )
                        PrimaryButton(
                            "重播整字",
                            modifier = Modifier.weight(1f),
                            onClick = { play(0, true) },
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("书写时长", fontSize = 12.sp, color = Soft, modifier = Modifier.width(64.dp))
                        Slider(
                            value = duration.toFloat(),
                            onValueChange = { duration = it.roundToInt() },
                            onValueChangeFinished = { vm.store.strokeDuration = duration },
                            valueRange = 300f..3000f,
                            steps = 26,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Accent,
                                activeTrackColor = Accent,
                                inactiveTrackColor = ChipBg,
                                activeTickColor = Color.Transparent,
                                inactiveTickColor = Color.Transparent,
                            ),
                        )
                        Text(
                            formatDuration(duration),
                            fontSize = 12.sp,
                            color = Muted,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(44.dp),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("笔间停顿", fontSize = 12.sp, color = Soft, modifier = Modifier.weight(1f))
                        Switch(
                            checked = pauseOn,
                            onCheckedChange = {
                                pauseOn = it
                                vm.store.strokePause = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Accent,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = ChipBg,
                                uncheckedBorderColor = Border,
                            ),
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(total) { i ->
                            val selected = i == cursor
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) Ink else ChipBg)
                                    .clickable { play(i, false) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    "${i + 1} ${names.getOrElse(i) { "" }}",
                                    fontSize = 11.sp,
                                    color = if (selected) Color.White else Soft,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
