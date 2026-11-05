package org.caojun.autoocrtask.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.caojun.autoocrtask.R
import org.caojun.autoocrtask.data.AutomationTask
import org.caojun.autoocrtask.data.AutomationTaskStore
import org.caojun.autoocrtask.service.AutomationStarter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit
) {
    val context = LocalContext.current
    val store = remember { AutomationTaskStore(context) }
    var tasks by remember { mutableStateOf(store.list()) }
    var pendingDelete by remember { mutableStateOf<AutomationTask?>(null) }
    val shizukuState = rememberShizukuState()

    fun refresh() {
        tasks = store.list()
    }

    fun run(task: AutomationTask) {
        if (shizukuState != ShizukuState.READY) {
            Toast.makeText(
                context,
                context.getString(R.string.run_no_shizuku),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (!AutomationStarter.start(context, task.id)) {
            Toast.makeText(
                context,
                context.getString(R.string.run_start_failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tasks_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.common_back))
                    }
                },
                actions = {
                    TextButton(onClick = onCreate) {
                        Text(stringResource(R.string.task_new))
                    }
                }
            )
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.tasks_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onRun = { run(task) },
                        onEdit = { onEdit(task.id) },
                        onDelete = { pendingDelete = task }
                    )
                }
            }
        }
    }

    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.task_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.task_delete_confirm_message, task.name))
            },
            confirmButton = {
                TextButton(onClick = {
                    store.remove(task.id)
                    pendingDelete = null
                    refresh()
                }) {
                    Text(stringResource(R.string.task_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun TaskCard(
    task: AutomationTask,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = task.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = task.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = stringResource(R.string.task_steps_count, task.steps.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Button(onClick = onRun) {
                    Text(stringResource(R.string.task_run))
                }
                OutlinedButton(onClick = onEdit) {
                    Text(stringResource(R.string.task_edit))
                }
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.task_delete))
                }
            }
        }
    }
}
