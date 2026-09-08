package org.caojun.shotocr

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.caojun.shotocr.accounting.AccountingManager
import org.caojun.shotocr.accounting.ui.BillListActivity
import org.caojun.shotocr.ui.theme.ShotOCRTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ShotOCR/MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        enableEdgeToEdge()
        AccountingManager.init(this)
        setContent {
            ShotOCRTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(checkStoragePermission(context)) }
    var hasNotificationPermission by remember {
        mutableStateOf(checkNotificationPermission(context))
    }
    var hasOverlayPermission by remember { mutableStateOf(checkOverlayPermission(context)) }
    var serviceRunning by remember { mutableStateOf(isServiceRunning(context)) }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        hasOverlayPermission = checkOverlayPermission(context)
        Log.d("ShotOCR/MainActivity", "Overlay permission result: granted=$hasOverlayPermission")
        if (hasPermission && hasNotificationPermission && hasOverlayPermission && !serviceRunning) {
            startScreenshotService(context)
            serviceRunning = true
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.values.all { it }
        Log.d("ShotOCR/MainActivity", "Storage permissions result: $permissions, granted=$hasPermission")
        if (hasPermission && !serviceRunning) {
            startScreenshotService(context)
            serviceRunning = true
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        Log.d("ShotOCR/MainActivity", "Notification permission result: granted=$granted")
        if (hasPermission && !serviceRunning) {
            startScreenshotService(context)
            serviceRunning = true
        }
    }

    LaunchedEffect(hasPermission, hasNotificationPermission, hasOverlayPermission) {
        if (hasPermission && hasNotificationPermission && hasOverlayPermission && !serviceRunning) {
            Log.d("ShotOCR/MainActivity", "Auto-starting service: storage=$hasPermission, notification=$hasNotificationPermission, overlay=$hasOverlayPermission")
            startScreenshotService(context)
            serviceRunning = true
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(R.string.app_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.service_status),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = stringResource(
                            if (serviceRunning) R.string.service_running else R.string.service_stopped
                        ),
                        fontSize = 14.sp,
                        color = if (serviceRunning)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.instructions_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.instruction_1),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = stringResource(R.string.instruction_2),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = stringResource(R.string.instruction_3),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = stringResource(R.string.instruction_4),
                        fontSize = 14.sp
                    )
                }
            }

            if (!hasPermission || !hasNotificationPermission || !hasOverlayPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.permission_required_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (!hasPermission) {
                            Text(
                                text = stringResource(R.string.permission_required_desc),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Button(
                                onClick = { storagePermissionLauncher.launch(getStoragePermissions()) }
                            ) {
                                Text(stringResource(R.string.grant_permission))
                            }
                        }
                        if (hasPermission && !hasNotificationPermission) {
                            Text(
                                text = stringResource(R.string.notification_permission_desc),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.grant_notification_permission))
                            }
                        }
                        if (hasPermission && hasNotificationPermission && !hasOverlayPermission) {
                            Text(
                                text = stringResource(R.string.overlay_permission_desc),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Button(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    overlayPermissionLauncher.launch(intent)
                                }
                            ) {
                                Text(stringResource(R.string.grant_overlay_permission))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    context.startActivity(Intent(context, BillListActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.view_records))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Powered by ONNX Runtime + OpenCV",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

private fun checkStoragePermission(context: android.content.Context): Boolean {
    val permissions = getStoragePermissions()
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

private fun checkNotificationPermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun checkOverlayPermission(context: android.content.Context): Boolean {
    return Settings.canDrawOverlays(context)
}

private fun getStoragePermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun isServiceRunning(context: android.content.Context): Boolean {
    val manager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as ActivityManager
    @Suppress("DEPRECATION")
    for (service in manager.getRunningServices(Int.MAX_VALUE)) {
        if (ScreenshotService::class.java.name == service.service.className) {
            return true
        }
    }
    return false
}

private fun startScreenshotService(context: android.content.Context) {
    Log.d("ShotOCR/MainActivity", "Starting ScreenshotService")
    val intent = Intent(context, ScreenshotService::class.java)
    context.startForegroundService(intent)
}
