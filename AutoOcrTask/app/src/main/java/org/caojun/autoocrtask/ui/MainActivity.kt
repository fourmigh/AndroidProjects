package org.caojun.autoocrtask.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.caojun.autoocrtask.data.AppNavigation
import org.caojun.autoocrtask.recorder.RecorderSession
import org.caojun.autoocrtask.shizuku.ShizukuReturn
import org.caojun.autoocrtask.ui.theme.AutoOcrTaskTheme
import org.caojun.shizuku.RikkaShizukuGateway

class MainActivity : ComponentActivity() {

    companion object {
        @Volatile
        var isResumed = false
            private set
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShizukuReturn.init(this)
        enableEdgeToEdge()
        setContent {
            AutoOcrTaskTheme {
                AutoOcrTaskApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        ShizukuReturn.onAppResumed()
        if (ShizukuReturn.consumeAutoRequestPermission()) {
            val gateway = RikkaShizukuGateway(this)
            if (gateway.isRunning() && !gateway.isPermissionGranted()) {
                gateway.requestPermission(ShizukuReturn.PERMISSION_REQUEST_CODE)
            }
        }
    }

    override fun onPause() {
        isResumed = false
        super.onPause()
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Tasks : Screen
    data class Editor(val taskId: String?) : Screen
}

@Composable
private fun AutoOcrTaskApp() {
    val context = LocalContext.current
    var screen by remember {
        mutableStateOf<Screen>(
            when {
                RecorderSession.hasPending(context) ->
                    Screen.Editor(RecorderSession.pendingTaskId(context))

                AppNavigation.consumeTasks(context) -> Screen.Tasks
                else -> Screen.Home
            }
        )
    }

    BackHandler(enabled = screen != Screen.Home) {
        screen = when (screen) {
            is Screen.Editor -> Screen.Tasks
            else -> Screen.Home
        }
    }

    when (val current = screen) {
        Screen.Home -> MainScreen(onOpenTasks = { screen = Screen.Tasks })

        Screen.Tasks -> TaskListScreen(
            onBack = { screen = Screen.Home },
            onCreate = { screen = Screen.Editor(null) },
            onEdit = { taskId -> screen = Screen.Editor(taskId) }
        )

        is Screen.Editor -> TaskEditorScreen(
            taskId = current.taskId,
            onBack = { screen = Screen.Tasks },
            onSaved = { screen = Screen.Tasks }
        )
    }
}
