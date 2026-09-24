package org.caojun.walletlogin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MockWalletActivity : ComponentActivity() {

    companion object {
        const val EXTRA_USER_ID = "userId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialUser = intent.getStringExtra(EXTRA_USER_ID)?.ifBlank { null } ?: "user001"
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MockWalletScreen(initialUserId = initialUser)
                }
            }
        }
    }
}

@Composable
private fun MockWalletScreen(initialUserId: String) {
    val scope = rememberCoroutineScope()
    var userId by remember { mutableStateOf(initialUserId) }
    var activeUserId by remember { mutableStateOf(initialUserId) }
    var code by remember { mutableStateOf("") }
    var remaining by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("正在获取动态码…") }
    var refreshTick by remember { mutableStateOf(0) }

    // 自动刷新动态码：按剩余秒数倒计时，归零后重新向后端获取
    LaunchedEffect(activeUserId, refreshTick) {
        while (true) {
            val fetched = withContext(Dispatchers.IO) {
                runCatching { ApiClient.fetchMockCode(activeUserId) }.getOrNull()
            }
            if (fetched == null) {
                code = ""
                remaining = 0
                status = "获取动态码失败，请检查后端是否已启动、用户 ID 是否正确"
                delay(3000)
                continue
            }
            code = fetched.code
            remaining = fetched.expiresInSeconds
            status = "动态码已更新（模拟模式）"
            while (remaining > 0) {
                delay(1000)
                remaining -= 1
            }
        }
    }

    val qrBitmap = remember(code) {
        if (code.isBlank()) {
            null
        } else {
            runCatching {
                BarcodeEncoder().encodeBitmap(code, BarcodeFormat.QR_CODE, 640, 640)
            }.getOrNull()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text("模拟钱包（无需 Google 凭据）", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "模拟模式无法加入 Google Wallet，这里直接渲染动态码，登录校验与真实流程一致",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = userId,
            onValueChange = { userId = it },
            label = { Text("用户 ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                activeUserId = userId.trim().ifBlank { "user001" }
                refreshTick += 1
                status = "正在获取动态码…"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("切换用户 / 刷新")
        }
        Spacer(Modifier.height(16.dp))
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "动态二维码",
                modifier = Modifier.size(240.dp)
            )
        } else {
            Text("（暂无二维码）", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = code.ifBlank { "—" },
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text("剩余 ${remaining}s", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val current = code
                if (current.isBlank()) {
                    status = "动态码为空，无法登录"
                } else {
                    scope.launch {
                        status = "正在验证动态码…"
                        val result = withContext(Dispatchers.IO) {
                            runCatching { ApiClient.login(current) }
                        }
                        status = result.fold(
                            onSuccess = { "模拟登录成功：$it" },
                            onFailure = { "模拟登录失败：${it.message}" }
                        )
                    }
                }
            },
            enabled = code.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("一键模拟登录")
        }
        Spacer(Modifier.height(12.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}
