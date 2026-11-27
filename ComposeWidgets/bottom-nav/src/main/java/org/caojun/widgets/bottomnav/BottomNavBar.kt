package org.caojun.widgets.bottomnav

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.caojun.widgets.theme.LocalWidgetColors

data class BottomNavItem<T>(
    val value: T,
    val label: String,
    val icon: Painter,
)

object BottomNavDefaults {
    val ContentHeight: Dp = 64.dp
}

/** 图标 + 文字的底部导航栏，配色跟随 WidgetTheme，背景可传半透明色。 */
@Composable
fun <T> BottomNavBar(
    items: List<BottomNavItem<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = LocalWidgetColors.current.bar,
    selectedColor: Color = LocalWidgetColors.current.barSelected,
    unselectedColor: Color = LocalWidgetColors.current.barUnselected,
    indicatorColor: Color = LocalWidgetColors.current.barIndicator,
    contentHeight: Dp = BottomNavDefaults.ContentHeight,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        tonalElevation = 0.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(contentHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val isSelected = item.value == selected
                    val tint by animateColorAsState(
                        targetValue = if (isSelected) selectedColor else unselectedColor,
                        label = "bottomNavTint",
                    )
                    val indicator by animateColorAsState(
                        targetValue = if (isSelected) indicatorColor else Color.Transparent,
                        label = "bottomNavIndicator",
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelected(item.value) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = 40.dp)
                                .height(30.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(indicator),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = item.icon,
                                contentDescription = item.label,
                                tint = tint,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            color = tint,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
