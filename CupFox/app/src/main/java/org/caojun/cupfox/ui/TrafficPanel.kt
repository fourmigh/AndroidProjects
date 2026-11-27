package org.caojun.cupfox.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.caojun.cupfox.R
import org.caojun.cupfox.data.DayTraffic
import org.caojun.cupfox.data.MonthTraffic
import org.caojun.cupfox.data.TrafficStore
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun TrafficPanel() {
    val context = LocalContext.current
    val store = remember { TrafficStore(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasAccess by remember { mutableStateOf(store.hasUsageAccess()) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var data by remember { mutableStateOf<MonthTraffic?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var hasPhonePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_PHONE_STATE,
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var networkLabel by remember {
        mutableStateOf(store.currentNetworkLabel(hasPhonePermission))
    }
    val phonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPhonePermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPhonePermission) {
            phonePermissionLauncher.launch(android.Manifest.permission.READ_PHONE_STATE)
        }
    }

    // 监听网络切换（WiFi / 移动 / VPN），不用手动刷新
    DisposableEffect(hasPhonePermission) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val handler = Handler(Looper.getMainLooper())
        val callback = object : ConnectivityManager.NetworkCallback() {
            private fun refresh() {
                val label = store.currentNetworkLabel(hasPhonePermission)
                handler.post { networkLabel = label }
            }

            override fun onAvailable(network: Network) = refresh()

            override fun onLost(network: Network) = refresh()

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) = refresh()
        }
        try {
            manager?.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) {
            // 没有网络状态权限时忽略
        }
        onDispose {
            handler.removeCallbacksAndMessages(null)
            try {
                manager?.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
            }
        }
    }

    LaunchedEffect(hasPhonePermission) {
        networkLabel = store.currentNetworkLabel(hasPhonePermission)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = store.hasUsageAccess()
                if (granted != hasAccess) {
                    hasAccess = granted
                    refreshKey++
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(month, refreshKey, hasAccess) {
        if (!hasAccess) {
            data = null
            return@LaunchedEffect
        }
        loading = true
        data = withContext(Dispatchers.IO) { store.queryMonth(month) }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryCard(
            networkLabel = networkLabel,
            month = month,
            data = data,
            loading = loading,
        )

        if (!hasAccess) {
            PermissionCard(
                onGrant = { openUsageAccessSettings(context) },
            )
        } else {
            CalendarCard(
                month = month,
                data = data,
                selectedDay = selectedDay,
                onSelectDay = { selectedDay = it },
                onPreviousMonth = {
                    month = month.minusMonths(1)
                    selectedDay = null
                },
                onNextMonth = {
                    if (month < YearMonth.now()) {
                        month = month.plusMonths(1)
                        selectedDay = null
                    }
                },
                onRefresh = {
                    store.clearCache()
                    refreshKey++
                },
            )

            selectedDay?.let { day ->
                DayDetailCard(
                    day = day,
                    traffic = data?.days?.get(day.dayOfMonth),
                )
            }

            Text(
                if (hasPhonePermission) {
                    "流量来自系统统计，4G/5G 合并为移动数据，仅供参考。"
                } else {
                    "流量来自系统统计，4G/5G 合并为移动数据。授予电话权限后，当前网络会显示 4G/5G 制式。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryCard(
    networkLabel: String,
    month: YearMonth,
    data: MonthTraffic?,
    loading: Boolean,
) {
    val today = LocalDate.now()
    val isCurrentMonth = month == YearMonth.from(today)
    val todayTraffic = if (isCurrentMonth) data?.days?.get(today.dayOfMonth) else null

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("当前网络", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = networkLabel,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            Row(modifier = Modifier.fillMaxWidth()) {
                if (isCurrentMonth) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "今日",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("WiFi  ${TrafficStore.formatBytes(todayTraffic?.wifiBytes ?: 0L)}")
                        Text("移动  ${TrafficStore.formatBytes(todayTraffic?.mobileBytes ?: 0L)}")
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "当月合计",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("WiFi  ${TrafficStore.formatBytes(data?.wifiBytes ?: 0L)}")
                    Text("移动  ${TrafficStore.formatBytes(data?.mobileBytes ?: 0L)}")
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(onGrant: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("需要流量统计权限", style = MaterialTheme.typography.titleMedium)
            Text(
                "Android 需要「使用情况访问」权限才能读取本 App 的流量。授权后返回这里会自动加载。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onGrant) { Text("去授权") }
        }
    }
}

@Composable
private fun CalendarCard(
    month: YearMonth,
    data: MonthTraffic?,
    selectedDay: LocalDate?,
    onSelectDay: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onRefresh: () -> Unit,
) {
    val today = LocalDate.now()
    val maxBytes = data?.days?.values?.maxOfOrNull { it.totalBytes } ?: 0L
    val firstOffset = month.atDay(1).dayOfWeek.value % 7
    val daysInMonth = month.lengthOfMonth()
    val rows = (firstOffset + daysInMonth + 6) / 7

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_left),
                        contentDescription = "上个月",
                    )
                }
                Text(
                    text = "${month.year} 年 ${month.monthValue} 月",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(
                    onClick = onNextMonth,
                    enabled = month < YearMonth.now(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = "下个月",
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = "刷新",
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("日", "一", "二", "三", "四", "五", "六").forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            var day = 1
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val cell = row * 7 + col
                        if (cell < firstOffset || day > daysInMonth) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                            )
                        } else {
                            val date = month.atDay(day)
                            val isFuture = date.isAfter(today)
                            DayCell(
                                date = date,
                                traffic = data?.days?.get(day),
                                maxBytes = maxBytes,
                                isToday = date == today,
                                isSelected = selectedDay == date,
                                isFuture = isFuture,
                                onClick = { if (!isFuture) onSelectDay(date) },
                                modifier = Modifier.weight(1f),
                            )
                            day++
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    traffic: DayTraffic?,
    maxBytes: Long,
    isToday: Boolean,
    isSelected: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = traffic?.totalBytes ?: 0L
    val ratio = if (maxBytes > 0L) (total.toFloat() / maxBytes).coerceIn(0f, 1f) else 0f
    val background = if (isFuture) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f + 0.20f * ratio)
    }
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .height(56.dp)
            .padding(2.dp)
            .clip(shape)
            .background(background)
            .then(
                if (isSelected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                }
            )
            .clickable(enabled = !isFuture, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 12.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = if (isFuture) "" else TrafficStore.formatShort(total),
                fontSize = 9.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (isFuture) 0.4f else 1f,
                ),
            )
        }
    }
}

@Composable
private fun DayDetailCard(day: LocalDate, traffic: DayTraffic?) {
    val wifi = traffic?.wifiBytes ?: 0L
    val mobile = traffic?.mobileBytes ?: 0L

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "${day.monthValue} 月 ${day.dayOfMonth} 日",
                style = MaterialTheme.typography.titleMedium,
            )
            DetailRow("WiFi", TrafficStore.formatBytes(wifi))
            DetailRow("移动数据", TrafficStore.formatBytes(mobile))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            DetailRow("合计", TrafficStore.formatBytes(wifi + mobile), bold = true)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            text = value,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private fun openUsageAccessSettings(context: Context) {
    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (_: Exception) {
        }
    }
}
