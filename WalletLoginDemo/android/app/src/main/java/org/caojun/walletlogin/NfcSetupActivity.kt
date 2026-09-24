package org.caojun.walletlogin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 绑定 / 解绑 NFC 登录。绑定按服务端当前配置生成凭证：
 * - ECDSA 模式：本机生成 Keystore 密钥对，上传公钥
 * - HMAC 模式：向后端领取对称密钥
 */
class NfcSetupActivity : ComponentActivity() {

    private val userIdState = mutableStateOf("user001")
    private val statusState = mutableStateOf("")
    private val boundState = mutableStateOf(false)
    private val busyState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshStatus()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SetupScreen(
                        userId = userIdState.value,
                        status = statusState.value,
                        bound = boundState.value,
                        busy = busyState.value,
                        onUserIdChange = { userIdState.value = it },
                        onBind = { bind(userIdState.value.trim()) },
                        onUnbind = { unbind() }
                    )
                }
            }
        }
    }

    private fun refreshStatus() {
        boundState.value = NfcKeyStore.isBound(this)
        statusState.value = if (boundState.value) {
            val mode = NfcKeyStore.getMode(this)
            val idType = NfcKeyStore.getIdType(this)
            val opaque = NfcKeyStore.getOpaqueId(this)
            buildString {
                append("本机已绑定用户：${NfcKeyStore.getUserId(this@NfcSetupActivity)}")
                append("\n模式：$mode，ID 类型：$idType")
                if (!opaque.isNullOrBlank()) append("\nopaqueId：$opaque")
            }
        } else {
            "尚未绑定 NFC，请输入用户 ID 后点击绑定"
        }
    }

    private fun bind(userId: String) {
        if (userId.isBlank()) {
            statusState.value = "请填写用户 ID"
            return
        }
        busyState.value = true
        statusState.value = "正在读取配置…"
        Thread {
            try {
                val cfg = ApiClient.getConfig()
                val useEcdsa = cfg.optBoolean("useEcdsa")
                statusState.value = if (useEcdsa) "正在生成密钥并绑定（ECDSA）…" else "正在绑定（HMAC）…"

                val publicKey = if (useEcdsa) NfcKeyStore.getEcPublicKeyBase64() else null
                val result = ApiClient.bindNfc(userId, publicKey)
                NfcKeyStore.save(
                    this,
                    userId,
                    result.mode,
                    result.idType,
                    result.opaqueId,
                    result.nfcKey
                )
                runOnUiThread {
                    busyState.value = false
                    refreshStatus()
                    statusState.value = "绑定成功（模式=${result.mode}，ID=${result.idType}）"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    busyState.value = false
                    statusState.value = "绑定失败：${e.message}"
                }
            }
        }.start()
    }

    private fun unbind() {
        val userId = NfcKeyStore.getUserId(this)
        busyState.value = true
        statusState.value = "正在解绑…"
        Thread {
            if (!userId.isNullOrBlank()) {
                runCatching { ApiClient.unbindNfc(userId) }
            }
            NfcKeyStore.clear(this)
            runOnUiThread {
                busyState.value = false
                refreshStatus()
                statusState.value = "已解绑"
            }
        }.start()
    }
}

@Composable
private fun SetupScreen(
    userId: String,
    status: String,
    bound: Boolean,
    busy: Boolean,
    onUserIdChange: (String) -> Unit,
    onBind: () -> Unit,
    onUnbind: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("绑定 NFC 登录", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "绑定后，本机靠近 NFC 读卡器即可完成登录（挑战-响应）",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = userId,
            onValueChange = onUserIdChange,
            label = { Text("用户 ID") },
            singleLine = true,
            enabled = !bound,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        if (bound) {
            OutlinedButton(
                onClick = onUnbind,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("解绑 NFC")
            }
        } else {
            Button(
                onClick = onBind,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("绑定 NFC 密钥")
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Text(
            text = "碰不到读卡器？请检查系统「默认付款应用」是否占用了 NFC。" +
                "部分手机的「卡包/钱包」会占用读卡模式（Reader Mode），导致本机无法被读卡器读取。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
