package org.caojun.quotamonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.caojun.quotamonitor.model.Platform
import org.caojun.quotamonitor.ui.ConsoleWebScreen
import org.caojun.quotamonitor.ui.DashboardScreen
import org.caojun.quotamonitor.ui.theme.QuotaTheme
import org.caojun.quotamonitor.viewmodel.QuotaController

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
    var consolePlatform by rememberSaveable { mutableStateOf<Platform?>(null) }

    // 首帧后开始静默刷新。
    LaunchedEffect(Unit) { controller.start() }

    Box(modifier = Modifier.fillMaxSize()) {
        DashboardScreen(
            controller = controller,
            onOpenConsole = { consolePlatform = it },
        )

        consolePlatform?.let { platform ->
            ConsoleWebScreen(
                controller = controller,
                platform = platform,
                onBack = { consolePlatform = null },
            )
        }
    }
}
