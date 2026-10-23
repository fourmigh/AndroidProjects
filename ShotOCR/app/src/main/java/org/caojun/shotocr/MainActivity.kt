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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.caojun.expandablecard.ExpandableCard
import org.caojun.shotocr.accounting.AccountingManager
import org.caojun.shotocr.accounting.CorrectionHooks
import org.caojun.shotocr.accounting.ui.BillListActivity
import org.caojun.shizuku.RikkaShizukuGateway
import org.caojun.shotocr.capture.CaptureMode
import org.caojun.shotocr.capture.CaptureModeManager
import org.caojun.shotocr.capture.CaptureModeStore
import org.caojun.shotocr.shizuku.ShizukuReturn
import org.caojun.shotocr.capture.applyCaptureMode
import org.caojun.shotocr.classification.repository.CategoryRepository
import org.caojun.shotocr.llm.Correction
import org.caojun.shotocr.llm.LlmStores
import org.caojun.shotocr.llm.PlatformDetector
import org.caojun.shotocr.ui.VerticalScrollbar
import org.caojun.shotocr.ui.theme.ShotOCRTheme
import org.caojun.shotocr.util.ForegroundAppResolver

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ShotOCR/MainActivity"

        @Volatile
        var isResumed = false
            private set
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
    }

    override fun onPause() {
        isResumed = false
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        enableEdgeToEdge()
        AccountingManager.init(this)
        CategoryRepository.init(this)
        ShizukuReturn.init(this)
        CorrectionHooks.remember = { rawText, correctedJson ->
            val platformId = PlatformDetector.detect(null, rawText)?.id ?: "generic"
            LlmStores.correctionStore(applicationContext).add(Correction(rawText, correctedJson, platformId))
        }
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

    val captureGateway = remember { RikkaShizukuGateway(context) }
    val captureModeManager = remember { CaptureModeStore.create(context) }
    var captureMode by remember { mutableStateOf(captureModeManager.resolve()) }

    fun syncCaptureState() {
        hasPermission = checkStoragePermission(context)
        applyCaptureMode(context)
        captureMode = captureModeManager.resolve()
        serviceRunning = captureMode == CaptureMode.FALLBACK && hasPermission
    }

    LifecycleResumeEffect(Unit) {
        ShizukuReturn.onAppResumed()
        syncCaptureState()
        if (ShizukuReturn.consumeAutoRequestPermission() &&
            !captureGateway.isPermissionGranted()
        ) {
            captureGateway.requestPermission(CaptureModeManager.PERMISSION_REQUEST_CODE)
        }
        onPauseOrDispose { }
    }

    DisposableEffect(Unit) {
        val removePermission = captureGateway.addPermissionResultListener { _, granted ->
            captureModeManager.onPermissionResult(granted)
            syncCaptureState()
        }
        val removeDead = captureGateway.addBinderDeadListener { syncCaptureState() }
        val removeReceived = captureGateway.addBinderReceivedListenerSticky {
            syncCaptureState()
        }
        onDispose {
            removePermission()
            removeDead()
            removeReceived()
        }
    }

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
        syncCaptureState()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        Log.d("ShotOCR/MainActivity", "Notification permission result: granted=$granted")
        syncCaptureState()
    }

    LaunchedEffect(hasPermission, hasNotificationPermission) {
        syncCaptureState()
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val shizukuMode = captureMode == CaptureMode.SHIZUKU
            val unavailable = captureMode == CaptureMode.UNAVAILABLE
            val statusText = stringResource(
                when {
                    shizukuMode -> R.string.capture_mode_shizuku
                    unavailable -> R.string.service_status_unavailable
                    serviceRunning -> R.string.capture_mode_fallback
                    else -> R.string.service_stopped
                }
            )
            val statusColor = if (shizukuMode || serviceRunning) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_title),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Column(
                    modifier = Modifier.padding(start = 16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = stringResource(R.string.service_status),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = statusText,
                        fontSize = 13.sp,
                        color = statusColor,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {

            ExpandableCard(
                title = stringResource(R.string.principle_title)
            ) {
                Text(
                    text = stringResource(R.string.principle_point_1),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
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
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = stringResource(R.string.principle_point_6),
                    fontSize = 14.sp
                )
            }

            ExpandableCard(
                title = stringResource(R.string.instructions_title)
            ) {
                Text(
                    text = stringResource(
                        R.string.instruction_1,
                        stringResource(R.string.capture_mode_fallback)
                    ),
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

            val needsStoragePermission =
                captureMode == CaptureMode.FALLBACK && !hasPermission
            if (needsStoragePermission || !hasNotificationPermission) {
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
                        if (needsStoragePermission) {
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
                        if (!hasNotificationPermission) {
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

            CaptureModeCard(
                onModeChanged = { newMode ->
                    captureMode = newMode
                    serviceRunning = newMode == CaptureMode.FALLBACK && hasPermission
                }
            )

                }
                VerticalScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(vertical = 4.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Button(
                            onClick = {
                                context.startActivity(Intent(context, BillListActivity::class.java))
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.view_records),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(context, ClassificationManagementActivity::class.java))
                            }
                        ) {
                            Text(
                                text = stringResource(org.caojun.shotocr.classification.R.string.category_entry),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(context, RecognitionSettingsActivity::class.java))
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.recognition_settings),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.powered_by),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
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


