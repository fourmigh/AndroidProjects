package org.caojun.quotamonitor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.caojun.quotamonitor.R
import org.caojun.quotamonitor.model.PlatformConfig
import org.caojun.quotamonitor.ui.theme.Accent
import org.caojun.quotamonitor.ui.theme.Border
import org.caojun.quotamonitor.ui.theme.CardBg
import org.caojun.quotamonitor.ui.theme.Ink
import org.caojun.quotamonitor.ui.theme.Muted
import org.caojun.quotamonitor.ui.theme.Paper
import org.caojun.quotamonitor.viewmodel.QuotaController

/**
 * 平台配置列表：启用开关、编辑、删除、恢复默认、导入导出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    controller: QuotaController,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onToast: (String) -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<PlatformConfig?>(null) }
    var showRestore by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    Scaffold(
        containerColor = Paper,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.settings_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.console_back), color = Ink)
                    }
                },
                actions = {
                    TextButton(onClick = onAdd) {
                        Text(stringResource(R.string.action_add_platform), color = Accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Paper,
                    titleContentColor = Ink,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (controller.configs.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.settings_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                items(controller.configs, key = { it.id }) { config ->
                    ConfigRow(
                        config = config,
                        onEnabledChange = { controller.setEnabled(config.id, it) },
                        onEdit = { onEdit(config.id) },
                        onDelete = { deleteTarget = config },
                    )
                    Spacer(Modifier.height(0.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { showRestore = true }) {
                    Text(stringResource(R.string.action_restore_defaults), color = Muted, fontSize = 13.sp)
                }
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(controller.exportJson()))
                    onToast(controller.text(R.string.settings_exported))
                }) {
                    Text(stringResource(R.string.action_export), color = Muted, fontSize = 13.sp)
                }
                TextButton(onClick = { showImport = true }) {
                    Text(stringResource(R.string.action_import), color = Muted, fontSize = 13.sp)
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.settings_delete_title)) },
            text = { Text(stringResource(R.string.settings_delete_message, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    controller.deleteConfig(target.id)
                    deleteTarget = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showRestore) {
        AlertDialog(
            onDismissRequest = { showRestore = false },
            title = { Text(stringResource(R.string.settings_restore_title)) },
            text = { Text(stringResource(R.string.settings_restore_message)) },
            confirmButton = {
                TextButton(onClick = {
                    controller.restoreDefaults()
                    showRestore = false
                }) { Text(stringResource(R.string.action_restore_defaults)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestore = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text(stringResource(R.string.settings_import_title)) },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    placeholder = { Text(stringResource(R.string.settings_import_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val count = controller.importJson(importText)
                    if (count < 0) {
                        onToast(controller.text(R.string.settings_import_failed))
                    } else {
                        onToast(controller.text(R.string.settings_imported, count))
                        showImport = false
                        importText = ""
                    }
                }) { Text(stringResource(R.string.action_import)) }
            },
            dismissButton = {
                TextButton(onClick = { showImport = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ConfigRow(
    config: PlatformConfig,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, Border),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        config.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                        fontSize = 15.sp,
                    )
                    Text(
                        config.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
                Switch(checked = config.enabled, onCheckedChange = onEnabledChange)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onEdit) {
                    Text(stringResource(R.string.action_edit), fontSize = 13.sp, color = Accent)
                }
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.action_delete), fontSize = 13.sp, color = Muted)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (config.enabled) "" else stringResource(R.string.settings_disabled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 结束

