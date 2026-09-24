package org.caojun.walletlogin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.pay.Pay
import com.google.android.gms.pay.PayApiAvailabilityStatus
import com.google.android.gms.pay.PayClient

class BindWalletActivity : ComponentActivity() {

    private lateinit var walletClient: PayClient
    private val requestCode = 1000

    private val statusState = mutableStateOf("正在检测 Google Wallet 可用性…")
    private val userIdState = mutableStateOf("user001")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        walletClient = Pay.getClient(this)
        checkAvailability()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BindScreen(
                        status = statusState.value,
                        userId = userIdState.value,
                        onUserIdChange = { userIdState.value = it },
                        onBind = { bindPass(userIdState.value.trim()) }
                    )
                }
            }
        }
    }

    private fun checkAvailability() {
        walletClient.getPayApiAvailabilityStatus(PayClient.RequestType.SAVE_PASSES)
            .addOnSuccessListener { status ->
                statusState.value = if (status == PayApiAvailabilityStatus.AVAILABLE) {
                    "Google Wallet 可用，可以绑定卡片"
                } else {
                    "当前设备或账号不可用（可能未加入 Demo 测试白名单）"
                }
            }
            .addOnFailureListener { e ->
                statusState.value = "检测失败：${e.message}"
            }
    }

    private fun bindPass(userId: String) {
        if (userId.isBlank()) {
            toast("请填写用户 ID")
            return
        }
        statusState.value = "正在向后端申请卡片…"
        Thread {
            try {
                val result = ApiClient.bindPass(userId)
                runOnUiThread {
                    if (result.mock) {
                        statusState.value = "当前为模拟模式，无法加入 Google Wallet，正在打开模拟钱包…"
                        startActivity(
                            Intent(this, MockWalletActivity::class.java)
                                .putExtra(MockWalletActivity.EXTRA_USER_ID, userId)
                        )
                    } else {
                        statusState.value = "正在打开 Google Wallet 添加卡片…"
                        walletClient.savePassesJwt(result.jwt!!, this, requestCode)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { statusState.value = "申请失败：${e.message}" }
            }
        }.start()
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != this.requestCode) return
        statusState.value = when (resultCode) {
            Activity.RESULT_OK -> "卡片已加入 Google Wallet"
            Activity.RESULT_CANCELED -> "已取消绑定"
            PayClient.SavePassesResult.SAVE_ERROR ->
                "保存失败：${data?.getStringExtra(PayClient.EXTRA_API_ERROR_MESSAGE)}"
            else -> "未知结果：$resultCode"
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun BindScreen(
    status: String,
    userId: String,
    onUserIdChange: (String) -> Unit,
    onBind: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("绑定登录卡", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = userId,
            onValueChange = onUserIdChange,
            label = { Text("用户 ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBind, modifier = Modifier.fillMaxWidth()) {
            Text("Add to Google Wallet")
        }
        Spacer(Modifier.height(16.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium)
    }
}
