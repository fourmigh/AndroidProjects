package org.caojun.walletlogin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class ScanLoginActivity : ComponentActivity() {

    private val resultState = mutableStateOf("点击下方按钮扫描 Google Wallet 中的动态二维码")
    private val busyState = mutableStateOf(false)

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val code = result.contents
        if (code.isNullOrBlank()) {
            resultState.value = "已取消扫码"
        } else {
            login(code)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScanScreen(
                        message = resultState.value,
                        busy = busyState.value,
                        onScan = { startScan() }
                    )
                }
            }
        }
    }

    private fun startScan() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("扫描 Google Wallet 中的动态二维码")
            setBeepEnabled(false)
            setOrientationLocked(false)
        }
        scanLauncher.launch(options)
    }

    private fun login(code: String) {
        busyState.value = true
        resultState.value = "正在验证动态码…"
        Thread {
            try {
                val user = ApiClient.login(code)
                runOnUiThread {
                    busyState.value = false
                    resultState.value = "登录成功：$user"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    busyState.value = false
                    resultState.value = "登录失败：${e.message}"
                }
            }
        }.start()
    }
}

@Composable
private fun ScanScreen(message: String, busy: Boolean, onScan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("扫码登录", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onScan,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("打开相机扫码")
        }
    }
}
