package org.caojun.segmented

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 单选分段控件，固定等宽，默认使用主题 primary 作为选中色。
 *
 * @param options 选项列表
 * @param selectedIndex 当前选中索引
 * @param onSelected 选择回调（返回索引）
 * @param showIcon 是否显示 Material3 默认的选中图标
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showIcon: Boolean = false,
    colors: SegmentedButtonColors = defaultSegmentedControlColors(),
    label: @Composable (T) -> Unit
) {
    if (options.isEmpty()) return
    val index = selectedIndex.coerceIn(0, options.lastIndex)

    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { itemIndex, item ->
            val selected = itemIndex == index
            SegmentedButton(
                selected = selected,
                onClick = { onSelected(itemIndex) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = itemIndex,
                    count = options.size
                ),
                modifier = Modifier.weight(1f),
                icon = if (showIcon) {
                    { SegmentedButtonDefaults.Icon(selected) }
                } else {
                    {}
                },
                colors = colors
            ) {
                label(item)
            }
        }
    }
}

/** 纯文本选项的便捷重载。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showIcon: Boolean = false,
    colors: SegmentedButtonColors = defaultSegmentedControlColors()
) {
    SegmentedControl(
        options = options,
        selectedIndex = selectedIndex,
        onSelected = onSelected,
        modifier = modifier,
        showIcon = showIcon,
        colors = colors,
        label = {
            Text(
                text = it,
                maxLines = 1,
                softWrap = false
            )
        }
    )
}

/** 与当前 App 一致的分段控件配色。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun defaultSegmentedControlColors(): SegmentedButtonColors =
    SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.primary,
        activeContentColor = MaterialTheme.colorScheme.onPrimary,
        inactiveContainerColor = MaterialTheme.colorScheme.surface,
        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
