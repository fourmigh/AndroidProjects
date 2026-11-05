package org.caojun.autoocrtask.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.caojun.autoocrtask.R
import org.caojun.autoocrtask.automation.AutomationLauncher
import org.caojun.autoocrtask.automation.StepDescriptions
import org.caojun.autoocrtask.data.AutomationStep
import org.caojun.autoocrtask.data.AutomationTask
import org.caojun.autoocrtask.data.AutomationTaskStore
import org.caojun.autoocrtask.data.LaunchSpec
import org.caojun.autoocrtask.data.LaunchType
import org.caojun.autoocrtask.data.StepType
import org.caojun.autoocrtask.recorder.RecorderSession
import org.caojun.autoocrtask.recorder.RecorderStartResult
import org.caojun.autoocrtask.recorder.RecorderStarter
import org.caojun.segmented.SegmentedControl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    taskId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { AutomationTaskStore(context) }
    val existing = remember(taskId) { taskId?.let(store::find) }

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var packageName by remember { mutableStateOf(existing?.packageName.orEmpty()) }
    var launchType by remember { mutableStateOf(existing?.launch?.type ?: LaunchType.HOME) }
    var activity by remember { mutableStateOf(existing?.launch?.activity.orEmpty()) }
    var uri by remember { mutableStateOf(existing?.launch?.uri.orEmpty()) }
    val steps = remember {
        mutableStateListOf<AutomationStep>().apply {
            addAll(existing?.steps.orEmpty())
        }
    }

    var showAppPicker by remember { mutableStateOf(false) }
    var overlayGranted by remember {
        mutableStateOf(RecorderStarter.isPermissionGranted(context))
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        overlayGranted = RecorderStarter.isPermissionGranted(context)
    }

    fun applyRecorderResult() {
        val result = RecorderSession.consume(context) ?: return
        if (result.taskId != taskId) return
        name = result.name
        packageName = result.packageName
        launchType = result.launch.type
        activity = result.launch.activity
        uri = result.launch.uri
        steps.clear()
        steps.addAll(result.existingSteps)
        steps.addAll(result.recordedSteps)
        if (result.recordedSteps.isNotEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.editor_recorder_applied, result.recordedSteps.size),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LifecycleResumeEffect(Unit) {
        overlayGranted = RecorderStarter.isPermissionGranted(context)
        applyRecorderResult()
        onPauseOrDispose { }
    }

    fun save() {
        if (name.isBlank() || packageName.isBlank()) {
            Toast.makeText(
                context,
                context.getString(R.string.editor_save_invalid),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (steps.isEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.editor_need_step),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val task = AutomationTask(
            id = existing?.id ?: AutomationTaskStore.newId(),
            name = name.trim(),
            packageName = packageName.trim(),
            launch = LaunchSpec(
                type = launchType,
                activity = activity.trim(),
                uri = uri.trim()
            ),
            steps = steps.toList()
        )
        store.save(task)
        Toast.makeText(
            context,
            context.getString(R.string.editor_saved),
            Toast.LENGTH_SHORT
        ).show()
        onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (existing == null) R.string.editor_title_new
                            else R.string.editor_title_edit
                        )
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.common_back))
                    }
                },
                actions = {
                    TextButton(onClick = { save() }) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.editor_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = packageName,
                onValueChange = { packageName = it },
                label = { Text(stringResource(R.string.editor_package)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = { showAppPicker = true }) {
                Text(stringResource(R.string.editor_package_pick))
            }

            Text(
                text = stringResource(R.string.editor_launch_type),
                style = MaterialTheme.typography.titleSmall
            )
            SegmentedControl(
                options = LaunchType.entries,
                selectedIndex = LaunchType.entries.indexOf(launchType).coerceAtLeast(0),
                onSelected = { index -> launchType = LaunchType.entries[index] },
                modifier = Modifier.fillMaxWidth(),
                label = { type ->
                    Text(
                        text = stringResource(launchTypeRes(type)),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            )

            when (launchType) {
                LaunchType.ACTIVITY -> OutlinedTextField(
                    value = activity,
                    onValueChange = { activity = it },
                    label = { Text(stringResource(R.string.editor_launch_activity_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                LaunchType.URI -> OutlinedTextField(
                    value = uri,
                    onValueChange = { uri = it },
                    label = { Text(stringResource(R.string.editor_launch_uri_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                LaunchType.HOME -> Unit
            }

            Text(
                text = stringResource(R.string.editor_steps),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (steps.isEmpty()) {
                Text(
                    text = stringResource(R.string.editor_steps_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            steps.forEachIndexed { index, step ->
                StepCard(
                    index = index,
                    step = step,
                    onUpdate = { steps[index] = it },
                    onUp = {
                        if (index > 0) {
                            val moving = steps.removeAt(index)
                            steps.add(index - 1, moving)
                        }
                    },
                    onDown = {
                        if (index < steps.lastIndex) {
                            val moving = steps.removeAt(index)
                            steps.add(index + 1, moving)
                        }
                    },
                    onDelete = { steps.removeAt(index) }
                )
            }

            Button(
                onClick = {
                    if (packageName.isBlank()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.editor_save_invalid),
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }
                    if (!overlayGranted) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.editor_recorder_permission),
                            Toast.LENGTH_LONG
                        ).show()
                        overlayPermissionLauncher.launch(
                            RecorderStarter.permissionIntent(context)
                        )
                        return@Button
                    }
                    when (RecorderStarter.start(
                        context = context,
                        taskId = existing?.id,
                        name = name.trim(),
                        packageName = packageName.trim(),
                        launch = LaunchSpec(
                            type = launchType,
                            activity = activity.trim(),
                            uri = uri.trim()
                        ),
                        existingSteps = steps.toList()
                    )) {
                        RecorderStartResult.OK -> Unit

                        RecorderStartResult.NO_OVERLAY_PERMISSION -> {
                            Toast.makeText(
                                context,
                                R.string.editor_recorder_permission,
                                Toast.LENGTH_LONG
                            ).show()
                            overlayPermissionLauncher.launch(
                                RecorderStarter.permissionIntent(context)
                            )
                        }

                        RecorderStartResult.SHIZUKU_NOT_RUNNING -> {
                            Toast.makeText(
                                context,
                                R.string.recorder_error_shizuku_not_running,
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        RecorderStartResult.SHIZUKU_NOT_GRANTED -> {
                            Toast.makeText(
                                context,
                                R.string.recorder_error_shizuku_not_granted,
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        RecorderStartResult.PACKAGE_MISSING -> {
                            Toast.makeText(
                                context,
                                R.string.editor_save_invalid,
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        RecorderStartResult.SERVICE_FAILED -> {
                            Toast.makeText(
                                context,
                                R.string.editor_recorder_failed,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.editor_add_step))
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onPick = { pkg, label ->
                packageName = pkg
                if (name.isBlank()) name = label
                showAppPicker = false
            }
        )
    }
}

@Composable
private fun StepCard(
    index: Int,
    step: AutomationStep,
    onUpdate: (AutomationStep) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${index + 1}. ${StepDescriptions.typeLabel(context, step.type)}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onUp) {
                    Text(stringResource(R.string.editor_move_up))
                }
                TextButton(onClick = onDown) {
                    Text(stringResource(R.string.editor_move_down))
                }
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.common_delete))
                }
            }
            Text(
                text = StepDescriptions.describe(context, step),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            when (step.type) {
                StepType.WAIT_TEXT, StepType.TAP_TEXT -> {
                    OutlinedTextField(
                        value = step.text,
                        onValueChange = { onUpdate(step.copy(text = it)) },
                        label = { Text(stringResource(R.string.editor_step_text)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    NumberField(
                        label = stringResource(R.string.editor_step_timeout),
                        value = step.timeoutMs,
                        onValueChange = { onUpdate(step.copy(timeoutMs = it)) }
                    )
                    BoolRow(
                        label = stringResource(R.string.editor_step_exact),
                        checked = step.exact,
                        onCheckedChange = { onUpdate(step.copy(exact = it)) }
                    )
                    BoolRow(
                        label = stringResource(R.string.editor_step_use_ocr),
                        checked = step.useOcr,
                        onCheckedChange = { onUpdate(step.copy(useOcr = it)) }
                    )
                }

                StepType.WAIT_PACKAGE -> {
                    OutlinedTextField(
                        value = step.packageName,
                        onValueChange = { onUpdate(step.copy(packageName = it)) },
                        label = { Text(stringResource(R.string.editor_package)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    NumberField(
                        label = stringResource(R.string.editor_step_timeout),
                        value = step.timeoutMs,
                        onValueChange = { onUpdate(step.copy(timeoutMs = it)) }
                    )
                }

                StepType.TAP_POINT -> {
                    RatioField(
                        label = stringResource(R.string.editor_step_x),
                        value = step.x,
                        onValueChange = { onUpdate(step.copy(x = it)) }
                    )
                    RatioField(
                        label = stringResource(R.string.editor_step_y),
                        value = step.y,
                        onValueChange = { onUpdate(step.copy(y = it)) }
                    )
                }

                StepType.SWIPE -> {
                    RatioField(
                        label = stringResource(R.string.editor_step_x),
                        value = step.x,
                        onValueChange = { onUpdate(step.copy(x = it)) }
                    )
                    RatioField(
                        label = stringResource(R.string.editor_step_y),
                        value = step.y,
                        onValueChange = { onUpdate(step.copy(y = it)) }
                    )
                    RatioField(
                        label = stringResource(R.string.editor_step_x) + " 2",
                        value = step.x2,
                        onValueChange = { onUpdate(step.copy(x2 = it)) }
                    )
                    RatioField(
                        label = stringResource(R.string.editor_step_y) + " 2",
                        value = step.y2,
                        onValueChange = { onUpdate(step.copy(y2 = it)) }
                    )
                    NumberField(
                        label = stringResource(R.string.editor_step_duration),
                        value = step.durationMs.toLong(),
                        onValueChange = { onUpdate(step.copy(durationMs = it.toInt())) }
                    )
                }

                StepType.DELAY -> {
                    NumberField(
                        label = stringResource(R.string.editor_step_ms),
                        value = step.delayMs,
                        onValueChange = { onUpdate(step.copy(delayMs = it)) }
                    )
                }

                StepType.BACK -> Unit
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Long,
    onValueChange: (Long) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toLongOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )
}

@Composable
private fun RatioField(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toFloatOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )
}

@Composable
private fun BoolRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun AppPickerDialog(
    onDismiss: () -> Unit,
    onPick: (packageName: String, label: String) -> Unit
) {
    val context = LocalContext.current
    val apps = remember {
        AutomationLauncher.queryLaunchablePackages(context)
    }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        apps.filter { (pkg, label) ->
            pkg.contains(query, ignoreCase = true) ||
                label.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_picker_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.app_picker_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    Text(
                        text = stringResource(R.string.app_picker_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .padding(top = 8.dp)
                    ) {
                        items(filtered, key = { it.first }) { (pkg, label) ->
                            TextButton(
                                onClick = { onPick(pkg, label) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(label)
                                    Text(
                                        text = pkg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

private fun launchTypeRes(type: LaunchType): Int = when (type) {
    LaunchType.HOME -> R.string.editor_launch_home
    LaunchType.ACTIVITY -> R.string.editor_launch_activity
    LaunchType.URI -> R.string.editor_launch_uri
}
