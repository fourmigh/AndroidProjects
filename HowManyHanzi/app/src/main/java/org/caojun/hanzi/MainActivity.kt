package org.caojun.hanzi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import org.caojun.hanzi.ui.AppViewModel
import org.caojun.hanzi.ui.CharDetailScreen
import org.caojun.hanzi.ui.CharsListScreen
import org.caojun.hanzi.ui.CharsScreen
import org.caojun.hanzi.ui.HistoryScreen
import org.caojun.hanzi.ui.HomeScreen
import org.caojun.hanzi.ui.IntroScreen
import org.caojun.hanzi.ui.LetterScreen
import org.caojun.hanzi.ui.QuizScreen
import org.caojun.hanzi.ui.ResultScreen
import org.caojun.hanzi.ui.Screen
import org.caojun.hanzi.ui.StrokePlayScreen
import org.caojun.hanzi.ui.theme.Accent
import org.caojun.hanzi.ui.theme.HanziTheme
import org.caojun.hanzi.ui.theme.Ink
import org.caojun.hanzi.ui.theme.Paper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HanziTheme {
                val vm: AppViewModel = viewModel()
                AppRoot(vm)
            }
        }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    val context = LocalContext.current
    LaunchedEffect(vm.message) {
        val msg = vm.message
        if (msg != null) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            vm.clearToast()
        }
    }

    if (vm.index == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Paper)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(
                    "我到底认识几个汉字",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                Spacer(Modifier.height(14.dp))
                Text("正在加载 8105 字字库…", fontSize = 12.sp, color = Color(0xFF7A858C))
            }
        }
        return
    }

    BackHandler(enabled = vm.canBack()) { vm.back() }

    when (val screen = vm.screen) {
        Screen.Home -> HomeScreen(vm)
        is Screen.Quiz -> QuizScreen(vm, screen)
        Screen.Result -> ResultScreen(vm)
        Screen.History -> HistoryScreen(vm)
        Screen.Chars -> CharsScreen(vm)
        is Screen.CharsList -> CharsListScreen(vm, screen)
        is Screen.CharDetail -> CharDetailScreen(vm, screen)
        is Screen.StrokePlay -> StrokePlayScreen(vm, screen)
        Screen.Letter -> LetterScreen(vm)
        Screen.Intro -> IntroScreen(vm)
    }
}
