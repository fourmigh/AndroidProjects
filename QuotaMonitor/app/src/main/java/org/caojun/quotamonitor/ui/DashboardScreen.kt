package org.caojun.quotamonitor.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.caojun.quotamonitor.R
import org.caojun.quotamonitor.model.Platform
import org.caojun.quotamonitor.model.PlatformState
import org.caojun.quotamonitor.model.QuotaItem
import org.caojun.quotamonitor.model.SyncStatus
import org.caojun.quotamonitor.viewmodel.HiddenWebFrame
import org.caojun.quotamonitor.viewmodel.QuotaController
import org.caojun.quotamonitor.ui.theme.Accent
import org.caojun.quotamonitor.ui.theme.Border
import org.caojun.quotamonitor.ui.theme.CardBg
import org.caojun.quotamonitor.ui.theme.ErrRed
import org.caojun.quotamonitor.ui.theme.Ink
import org.caojun.quotamonitor.ui.theme.Muted
import org.caojun.quotamonitor.ui.theme.OkGreen
import org.caojun.quotamonitor.ui.theme.Paper
import org.caojun.quotamonitor.ui.theme.WarnAmber
import org.caojun.quotamonitor.ui.theme.progressColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    controller: QuotaController,
    onOpenConsole: (Platform) -> Unit,
) {
    Scaffold(
        containerColor = Paper,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("套餐限额", fontWeight = FontWeight.Bold)
                        Text(
                            "OpenCode / 火山引擎 Coding Plan",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Paper,
                    titleContentColor = Ink,
                    actionIconContentColor = Accent,
                ),
                actions = {
                    IconButton(onClick = { controller.refreshAll() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = "全部刷新",
                            tint = Accent,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 常驻隐藏 WebView 宿主：全尺寸、置于内容下层、不拦截触摸。
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    HiddenWebFrame(context).also { frame ->
                        frame.addOnAttachStateChangeListener(
                            object : android.view.View.OnAttachStateChangeListener {
                                override fun onViewAttachedToWindow(v: android.view.View) {
                                    controller.bindHidden(frame as ViewGroup)
                                }

                                override fun onViewDetachedFromWindow(v: android.view.View) = Unit
                            }
                        )
                    }
                },
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Paper)
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(Platform.entries, key = { it.name }) { platform ->
                    PlatformCard(
                        platform = platform,
                        state = controller.states[platform] ?: PlatformState(),
                        onRefresh = { controller.refresh(platform) },
                        onOpenConsole = { onOpenConsole(platform) },
                    )
                }
                item { FooterNote() }
            }
        }
    }
}

// ---------------------------------------------------------------- 平台卡片

@Composable
private fun PlatformCard(
    platform: Platform,
    state: PlatformState,
    onRefresh: () -> Unit,
    onOpenConsole: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    platform.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(state)
            }

            state.snapshot?.planName?.let { plan ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "套餐：$plan",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Accent,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(12.dp))

            when (state.status) {
                SyncStatus.IDLE -> EmptyHint("尚未同步，点下方按钮开始")

                SyncStatus.LOADING -> {
                    if (state.snapshot == null) {
                        EmptyHint("正在加载控制台页面…")
                    } else {
                        state.snapshot.items.forEach { MetricRow(it) }
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (state.progress.coerceIn(0, 100)) / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = Accent,
                        trackColor = Border,
                    )
                }

                SyncStatus.NEED_LOGIN -> {
                    EmptyHint("需要登录才能读取额度")
                    Text(
                        state.message ?: platform.loginHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = WarnAmber,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                SyncStatus.ERROR -> {
                    state.snapshot?.let { snap ->
                        if (snap.items.isEmpty() && snap.lines.isEmpty()) {
                            EmptyHint("同步失败")
                        } else {
                            snap.items.forEach { MetricRow(it) }
                        }
                    } ?: EmptyHint("同步失败")
                    Text(
                        state.message ?: "未知错误",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrRed,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                SyncStatus.OK -> {
                    val snap = state.snapshot
                    if (snap == null || (snap.items.isEmpty() && snap.lines.isEmpty())) {
                        EmptyHint("页面已加载，但未识别到额度数据")
                    } else {
                        if (snap.items.isEmpty()) {
                            snap.lines.take(8).forEach { line ->
                                Text(
                                    line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Ink,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(vertical = 1.dp),
                                )
                            }
                        } else {
                            snap.items.forEach { MetricRow(it) }
                        }
                        snap.resetAt?.let { reset ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                reset,
                                style = MaterialTheme.typography.bodySmall,
                                color = WarnAmber,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "更新于 ${formatTime(snap.capturedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (snap.source == org.caojun.quotamonitor.model.Source.NETWORK) "接口数据" else "页面解析",
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = state.status != SyncStatus.LOADING,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("刷新")
                }
                FilledTonalButton(
                    onClick = onOpenConsole,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.status == SyncStatus.NEED_LOGIN) "去登录" else "打开网页")
                }
            }
        }
    }
}

@Composable
private fun StatusChip(state: PlatformState) {
    val (label, color) = when (state.status) {
        SyncStatus.IDLE -> "未同步" to Muted
        SyncStatus.LOADING -> "同步中…" to Accent
        SyncStatus.NEED_LOGIN -> "需登录" to WarnAmber
        SyncStatus.OK -> "已同步" to OkGreen
        SyncStatus.ERROR -> "失败" to ErrRed
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        if (state.status == SyncStatus.LOADING) {
            CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                strokeWidth = 1.5.dp,
                color = Accent,
            )
            Spacer(Modifier.width(6.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, RoundedCornerShape(50)),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------- 单项额度

@Composable
private fun MetricRow(item: QuotaItem) {
    val fraction = item.fraction
    val color = progressColor(fraction)
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.resetHint?.let { hint ->
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                valueText(item),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(
            progress = { fraction ?: 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = color,
            trackColor = color.copy(alpha = 0.14f),
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

private fun valueText(item: QuotaItem): String = when {
    item.used != null && item.total != null ->
        "${formatNum(item.used)} / ${formatNum(item.total)}" +
            (item.percent?.let { "（${"%.0f".format(it)}%）" } ?: "")
    item.percent != null -> "${"%.1f".format(item.percent)}%"
    item.used != null -> formatNum(item.used)
    else -> "—"
}

private fun formatNum(v: Double): String {
    val abs = abs(v)
    return when {
        abs >= 1_000_000_000 -> "%.1fB".format(v / 1_000_000_000)
        abs >= 1_000_000 -> "%.1fM".format(v / 1_000_000)
        abs >= 10_000 -> "%.1fK".format(v / 1_000)
        v == v.toLong().toDouble() -> String.format(Locale.US, "%,d", v.toLong())
        else -> "%.2f".format(v)
    }
}

private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

private fun formatTime(ts: Long): String = timeFormat.format(Date(ts))

// ---------------------------------------------------------------- 其他

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Muted,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun FooterNote() {
    Text(
        "说明：额度数据来自各控制台网页的接口与页面解析，仅供参考，" +
            "以官网控制台显示为准。登录状态保存在本机 WebView 中。",
        style = MaterialTheme.typography.bodySmall,
        color = Muted,
        modifier = Modifier.padding(top = 4.dp),
    )
}
