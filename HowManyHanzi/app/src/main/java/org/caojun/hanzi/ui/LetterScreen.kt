package org.caojun.hanzi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.caojun.hanzi.data.LetterLayout
import org.caojun.hanzi.strokes.MedianPoint
import org.caojun.hanzi.strokes.PathCmd
import org.caojun.hanzi.strokes.StrokeCalc
import org.caojun.hanzi.strokes.SvgPath
import org.caojun.hanzi.ui.theme.Accent
import org.caojun.hanzi.ui.theme.Border
import org.caojun.hanzi.ui.theme.ChipBg
import org.caojun.hanzi.ui.theme.Ink
import org.caojun.hanzi.ui.theme.Muted
import org.caojun.hanzi.ui.theme.Soft
import java.util.Locale
import kotlin.math.roundToInt

/** 预览示例：马致远《天净沙·秋思》全文（28 字，正好两行 × 最大 14 格）。 */
private const val PREVIEW_CHARS = "枯藤老树昏鸦小桥流水人家古道西风瘦马夕阳西下断肠人在天涯"

private val SIZE_LEVELS = listOf("小", "中", "大", "特大", "超大")
private val GAP_LEVELS = listOf("极紧", "紧", "无", "窄", "适中", "宽")

private fun sizeLevel(size: Int): Int = when {
    size <= 70 -> 0
    size <= 100 -> 1
    size <= 130 -> 2
    size <= 165 -> 3
    else -> 4
}

private fun gapLevel(gap: Int): Int = when {
    gap <= -6 -> 0
    gap < 0 -> 1
    gap == 0 -> 2
    gap <= 8 -> 3
    gap <= 16 -> 4
    else -> 5
}

private fun durationText(ms: Int): String =
    String.format(Locale.ROOT, "%.1fs", ms / 1000f)

private data class CellGlyph(val fallback: String? = null, val paths: List<Path>? = null)

private data class ActiveGlyph(
    val parsed: List<List<PathCmd>>,
    val paths: List<Path>,
    val medians: List<List<MedianPoint>>,
    val widths: List<Float>,
)

