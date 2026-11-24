package org.caojun.quotamonitor

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.caojun.quotamonitor.ui.AddPlatformWizardScreen
import org.caojun.quotamonitor.ui.ConsoleWebScreen
import org.caojun.quotamonitor.ui.DashboardScreen
import org.caojun.quotamonitor.ui.PlatformEditScreen
import org.caojun.quotamonitor.ui.SettingsScreen
import org.caojun.quotamonitor.ui.theme.QuotaTheme
import org.caojun.quotamonitor.viewmodel.QuotaController

private sealed interface Screen {
    data object Dashboard : Screen
    data object Settings : Screen
    data class Edit(val configId: String?) : Screen
    data class Console(val configId: String) : Screen
    data object Wizard : Screen
}

class MainActivity : ComponentActivity() {

    private lateinit var controller: QuotaController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        controller = QuotaController(applicationContext)
        setContent {
            QuotaTheme {
                AppRoot(controller)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::controller.isInitialized) controller.flushCookies()
    }

    override fun onDestroy() {
        if (::controller.isInitialized) controller.destroy()
        super.onDestroy()
    }
}

@Composable
private fun AppRoot(controller: QuotaController) {
    var screen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
    val context = LocalContext.current

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) { controller.start() }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = screen) {
            Screen.Dashboard -> {
                DashboardScreen(
                    controller = controller,
                    onOpenConsole = { screen = Screen.Console(it) },
                    onOpenSettings = { screen = Screen.Settings },
                )
            }

            Screen.Settings -> {
                BackHandler { screen = Screen.Dashboard }
                SettingsScreen(
                    controller = controller,
                    onBack = { screen = Screen.Dashboard },
                    onAdd = { screen = Screen.Wizard },
                    onEdit = { screen = Screen.Edit(it) },
                    onToast = { toast(it) },
                )
            }

            is Screen.Edit -> {
                BackHandler { screen = Screen.Settings }
                PlatformEditScreen(
                    controller = controller,
                    configId = current.configId,
                    onBack = { screen = Screen.Settings },
                    onToast = { toast(it) },
                )
            }

            is Screen.Console -> {
                BackHandler { screen = Screen.Dashboard }
                ConsoleWebScreen(
                    controller = controller,
                    configId = current.configId,
                    onBack = { screen = Screen.Dashboard },
                )
            }

            Screen.Wizard -> {
                BackHandler { screen = Screen.Settings }
                AddPlatformWizardScreen(
                    controller = controller,
                    onBack = { screen = Screen.Settings },
                    onSaved = { screen = Screen.Settings },
                    onToast = { toast(it) },
                )
            }
        }
    }
}
