package org.caojun.cupfox

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.caojun.cupfox.ui.MainScreen
import org.caojun.cupfox.ui.theme.CupFoxTheme
import org.caojun.cupfox.web.BrowserController

class MainActivity : ComponentActivity() {

    private lateinit var controller: BrowserController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        controller = BrowserController(this)
        setContent {
            CupFoxTheme {
                MainScreen(
                    controller = controller,
                    onExit = { finish() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::controller.isInitialized) {
            controller.onResume()
            controller.refreshHistory()
        }
    }

    override fun onPause() {
        if (::controller.isInitialized) controller.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::controller.isInitialized) controller.onDestroy()
        super.onDestroy()
    }
}
