package org.caojun.shotocr

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.caojun.floatingbutton.FloatingButtonController
import org.caojun.shotocr.capture.CaptureMode
import org.caojun.shotocr.capture.CaptureModeManager
import org.caojun.shotocr.capture.CaptureModeStore
import org.caojun.shotocr.capture.CapturePreference
import org.caojun.shotocr.shizuku.ShizukuReturn
import org.caojun.shizuku.RikkaShizukuGateway
import org.caojun.shotocr.capture.applyCaptureMode

@Composable
fun CaptureModeCard(
    modifier: Modifier = Modifier,
    onModeChanged: (CaptureMode) -> Unit = {}
) {
    val context = LocalContext.current
    val modeManager = remember { CaptureModeStore.create(context) }
    val gateway = remember { RikkaShizukuGateway(context) }
    val shizukuLabel = stringResource(R.string.capture_mode_shizuku)
    val fallbackLabel = stringResource(R.string.capture_mode_fallback)

    var preference by remember { mutableStateOf(modeManager.preference()) }
    var mode by remember { mutableStateOf(modeManager.resolve()) }
    var shizukuInstalled by remember { mutableStateOf(gateway.isInstalled()) }
    var shizukuRunning by remember { mutableStateOf(gateway.isRunning()) }
    var shizukuGranted by remember { mutableStateOf(gateway.isPermissionGranted()) }
    var floatingEnabled by remember { mutableStateOf(FloatingButtonController.isEnabled(context)) }
    var floatingPermission by remember {
        mutableStateOf(FloatingButtonController.isPermissionGranted(context))
    }
    var pendingFloatingEnable by remember { mutableStateOf(false) }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        pendingFloatingEnable = false
        val granted = FloatingButtonController.isPermissionGranted(context)
        floatingPermission = granted
        floatingEnabled = granted
        FloatingButtonController.setEnabled(context, granted)
        applyCaptureMode(context)
    }

    fun refresh() {
        val resolved = modeManager.resolve()
        mode = resolved
        onModeChanged(resolved)
        shizukuInstalled = gateway.isInstalled()
        shizukuRunning = gateway.isRunning()
        shizukuGranted = gateway.isPermissionGranted()

        floatingPermission = FloatingButtonController.isPermissionGranted(context)
        floatingEnabled = FloatingButtonController.isEnabled(context)
        if (pendingFloatingEnable && floatingPermission) {
            FloatingButtonController.setEnabled(context, true)
            floatingEnabled = true
            pendingFloatingEnable = false
        }
        if (floatingEnabled && !floatingPermission) {
            FloatingButtonController.setEnabled(context, false)
            floatingEnabled = false
        }

        applyCaptureMode(context)
    }

    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }

    fun select(pref: CapturePreference) {
        modeManager.setPreference(pref)
        preference = pref
        refresh()
    }

    DisposableEffect(Unit) {
        val removePermission = gateway.addPermissionResultListener { _, granted ->
            modeManager.onPermissionResult(granted)
            refresh()
        }
        val removeDead = gateway.addBinderDeadListener { refresh() }
        val removeReceived = gateway.addBinderReceivedListenerSticky {
            refresh()
        }
        onDispose {
            removePermission()
            removeDead()
            removeReceived()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.capture_mode_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = when (mode) {
                    CaptureMode.SHIZUKU ->
                        stringResource(R.string.capture_mode_status_shizuku, shizukuLabel)
                    CaptureMode.FALLBACK ->
                        stringResource(R.string.capture_mode_status_fallback, fallbackLabel)
                    CaptureMode.UNAVAILABLE ->
                        stringResource(R.string.capture_mode_status_unavailable)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (mode == CaptureMode.UNAVAILABLE)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = preference == CapturePreference.SHIZUKU,
                    onClick = { select(CapturePreference.SHIZUKU) },
                    label = { Text(shizukuLabel) }
                )
                FilterChip(
                    selected = preference == CapturePreference.FALLBACK,
                    onClick = { select(CapturePreference.FALLBACK) },
                    label = { Text(fallbackLabel) }
                )
            }

            if (mode == CaptureMode.SHIZUKU) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.floating_button_title),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(
                                if (floatingPermission) R.string.floating_button_hint
                                else R.string.floating_button_hint_permission
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = floatingEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (FloatingButtonController.isPermissionGranted(context)) {
                                    floatingPermission = true
                                    floatingEnabled = true
                                    FloatingButtonController.setEnabled(context, true)
                                    applyCaptureMode(context)
                                } else {
                                    pendingFloatingEnable = true
                                    overlayPermissionLauncher.launch(
                                        FloatingButtonController.permissionIntent(context)
                                    )
                                }
                            } else {
                                pendingFloatingEnable = false
                                floatingEnabled = false
                                FloatingButtonController.setEnabled(context, false)
                                applyCaptureMode(context)
                            }
                        }
                    )
                }
            }

            if (preference == CapturePreference.FALLBACK) {
                Text(
                    text = stringResource(R.string.capture_mode_hint_fallback, fallbackLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (preference == CapturePreference.SHIZUKU && mode == CaptureMode.UNAVAILABLE) {
                val needsInstall = !shizukuInstalled && !shizukuRunning
                val needsStart = shizukuInstalled && !shizukuRunning
                Text(
                    text = stringResource(
                        when {
                            needsInstall -> R.string.capture_mode_hint_install
                            needsStart -> R.string.capture_mode_hint_start
                            !shizukuGranted -> R.string.capture_mode_hint_grant
                            else -> R.string.capture_mode_hint_retry
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Button(onClick = {
                    when {
                        needsInstall -> openShizukuDownload(context)
                        needsStart -> openShizukuApp(context)
                        !shizukuGranted -> gateway.requestPermission(CaptureModeManager.PERMISSION_REQUEST_CODE)
                        else -> refresh()
                    }
                }) {
                    Text(
                        stringResource(
                            when {
                                needsInstall -> R.string.capture_mode_install
                                needsStart -> R.string.capture_mode_start
                                !shizukuGranted -> R.string.capture_mode_grant
                                else -> R.string.capture_mode_retry
                            }
                        )
                    )
                }
            }
        }
    }
}

private fun openShizukuApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(RikkaShizukuGateway.SHIZUKU_PACKAGE)
    if (intent == null) {
        openShizukuDownload(context)
        return
    }
    ShizukuReturn.markLaunched()
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        ShizukuReturn.cancel()
        openShizukuDownload(context)
    }
}

private fun openShizukuDownload(context: Context) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}
