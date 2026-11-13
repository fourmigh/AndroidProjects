package org.caojun.hanzi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import org.caojun.hanzi.ui.theme.CardBg
import org.caojun.hanzi.ui.theme.ChipBg
import org.caojun.hanzi.ui.theme.Ink
import org.caojun.hanzi.ui.theme.Muted
import org.caojun.hanzi.ui.theme.Paper
import org.caojun.hanzi.ui.theme.Soft
import org.caojun.hanzi.ui.theme.levelColor

/**
 * 统一的三段式页面布局：固定标题、固定页脚、中间内容区。
 *
 * - 标题区由 [ScreenHeader] 渲染，不参与滚动。
 * - [headerContent] 是标题下方的固定扩展区（字库页的档位与搜索框）。
 * - [content] 接收一个带 weight 的 Modifier，由页面决定用普通滚动或 Lazy 列表。
 * - [footer] 固定在底部，页面不存在页脚时可传 null。
 */
@Composable
fun PageScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    headerTrailing: (@Composable RowScope.() -> Unit)? = null,
    headerContent: (@Composable ColumnScope.() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
    ) {
        ScreenHeader(
            title = title,
            subtitle = subtitle,
            onBack = onBack,
            trailing = headerTrailing,
        )
        if (headerContent != null) {
            Column(modifier = Modifier.fillMaxWidth(), content = headerContent)
        }
        content(Modifier.weight(1f).fillMaxWidth())
        if (footer != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 10.dp),
            ) {
                footer()
            }
        }
    }
}

/** PageScaffold 中普通页面的滚动内容区。 */
@Composable
fun ScrollColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        content = content,
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, Color(0x11000000), RoundedCornerShape(16.dp))
            .padding(18.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = Ink,
    )
}

@Composable
fun SelectableChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Ink else ChipBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = if (selected) Color.White else Soft,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(if (enabled) Accent else Accent.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, Border, RoundedCornerShape(26.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) Ink else Muted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun LevelMark(level: Int, modifier: Modifier = Modifier, size: Int = 26, fontSize: Int = 13) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(levelColor(level)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            org.caojun.hanzi.engine.Levels.markOf(level),
            color = Color.White,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun RankBadge(rank: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Accent)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(rank, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("‹ 返回", fontSize = 14.sp, color = Soft)
            }
            Spacer(Modifier.size(4.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = 1.sp)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = Muted, lineHeight = 18.sp)
            }
        }
        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

@Composable
fun EmptyState(title: String, desc: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        if (desc != null) {
            Spacer(Modifier.height(8.dp))
            Text(desc, fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center, lineHeight = 20.sp)
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    cancelText: String = "取消",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = { Text(message, fontSize = 14.sp, lineHeight = 21.sp) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) {
                Text(confirmText, color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelText, color = Muted) }
        },
        containerColor = CardBg,
    )
}

@Composable
fun LevelBarRow(level: Int, count: Int, max: Int, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LevelMark(level, size = 24, fontSize = 12)
        Spacer(Modifier.size(6.dp))
        Text(label, fontSize = 12.sp, color = Soft, modifier = Modifier.width(34.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(ChipBg),
        ) {
            if (max > 0 && count > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(count.toFloat() / max)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(levelColor(level)),
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Text("$count", fontSize = 12.sp, color = Muted, textAlign = TextAlign.End, modifier = Modifier.width(26.dp))
    }
}

@Composable
fun ChipLine(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
