package org.caojun.autoocrtask.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.caojun.autoocrtask.R
import org.caojun.autoocrtask.util.ForegroundApp

@Composable
fun NotificationPermissionCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(checkNotificationPermission(context)) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
    }

    LifecycleResumeEffect(Unit) {
        granted = checkNotificationPermission(context)
        onPauseOrDispose { }
    }

    if (granted) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.permission_notification_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.permission_notification_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            ) {
                Text(stringResource(R.string.permission_grant_notification))
            }
        }
    }
}

@Composable
fun UsageAccessCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ForegroundApp.isUsageAccessGranted(context)) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        granted = ForegroundApp.isUsageAccessGranted(context)
    }

    LifecycleResumeEffect(Unit) {
        granted = ForegroundApp.isUsageAccessGranted(context)
        onPauseOrDispose { }
    }

    if (granted) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.permission_usage_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.permission_usage_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            Button(
                onClick = {
                    launcher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            ) {
                Text(stringResource(R.string.permission_grant_usage))
            }
        }
    }
}

@Composable
fun OverlayPermissionCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        granted = Settings.canDrawOverlays(context)
    }

    LifecycleResumeEffect(Unit) {
        granted = Settings.canDrawOverlays(context)
        onPauseOrDispose { }
    }

    if (granted) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.permission_overlay_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.permission_overlay_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            Button(
                onClick = {
                    launcher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.permission_grant_overlay))
            }
        }
    }
}

private fun checkNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}