/** 写信页，移植自小程序 pages/letter。 */
@Composable
fun LetterScreen(vm: AppViewModel) {
    var text by remember { mutableStateOf("") }
    var sizeRpx by remember { mutableIntStateOf(vm.store.letterSize.coerceIn(45, 200)) }
    var gapRpx by remember { mutableIntStateOf(vm.store.letterGap.coerceIn(-12, 24)) }
    var durationMs by remember { mutableIntStateOf(vm.store.letterDuration.coerceIn(300, 3000)) }

    val cols = remember(sizeRpx, gapRpx) { LetterLayout.colsOf(sizeRpx, gapRpx) }
    val previewLines = remember(text, cols) {
        LetterLayout.parseLines(if (text.isBlank()) PREVIEW_CHARS else text, cols)
    }
    val writable = remember(text, cols) {
        if (text.isBlank()) 0
        else LetterLayout.countWritable(LetterLayout.parseLines(text, cols))
    }

    var started by remember { mutableStateOf(false) }
    var writing by remember { mutableStateOf(false) }
    var written by remember { mutableIntStateOf(0) }
    var writeTotal by remember { mutableIntStateOf(0) }
    var writeToken by remember { mutableIntStateOf(0) }

    var previewGlyphs by remember { mutableStateOf<Map<Int, CellGlyph>>(emptyMap()) }
    var doneGlyphs by remember { mutableStateOf<Map<Int, CellGlyph>>(emptyMap()) }
    var active by remember { mutableStateOf<ActiveGlyph?>(null) }
    var activeCell by remember { mutableIntStateOf(-1) }
    var activeStroke by remember { mutableIntStateOf(0) }
    var activeProgress by remember { mutableFloatStateOf(0f) }

    val scroll = rememberScrollState()
    val densityObj = LocalDensity.current
    val measurer = rememberTextMeasurer()

    val subtitle = when {
        writing -> "写入中 · $written/$writeTotal 字"
        started && written > 0 -> "写完啦 · 共 $writeTotal 字"
        text.isBlank() -> "每行 $cols 字 · 示例预览"
        else -> "每行 $cols 字 · 共 $writable 字"
    }

    PageScaffold(
        title = "写信",
        subtitle = subtitle,
        onBack = { vm.back() },
        footer = {
            PrimaryButton(
                text = when {
                    writing -> "书写中…"
                    started -> "重写"
                    else -> "开始写"
                },
                enabled = !writing,
                onClick = {
                    val total = if (text.isBlank()) 0
                    else LetterLayout.countWritable(LetterLayout.parseLines(text, cols))
                    if (total == 0) {
                        vm.toast("先写点内容吧")
                    } else {
                        writeTotal = total
                        writeToken++
                    }
                },
            )
        },
    ) { modifier ->
        BoxWithConstraints(modifier) {
            val unitDp = maxWidth / LetterLayout.DESIGN_WIDTH
            val padTopDp = unitDp * (sizeRpx * 0.8f)
            val rowHDp = unitDp * (sizeRpx * 1.2f)
            val contentHDp = rowHDp * previewLines.size + padTopDp
            val paperHDp = if (contentHDp > maxHeight) contentHDp else maxHeight
            val rowHpx = with(densityObj) { rowHDp.toPx() }
            val padTopPx = with(densityObj) { padTopDp.toPx() }

            // 编辑态：把内容静态铺在信纸上预览（输入有变化时防抖重画）。
            LaunchedEffect(text, cols, writing, started) {
                if (writing || started) return@LaunchedEffect
                delay(80)
                val lines = LetterLayout.parseLines(if (text.isBlank()) PREVIEW_CHARS else text, cols)
                val cells = ArrayList<Pair<Int, String>>()
                lines.forEachIndexed { r, line ->
                    line.forEachIndexed { c, ch ->
                        if (!LetterLayout.isBlank(ch)) cells.add((r * cols + c) to ch)
                    }
                }
                val cache = HashMap<String, CellGlyph>()
                val glyphs = HashMap<Int, CellGlyph>()
                for ((cell, ch) in cells) {
                    val glyph = cache.getOrPut(ch) {
                        val d = vm.strokes.load(ch)
                        if (d == null) CellGlyph(fallback = ch)
                        else CellGlyph(paths = d.strokes.map { buildStrokePath(SvgPath.parse(it)) })
                    }
                    glyphs[cell] = glyph
                }
                previewGlyphs = glyphs
            }

            // 书写：逐行逐字，每个字逐笔沿中心线写出。
            LaunchedEffect(writeToken) {
                if (writeToken == 0) return@LaunchedEffect
                val writeLines = LetterLayout.parseLines(text, cols)
                writing = true
                started = true
                written = 0
                doneGlyphs = emptyMap()
                previewGlyphs = emptyMap()
                active = null
                activeCell = -1
                scroll.scrollTo(0)
                for (r in writeLines.indices) {
                    if (r > 0) {
                        val target = padTopPx + r * rowHpx - rowHpx * 0.5f
                        scroll.animateScrollTo(target.roundToInt().coerceAtLeast(0))
                    }
                    val line = writeLines[r]
                    for (c in line.indices) {
                        val ch = line[c]
                        if (LetterLayout.isBlank(ch)) continue
                        val cell = r * cols + c
                        val d = vm.strokes.load(ch)
                        if (d == null) {
                            doneGlyphs = doneGlyphs + (cell to CellGlyph(fallback = ch))
                            written++
                            continue
                        }
                        val parsed = d.strokes.map { SvgPath.parse(it) }
                        val paths = parsed.map { buildStrokePath(it) }
                        val widths = parsed.indices.map { i ->
                            StrokeCalc.estimateWidth(
                                parsed[i],
                                StrokeCalc.polylineLength(d.medians.getOrNull(i).orEmpty()),
                            )
                        }
                        active = ActiveGlyph(parsed, paths, d.medians, widths)
                        activeCell = cell
                        for (si in parsed.indices) {
                            activeStroke = si
                            activeProgress = 0f
                            val start = withFrameNanos { it }
                            while (true) {
                                val now = withFrameNanos { it }
                                val t = ((now - start) / 1_000_000f) / durationMs
                                activeProgress = t.coerceIn(0f, 1f)
                                if (t >= 1f) break
                            }
                        }
                        active = null
                        activeCell = -1
                        doneGlyphs = doneGlyphs + (cell to CellGlyph(paths = paths))
                        written++
                    }
                }
                writing = false
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll),
            ) {
                if (!writing) {
                    EditorCard(
                        text = text,
                        onTextChange = {
                            text = LetterLayout.takeCodePoints(it, LetterLayout.MAX_LEN)
                            started = false
                            written = 0
                            doneGlyphs = emptyMap()
                        },
                        cols = cols,
                        sizeRpx = sizeRpx,
                        gapRpx = gapRpx,
                        durationMs = durationMs,
                        onSizeChange = { sizeRpx = it.roundToInt().coerceIn(45, 200) },
                        onSizeFinished = { vm.store.letterSize = sizeRpx },
                        onGapChange = { gapRpx = it.roundToInt().coerceIn(-12, 24) },
                        onGapFinished = { vm.store.letterGap = gapRpx },
                        onDurationChange = { durationMs = it.roundToInt().coerceIn(300, 3000) },
                        onDurationFinished = { vm.store.letterDuration = durationMs },
                    )
                    Spacer(Modifier.height(12.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(paperHDp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(LetterPaper)
                        .border(1.dp, Border, RoundedCornerShape(16.dp)),
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val unitPx = size.width / LetterLayout.DESIGN_WIDTH
                        val cellW = (sizeRpx + gapRpx) * unitPx
                        val rowH = sizeRpx * 1.2f * unitPx
                        val padTop = sizeRpx * 0.8f * unitPx
                        val boxPx = sizeRpx * unitPx / 0.88f
                        val offsetX = ((size.width - cols * cellW) / 2f).coerceAtLeast(0f)
                        val gs = boxPx * 0.9f / 1024f
                        val boxOffset = 512f * gs

                        // 信纸横线：顶部留白区不画线。
                        var y = padTop + rowH
                        while (y <= size.height + 0.5f) {
                            drawRect(LetterRule, Offset(0f, y - 1f), Size(size.width, 1f))
                            y += rowH
                        }

                        fun DrawScope.drawCell(cell: Int, glyph: CellGlyph) {
                            val row = cell / cols
                            val col = cell % cols
                            val areaX = offsetX + col * cellW
                            val areaY = padTop + row * rowH
                            val originX = areaX + (cellW - boxPx) / 2f
                            val originY = areaY + (rowH - boxPx) / 2f + boxPx * 0.07f
                            if (glyph.paths != null) {
                                withTransform({
                                    translate(
                                        originX + boxPx / 2f - boxOffset,
                                        originY + boxPx / 2f - boxOffset + 900f * gs,
                                    )
                                    scale(gs, -gs, pivot = Offset.Zero)
                                }) {
                                    for (p in glyph.paths) drawPath(p, LetterInk)
                                }
                            } else if (glyph.fallback != null) {
                                val layout = measurer.measure(
                                    AnnotatedString(glyph.fallback),
                                    style = TextStyle(
                                        color = LetterInk,
                                        fontSize = (boxPx / this.density).sp,
                                        fontFamily = FontFamily.Serif,
                                    ),
                                )
                                drawText(
                                    layout,
                                    topLeft = Offset(
                                        areaX + cellW / 2f - layout.size.width / 2f,
                                        areaY + rowH / 2f - layout.size.height / 2f,
                                    ),
                                )
                            }
                        }

                        if (writing) {
                            doneGlyphs.forEach { (cell, glyph) -> drawCell(cell, glyph) }
                            val a = active
                            if (a != null && activeCell >= 0) {
                                val row = activeCell / cols
                                val col = activeCell % cols
                                val areaX = offsetX + col * cellW
                                val areaY = padTop + row * rowH
                                val originX = areaX + (cellW - boxPx) / 2f
                                val originY = areaY + (rowH - boxPx) / 2f + boxPx * 0.07f
                                withTransform({
                                    translate(
                                        originX + boxPx / 2f - boxOffset,
                                        originY + boxPx / 2f - boxOffset + 900f * gs,
                                    )
                                    scale(gs, -gs, pivot = Offset.Zero)
                                }) {
                                    for (j in 0 until activeStroke.coerceAtMost(a.paths.size)) {
                                        drawPath(a.paths[j], LetterInk)
                                    }
                                    val pts = a.medians.getOrNull(activeStroke).orEmpty()
                                    var w = a.widths.getOrElse(activeStroke) { 0f } * 0.85f
                                    if (w <= 0f) w = 200f
                                    w = w.coerceIn(BOX_RAW_MIN, BOX_RAW_MAX)
                                    for ((p1, p2) in StrokeCalc.partialPolyline(pts, activeProgress)) {
                                        drawLine(
                                            color = LetterInk,
                                            start = Offset(p1.x, p1.y),
                                            end = Offset(p2.x, p2.y),
                                            strokeWidth = w,
                                            cap = StrokeCap.Round,
                                        )
                                    }
                                }
                            }
                        } else if (started) {
                            doneGlyphs.forEach { (cell, glyph) -> drawCell(cell, glyph) }
                        } else {
                            previewGlyphs.forEach { (cell, glyph) -> drawCell(cell, glyph) }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private const val BOX_RAW_MIN = (1024f / 0.9f) * 0.012f
private const val BOX_RAW_MAX = (1024f / 0.9f) * 0.2f

@Composable
private fun EditorCard(
    text: String,
    onTextChange: (String) -> Unit,
    cols: Int,
    sizeRpx: Int,
    gapRpx: Int,
    durationMs: Int,
    onSizeChange: (Float) -> Unit,
    onSizeFinished: () -> Unit,
    onGapChange: (Float) -> Unit,
    onGapFinished: () -> Unit,
    onDurationChange: (Float) -> Unit,
    onDurationFinished: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    AppCard {
        SectionTitle("写点什么")
        Text(
            "用逐笔动画把这段话写到信纸上，最多 ${LetterLayout.MAX_LEN} 字。",
            fontSize = 11.sp,
            color = Muted,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 92.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, if (focused) Ink else Border, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
                textStyle = TextStyle(fontSize = 15.sp, color = Ink, lineHeight = 22.sp),
                cursorBrush = SolidColor(Accent),
            )
            if (text.isEmpty()) {
                Text("写下你想写的话…", fontSize = 14.sp, color = Muted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "每行 $cols 字",
            fontSize = 11.sp,
            color = Muted,
        )
        Spacer(Modifier.height(4.dp))
        SettingRow(
            label = "字号",
            valueText = SIZE_LEVELS[sizeLevel(sizeRpx)],
            value = sizeRpx.toFloat(),
            range = 45f..200f,
            steps = 30,
            onValueChange = onSizeChange,
            onFinished = onSizeFinished,
        )
        SettingRow(
            label = "字距",
            valueText = GAP_LEVELS[gapLevel(gapRpx)],
            value = gapRpx.toFloat(),
            range = -12f..24f,
            steps = 17,
            onValueChange = onGapChange,
            onFinished = onGapFinished,
        )
        SettingRow(
            label = "书写时长",
            valueText = durationText(durationMs),
            value = durationMs.toFloat(),
            range = 300f..3000f,
            steps = 26,
            onValueChange = onDurationChange,
            onFinished = onDurationFinished,
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = Soft, modifier = Modifier.width(56.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
            steps = steps,
            modifier = Modifier.weight(1f),
            colors = letterSliderColors(),
        )
        Text(
            valueText,
            fontSize = 12.sp,
            color = Muted,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp),
        )
    }
}

@Composable
private fun letterSliderColors(): SliderColors = SliderDefaults.colors(
    thumbColor = Accent,
    activeTrackColor = Accent,
    inactiveTrackColor = ChipBg,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)
