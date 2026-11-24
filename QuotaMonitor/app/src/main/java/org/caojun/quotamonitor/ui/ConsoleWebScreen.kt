package org.caojun.quotamonitor.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.caojun.quotamonitor.R
import org.caojun.quotamonitor.model.SyncStatus
import org.caojun.quotamonitor.viewmodel.QuotaController
import org.caojun.quotamonitor.ui.theme.Accent
import org.caojun.quotamonitor.ui.theme.Border
import org.caojun.quotamonitor.ui.theme.Ink
import org.caojun.quotamonitor.ui.theme.Muted
import org.caojun.quotamonitor.ui.theme.Paper

/**
 * 内置控制台网页：承载同一个常驻 WebView（登录态复用），
 * 用于首次登录、核对原始网页或在页面解析失败时手动查看。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleWebScreen(
    controller: QuotaController,
    configId: String,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val config = controller.findConfig(configId)
    val st = controller.states[configId]
    val loading = st?.status == SyncStatus.LOADING
    var uaDesktop by remember(configId) { mutableStateOf(controller.isDesktopUa(configId)) }
    val title = config?.name ?: ""

    Scaffold(
        containerColor = Paper,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                title,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(
                                    when {
                                        loading -> R.string.console_loading
                                        st?.status == SyncStatus.NEED_LOGIN -> R.string.console_login_hint
                                        else -> R.string.console_shared_session
                                    },
                                ),
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
                        TextButton(onClick = { controller.refresh(configId) }) {
                            Text(stringResource(R.string.action_refresh), color = Accent)
                        }
                        TextButton(
                            onClick = {
                                uaDesktop = !uaDesktop
                                controller.toggleDesktopUa(configId)
                            },
                        ) {
                            Text(
                                stringResource(
                                    if (uaDesktop) R.string.console_ua_mobile else R.string.console_ua_desktop,
                                ),
                                color = Accent,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Paper,
                        titleContentColor = Ink,
                        navigationIconContentColor = Ink,
                        actionIconContentColor = Accent,
                    ),
                )
                if (loading) {
                    LinearProgressIndicator(
                        progress = { ((st?.progress ?: 0).coerceIn(0, 100)) / 100f },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = Accent,
                        trackColor = Border,
                    )
                }
            }
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            factory = { context ->
                android.widget.FrameLayout(context)
            },
            update = { container ->
                controller.attachForDisplay(configId, container as ViewGroup)
            },
            onRelease = { container ->
                controller.detachFromDisplay(configId, container as ViewGroup)
            },
        )
    }
}
