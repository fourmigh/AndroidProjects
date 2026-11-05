package org.caojun.autoocrtask.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.caojun.autoocrtask.R
import org.caojun.autoocrtask.shizuku.ShizukuReturn
import org.caojun.expandablecard.ExpandableCard
import org.caojun.shizuku.RikkaShizukuGateway
import org.caojun.shizuku.ShizukuGateway

enum class ShizukuState {
    CHECKING,
    NOT_INSTALLED,
    NOT_RUNNING,
    NOT_GRANTED,
    READY
}

fun resolveShizukuState(gateway: ShizukuGateway): ShizukuState = when {
    !gateway.isInstalled() -> ShizukuState.NOT_INSTALLED
    !gateway.isRunning() -> ShizukuState.NOT_RUNNING
    !gateway.isPermissionGranted() -> ShizukuState.NOT_GRANTED
    else -> ShizukuState.READY
}

@Composable
fun rememberShizukuState(): ShizukuState {
    val context = LocalContext.current
    val gateway = remember { RikkaShizukuGateway(context) }
    var state by remember { mutableStateOf(resolveShizukuState(gateway)) }

    fun refresh() {
        state = resolveShizukuState(gateway)
    }

    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }

    DisposableEffect(gateway) {
        val permission = gateway.addPermissionResultListener { _, _ -> refresh() }
        val dead = gateway.addBinderDeadListener { refresh() }
        val received = gateway.addBinderReceivedListenerSticky { refresh() }
        onDispose {
            permission()
            dead()
            received()
        }
    }

    LaunchedEffect(gateway) {
        refresh()
    }

    return state
}

@Composable
fun ShizukuStatusCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val gateway = remember { RikkaShizukuGateway(context) }
    var state by remember { mutableStateOf(resolveShizukuState(gateway)) }

    fun refresh() {
        state = resolveShizukuState(gateway)
    }

    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }

    DisposableEffect(gateway) {
        val permission = gateway.addPermissionResultListener { _, _ -> refresh() }
        val dead = gateway.addBinderDeadListener { refresh() }
        val received = gateway.addBinderReceivedListenerSticky { refresh() }
        onDispose {
            permission()
            dead()
            received()
        }
    }

    val statusText = stringResource(
        when (state) {
            ShizukuState.CHECKING -> R.string.status_checking
            ShizukuState.NOT_INSTALLED -> R.string.status_not_installed
            ShizukuState.NOT_RUNNING -> R.string.status_not_running
            ShizukuState.NOT_GRANTED -> R.string.status_not_granted
            ShizukuState.READY -> R.string.status_ready
        }
    )
    val statusColor = when (state) {
        ShizukuState.READY -> MaterialTheme.colorScheme.primary
        ShizukuState.CHECKING -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.error
    }

    ExpandableCard(
        title = stringResource(R.string.shizuku_card_title),
        subtitle = statusText,
        subtitleColor = statusColor,
        initiallyExpanded = true,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(
                    when (state) {
                        ShizukuState.CHECKING -> R.string.status_checking
                        ShizukuState.NOT_INSTALLED -> R.string.shizuku_hint_install
                        ShizukuState.NOT_RUNNING -> R.string.shizuku_hint_start
                        ShizukuState.NOT_GRANTED -> R.string.shizuku_hint_grant
                        ShizukuState.READY -> R.string.shizuku_hint_ready
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Button(
                onClick = {
                    when (state) {
                        ShizukuState.NOT_INSTALLED -> openShizuku(context, markReturn = false)
                        ShizukuState.NOT_RUNNING -> openShizuku(context, markReturn = true)
                        ShizukuState.NOT_GRANTED ->
                            gateway.requestPermission(ShizukuReturn.PERMISSION_REQUEST_CODE)

                        ShizukuState.CHECKING, ShizukuState.READY -> refresh()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        when (state) {
                            ShizukuState.NOT_INSTALLED -> R.string.shizuku_install
                            ShizukuState.NOT_RUNNING -> R.string.shizuku_start
                            ShizukuState.NOT_GRANTED -> R.string.shizuku_grant
                            ShizukuState.CHECKING, ShizukuState.READY -> R.string.shizuku_retry
                        }
                    )
                )
            }
        }
    }
}

private fun openShizuku(context: Context, markReturn: Boolean) {
    val launch = context.packageManager
        .getLaunchIntentForPackage(RikkaShizukuGateway.SHIZUKU_PACKAGE)
    val intent = launch ?: Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://shizuku.rikka.app/download/")
    )
    if (markReturn && launch != null) {
        ShizukuReturn.markLaunched()
    }
    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        if (markReturn) ShizukuReturn.cancel()
    }
}
