package org.caojun.shotocr.monitor.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.caojun.shotocr.monitor.R
import org.caojun.shotocr.monitor.Screenshot
import org.caojun.shotocr.monitor.ScreenshotMonitor

class MonitorViewModel(
    private val context: Context,
    private val monitor: ScreenshotMonitor
) : ViewModel() {

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    fun startMonitor() {
        monitor.start()
        _message.value = context.getString(R.string.monitor_started)
    }

    fun stopMonitor() {
        monitor.stop()
        _message.value = context.getString(R.string.monitor_stopped)
    }

    fun clearRecent() {
        monitor.clearRecent()
        _message.value = context.getString(R.string.recent_list_cleared)
    }
}

class MonitorViewModelFactory(
    private val context: Context,
    private val monitor: ScreenshotMonitor
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MonitorViewModel(context, monitor) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorTestScreen() {
    val context = LocalContext.current
    val monitor = remember { ScreenshotMonitor(context) }
    val viewModel: MonitorViewModel = viewModel(
        factory = MonitorViewModelFactory(context, monitor)
    )

    val isRunning by monitor.isRunning.collectAsState()
    val recentScreenshots by monitor.recentScreenshots.collectAsState()
    val message by viewModel.message.collectAsState()

    DisposableEffect(Unit) {
        onDispose { monitor.destroy() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.monitor_title),
            fontSize = 24.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Button(
                onClick = { viewModel.startMonitor() },
                enabled = !isRunning
            ) {
                Text(stringResource(R.string.start))
            }
            Button(
                onClick = { viewModel.stopMonitor() },
                enabled = isRunning
            ) {
                Text(stringResource(R.string.stop))
            }
            OutlinedButton(onClick = { viewModel.clearRecent() }) {
                Text(stringResource(R.string.clear))
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = stringResource(
                        if (isRunning) R.string.status_running else R.string.status_stopped
                    ),
                    fontSize = 16.sp
                )
                if (message.isNotEmpty()) {
                    Text(
                        text = message,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.recent_screenshots_count, recentScreenshots.size),
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (recentScreenshots.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_screenshots),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(recentScreenshots.reversed()) { screenshot ->
                    ScreenshotItem(screenshot)
                }
            }
        }
    }
}

@Composable
fun ScreenshotItem(screenshot: Screenshot) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = screenshot.displayName,
                fontSize = 14.sp
            )
            Text(
                text = screenshot.path,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
