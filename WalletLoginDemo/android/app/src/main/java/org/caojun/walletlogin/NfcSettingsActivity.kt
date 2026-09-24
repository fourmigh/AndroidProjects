package org.caojun.walletlogin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/**
 * NFC 安全设置：开关各项加固功能。配置存于服务端，双方共用。
 * 顶部标题/说明与底部状态/保存按钮固定，中间开关列表可滚动。
 */
class NfcSettingsActivity : ComponentActivity() {

    private val statusState = mutableStateOf("正在加载…")
    private val busyState = mutableStateOf(false)
    private val configState = mutableStateOf<JSONObject?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadConfig()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val cfg = configState.value
                    if (cfg == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(statusState.value)
                        }
                    } else {
                        SettingsScreen(
                            initial = cfg,
                            status = statusState.value,
                            busy = busyState.value,
                            onSave = { save(it) }
                        )
                    }
                }
            }
        }
    }

    private fun loadConfig() {
        statusState.value = "正在加载…"
        Thread {
            val result = runCatching { ApiClient.getConfig() }
            runOnUiThread {
                result.fold(
                    onSuccess = { configState.value = it; statusState.value = "" },
                    onFailure = { statusState.value = "加载失败：${it.message}" }
                )
            }
        }.start()
    }

    private fun save(config: JSONObject) {
        busyState.value = true
        statusState.value = "正在保存…"
        Thread {
            val result = runCatching { ApiClient.setConfig(config) }
            runOnUiThread {
                busyState.value = false
                result.fold(
                    onSuccess = {
                        configState.value = it
                        statusState.value = "已保存（切换签名方式 / ID 类型需重新绑定才生效）"
                    },
                    onFailure = { statusState.value = "保存失败：${it.message}" }
                )
            }
        }.start()
    }
}

@Composable
private fun SettingsScreen(
    initial: JSONObject,
    status: String,
    busy: Boolean,
    onSave: (JSONObject) -> Unit
) {
    var serverChallenge by remember { mutableStateOf(initial.optBoolean("serverChallenge")) }
    var readerAuth by remember { mutableStateOf(initial.optBoolean("readerAuth")) }
    var useEcdsa by remember { mutableStateOf(initial.optBoolean("useEcdsa")) }
    var useOpaqueId by remember { mutableStateOf(initial.optBoolean("useOpaqueId")) }
    var shortTimeout by remember { mutableStateOf(initial.optBoolean("shortTimeout")) }
    var shortTimeoutMs by remember { mutableStateOf(initial.optInt("shortTimeoutMs", 2000).toString()) }
    var sessionTtl by remember { mutableStateOf(initial.optInt("sessionTtlMs", 0).toString()) }
    var challengeTtl by remember { mutableStateOf(initial.optInt("challengeTtlMs", 10000).toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        // 固定顶部
        Text("NFC 安全设置", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text("配置保存在服务端，客户端与服务端共用同一份。", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        HorizontalDivider()

        // 中间可滚动
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            ToggleRow(
                "后端下发 challenge",
                "由服务端生成随机数并要求读卡器先申请；后端校验其合法、未用、未过期，防止读卡器自造或截获重放。",
                serverChallenge
            ) { serverChallenge = it }

            ToggleRow(
                "读卡器认证",
                "读卡器调用后端时须携带共享口令（X-Reader-Token），防止未授权设备调用验证接口。",
                readerAuth
            ) { readerAuth = it }

            ToggleRow(
                "非对称签名 (ECDSA)",
                "手机用 Keystore 私钥对 challenge 签名、服务端用公钥验签；私钥不可导出，即使被 root 也难以提取。关闭则用对称 HMAC。",
                useEcdsa
            ) { useEcdsa = it }

            ToggleRow(
                "不透明 ID",
                "手机响应传随机 opaqueId 而非明文 userId，减少用户标识泄露。",
                useOpaqueId
            ) { useOpaqueId = it }

            ToggleRow(
                "防中继短超时",
                "读卡器与手机通信超时缩短，中继攻击会因延迟超时而失败。",
                shortTimeout
            ) { shortTimeout = it }

            NumberRow(
                "短超时 (ms)",
                "启用「防中继短超时」时使用的读卡器通信超时（默认 2000ms，越小越严格但可能误超时）。",
                shortTimeoutMs
            ) { shortTimeoutMs = it }

            NumberRow(
                "会话有效期 (秒)",
                "大于 0 时验证成功会签发带过期的会话 token；0 表示不管理会话（关闭此加固）。",
                sessionTtl
            ) { sessionTtl = it }

            NumberRow(
                "challenge 有效期 (ms)",
                "后端下发 challenge 的有效期，越短越安全（仅「后端下发 challenge」开启时生效）。",
                challengeTtl
            ) { challengeTtl = it }
        }

        // 固定底部
        HorizontalDivider()
        if (status.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val cfg = JSONObject()
                    .put("serverChallenge", serverChallenge)
                    .put("readerAuth", readerAuth)
                    .put("useEcdsa", useEcdsa)
                    .put("useOpaqueId", useOpaqueId)
                    .put("shortTimeout", shortTimeout)
                    .put("shortTimeoutMs", shortTimeoutMs.toIntOrNull() ?: 2000)
                    .put("sessionTtlMs", sessionTtl.toIntOrNull() ?: 0)
                    .put("challengeTtlMs", challengeTtl.toIntOrNull() ?: 10000)
                onSave(cfg)
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存")
        }
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Switch(checked = checked, onCheckedChange = onChange)
        }
        Text(desc, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NumberRow(title: String, desc: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(title) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text(desc, style = MaterialTheme.typography.bodySmall)
    }
}
