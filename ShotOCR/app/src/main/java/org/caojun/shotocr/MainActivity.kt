package org.caojun.shotocr

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import org.caojun.shotocr.classification.repository.CategoryRepository
import org.caojun.shotocr.parser.ui.ParserConfigActivity
import org.caojun.shotocr.ui.theme.ShotOCRTheme
import org.caojun.shotocr.util.ForegroundAppResolver

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ShotOCR/MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        enableEdgeToEdge()
        AccountingManager.init(this)
        CategoryRepository.init(this)
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
    var hasFullScreenIntentPermission by remember {
        mutableStateOf(checkFullScreenIntentPermission(context))
    }
    var hasUsageAccess by remember {
        mutableStateOf(ForegroundAppResolver.isUsageAccessGranted(context))
    }
    var serviceRunning by remember { mutableStateOf(isServiceRunning(context)) }
    var principleExpanded by remember { mutableStateOf(false) }

    val usageAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        hasUsageAccess = ForegroundAppResolver.isUsageAccessGranted(context)
        Log.d("ShotOCR/MainActivity", "Usage access result: granted=$hasUsageAccess")
    }

    val fullScreenIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        hasFullScreenIntentPermission = checkFullScreenIntentPermission(context)
        Log.d("ShotOCR/MainActivity", "Full screen intent result: granted=$hasFullScreenIntentPermission")
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

    LaunchedEffect(hasPermission, hasNotificationPermission) {
        if (hasPermission && hasNotificationPermission && !serviceRunning) {
            Log.d("ShotOCR/MainActivity", "Auto-starting service: storage=$hasPermission, notification=$hasNotificationPermission")
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { principleExpanded = !principleExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.principle_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(
                                if (principleExpanded) R.string.principle_collapse else R.string.principle_expand
                            ),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    AnimatedVisibility(visible = principleExpanded) {
                        Column {
                            Text(
                                text = stringResource(R.string.principle_point_1),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                            Text(
                                text = stringResource(R.string.principle_point_2),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = stringResource(R.string.principle_point_3),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = stringResource(R.string.principle_point_4),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = stringResource(R.string.principle_point_5),
                                fontSize = 14.sp
                            )
                        }
                    }
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

            if (!hasPermission || !hasNotificationPermission) {
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
                    }
                }
            }

            if (!hasUsageAccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.usage_access_permission_desc),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Button(
                            onClick = {
                                usageAccessLauncher.launch(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                )
                            }
                        ) {
                            Text(stringResource(R.string.grant_usage_access_permission))
                        }
                    }
                }
            }

            if (!hasFullScreenIntentPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.full_screen_intent_permission_desc),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    fullScreenIntentLauncher.launch(intent)
                                }
                            }
                        ) {
                            Text(stringResource(R.string.grant_full_screen_intent_permission))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        context.startActivity(Intent(context, BillListActivity::class.java))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.view_records))
                }

                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(context, ClassificationManagementActivity::class.java))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(org.caojun.shotocr.classification.R.string.category_entry))
                }

                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(context, ParserConfigActivity::class.java))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.parser_config))
                }
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

private fun checkFullScreenIntentPermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
    val notificationManager =
        context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
    return notificationManager.canUseFullScreenIntent()
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
