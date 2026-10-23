package org.caojun.floatingbutton

import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FloatingButtonSettingsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var config by remember { mutableStateOf(FloatingButtonController.config(context)) }
    var mode by remember { mutableStateOf(FloatingButtonController.mode(context)) }
    var pendingMode by remember { mutableStateOf<FloatingButtonMode?>(null) }
    var showColorDialog by remember { mutableStateOf(false) }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = FloatingButtonController.isPermissionGranted(context)
        val target = pendingMode
        pendingMode = null
        if (granted) {
            val applied = target ?: FloatingButtonController.mode(context)
            mode = applied
            FloatingButtonController.setMode(context, applied)
            FloatingButtonController.applyMode(context)
        } else {
            mode = FloatingButtonMode.OFF
            FloatingButtonController.setMode(context, FloatingButtonMode.OFF)
            FloatingButtonController.applyMode(context)
            scope.launch {
                snackbar.showSnackbar(
                    context.getString(R.string.floating_button_permission_required)
                )
            }
        }
    }

    fun selectMode(target: FloatingButtonMode) {
        if (target != FloatingButtonMode.OFF &&
            !FloatingButtonController.isPermissionGranted(context)
        ) {
            pendingMode = target
            overlayPermissionLauncher.launch(FloatingButtonController.permissionIntent(context))
            return
        }
        mode = target
        FloatingButtonController.setMode(context, target)
        FloatingButtonController.applyMode(context)
    }

    fun updateConfig(transform: (FloatingButtonConfig) -> FloatingButtonConfig) {
        val updated = transform(config).normalized()
        config = updated
        FloatingButtonController.setConfig(context, updated)
    }

    if (showColorDialog) {
        CustomColorDialog(
            initialColor = config.backgroundColor,
            onDismiss = { showColorDialog = false },
            onConfirm = { color ->
                updateConfig { it.copy(backgroundColor = color) }
                showColorDialog = false
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.floating_button_settings_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.floating_button_settings_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.floating_button_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            SettingsCard(title = stringResource(R.string.floating_button_display_mode)) {
                Text(
                    text = stringResource(R.string.floating_button_display_mode_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                FloatingButtonMode.entries.forEach { item ->
                    OptionRow(
                        label = stringResource(modeLabelRes(item)),
                        selected = mode == item,
                        onClick = { selectMode(item) }
                    )
                }
            }

            SettingsCard(title = stringResource(R.string.floating_button_edge_tap_title)) {
                Text(
                    text = stringResource(R.string.floating_button_edge_tap_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                FloatingButtonEdgeTapAction.entries.forEach { action ->
                    OptionRow(
                        label = stringResource(edgeTapLabelRes(action)),
                        selected = config.edgeTapAction == action,
                        onClick = { updateConfig { it.copy(edgeTapAction = action) } }
                    )
                }
                if (config.edgeTapAction == FloatingButtonEdgeTapAction.REVEAL_FIRST) {
                    Text(
                        text = stringResource(R.string.floating_button_edge_tap_reveal_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            SettingsCard(title = stringResource(R.string.floating_button_size_title)) {
                ValueRow(
                    value = stringResource(
                        R.string.floating_button_size_value,
                        config.sizeDp
                    )
                )
                Slider(
                    value = config.sizeDp.toFloat(),
                    onValueChange = {
                        config = config.copy(sizeDp = snapSize(it)).normalized()
                    },
                    onValueChangeFinished = {
                        FloatingButtonController.setConfig(context, config)
                    },
                    valueRange = FloatingButtonConfig.MIN_SIZE_DP.toFloat()..
                        FloatingButtonConfig.MAX_SIZE_DP.toFloat(),
                    steps = SIZE_STEPS
                )
            }

            SettingsCard(title = stringResource(R.string.floating_button_appearance_title)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ButtonPreview(config)
                    Text(
                        text = stringResource(R.string.floating_button_preview),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Text(
                    text = stringResource(R.string.floating_button_shape_title),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FloatingButtonShape.entries.forEach { shape ->
                        FilterChip(
                            selected = config.shape == shape,
                            onClick = { updateConfig { it.copy(shape = shape) } },
                            label = { Text(stringResource(shapeLabelRes(shape))) }
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.floating_button_background_color),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PRESET_COLORS.forEach { color ->
                        ColorSwatch(
                            color = color,
                            selected = config.backgroundColor == color,
                            onClick = {
                                updateConfig { it.copy(backgroundColor = color) }
                            }
                        )
                    }
                }
                OutlinedButton(
                    onClick = { showColorDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.floating_button_custom_color))
                }

                Text(
                    text = stringResource(R.string.floating_button_opacity),
                    style = MaterialTheme.typography.bodyMedium
                )
                ValueRow(
                    value = stringResource(
                        R.string.floating_button_opacity_value,
                        (config.opacity * 100).roundToInt()
                    )
                )
                Slider(
                    value = config.opacity,
                    onValueChange = {
                        config = config.copy(opacity = it).normalized()
                    },
                    onValueChangeFinished = {
                        FloatingButtonController.setConfig(context, config)
                    },
                    valueRange = FloatingButtonConfig.MIN_OPACITY..
                        FloatingButtonConfig.MAX_OPACITY
                )

                Text(
                    text = stringResource(R.string.floating_button_icon_title),
                    style = MaterialTheme.typography.bodyMedium
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 5
                ) {
                    FloatingButtonIcon.entries.forEach { icon ->
                        IconOption(
                            icon = icon,
                            selected = config.icon == icon,
                            onClick = { updateConfig { it.copy(icon = icon) } }
                        )
                    }
                }
            }

            SettingsCard(title = stringResource(R.string.floating_button_edge_auto_hide_title)) {
                ValueRow(
                    value = stringResource(
                        R.string.floating_button_edge_auto_hide_value,
                        formatSeconds(config.edgeAutoHideMs)
                    )
                )
                Slider(
                    value = config.edgeAutoHideMs / 1_000f,
                    onValueChange = {
                        config = config.copy(edgeAutoHideMs = (it * 1_000).roundToInt().toLong())
                            .normalized()
                    },
                    onValueChangeFinished = {
                        FloatingButtonController.setConfig(context, config)
                    },
                    valueRange = FloatingButtonConfig.MIN_EDGE_AUTO_HIDE_MS / 1_000f..
                        FloatingButtonConfig.MAX_EDGE_AUTO_HIDE_MS / 1_000f,
                    steps = EDGE_AUTO_HIDE_STEPS
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        FloatingButtonController.resetPosition(context)
                        scope.launch {
                            snackbar.showSnackbar(
                                context.getString(R.string.floating_button_reset_done)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.floating_button_reset_position))
                }
                OutlinedButton(
                    onClick = {
                        FloatingButtonController.resetConfig(context)
                        config = FloatingButtonController.config(context)
                        scope.launch {
                            snackbar.showSnackbar(
                                context.getString(R.string.floating_button_reset_done)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.floating_button_reset_defaults))
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            content()
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ValueRow(value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ButtonPreview(config: FloatingButtonConfig) {
    val backgroundColor = Color(config.backgroundColor)
    val iconColor = if (backgroundColor.luminance() > 0.55f) Color.Black else Color.White
    val shape = when (config.shape) {
        FloatingButtonShape.CIRCLE -> CircleShape
        FloatingButtonShape.ROUNDED_RECT -> RoundedCornerShape(18.dp)
        FloatingButtonShape.SQUARE -> RoundedCornerShape(5.dp)
    }
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(shape)
            .background(backgroundColor.copy(alpha = config.opacity))
            .border(1.dp, Color.White.copy(alpha = 0.25f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(config.icon.drawableRes),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun ColorSwatch(
    color: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = borderColor,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

@Composable
private fun IconOption(
    icon: FloatingButtonIcon,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val tint = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .width(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            painter = painterResource(icon.drawableRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = stringResource(iconLabelRes(icon)),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CustomColorDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var red by remember(initialColor) { mutableStateOf(AndroidColor.red(initialColor)) }
    var green by remember(initialColor) { mutableStateOf(AndroidColor.green(initialColor)) }
    var blue by remember(initialColor) { mutableStateOf(AndroidColor.blue(initialColor)) }
    val color = AndroidColor.rgb(red, green, blue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.floating_button_custom_color_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(color))
                )
                ColorSlider(
                    label = stringResource(R.string.floating_button_color_red),
                    value = red,
                    onValueChange = { red = it }
                )
                ColorSlider(
                    label = stringResource(R.string.floating_button_color_green),
                    value = green,
                    onValueChange = { green = it }
                )
                ColorSlider(
                    label = stringResource(R.string.floating_button_color_blue),
                    value = blue,
                    onValueChange = { blue = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(color) }) {
                Text(stringResource(R.string.floating_button_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.floating_button_cancel))
            }
        }
    )
}

@Composable
private fun ColorSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value.toString(), style = MaterialTheme.typography.bodySmall)
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.roundToInt()) },
        valueRange = 0f..255f,
        steps = 254
    )
}

private fun modeLabelRes(mode: FloatingButtonMode): Int = when (mode) {
    FloatingButtonMode.OFF -> R.string.floating_button_mode_off
    FloatingButtonMode.EDGE_HIDE -> R.string.floating_button_mode_edge_hide
    FloatingButtonMode.ALWAYS_SHOW -> R.string.floating_button_mode_always_show
}

private fun edgeTapLabelRes(action: FloatingButtonEdgeTapAction): Int = when (action) {
    FloatingButtonEdgeTapAction.TRIGGER -> R.string.floating_button_edge_tap_trigger
    FloatingButtonEdgeTapAction.REVEAL_FIRST -> R.string.floating_button_edge_tap_reveal
}

private fun shapeLabelRes(shape: FloatingButtonShape): Int = when (shape) {
    FloatingButtonShape.CIRCLE -> R.string.floating_button_shape_circle
    FloatingButtonShape.ROUNDED_RECT -> R.string.floating_button_shape_rounded
    FloatingButtonShape.SQUARE -> R.string.floating_button_shape_square
}

private fun iconLabelRes(icon: FloatingButtonIcon): Int = when (icon) {
    FloatingButtonIcon.CAMERA -> R.string.floating_button_icon_camera
    FloatingButtonIcon.IMAGE -> R.string.floating_button_icon_image
    FloatingButtonIcon.CROP -> R.string.floating_button_icon_crop
    FloatingButtonIcon.SEARCH -> R.string.floating_button_icon_search
    FloatingButtonIcon.SETTINGS -> R.string.floating_button_icon_settings
    FloatingButtonIcon.EDIT -> R.string.floating_button_icon_edit
    FloatingButtonIcon.CALENDAR -> R.string.floating_button_icon_calendar
    FloatingButtonIcon.AGENDA -> R.string.floating_button_icon_agenda
    FloatingButtonIcon.ADD -> R.string.floating_button_icon_add
    FloatingButtonIcon.SHARE -> R.string.floating_button_icon_share
    FloatingButtonIcon.RECENT -> R.string.floating_button_icon_recent
    FloatingButtonIcon.INFO -> R.string.floating_button_icon_info
    FloatingButtonIcon.DELETE -> R.string.floating_button_icon_delete
    FloatingButtonIcon.SAVE -> R.string.floating_button_icon_save
}

private fun snapSize(value: Float): Int {
    val snapped = (value / SIZE_STEP_DP).roundToInt() * SIZE_STEP_DP
    return snapped.coerceIn(
        FloatingButtonConfig.MIN_SIZE_DP,
        FloatingButtonConfig.MAX_SIZE_DP
    )
}

private fun formatSeconds(ms: Long): String =
    if (ms % 1_000L == 0L) {
        (ms / 1_000L).toString()
    } else {
        String.format(Locale.ROOT, "%.1f", ms / 1_000.0)
    }

private val PRESET_COLORS = listOf(
    0xFF212121.toInt(),
    0xFF1976D2.toInt(),
    0xFF00897B.toInt(),
    0xFF388E3C.toInt(),
    0xFFF57C00.toInt(),
    0xFFD32F2F.toInt(),
    0xFF7B1FA2.toInt(),
    0xFFF5F5F5.toInt()
)

private const val SIZE_STEP_DP = 4
private const val SIZE_STEPS = 9
private const val EDGE_AUTO_HIDE_STEPS = 17
