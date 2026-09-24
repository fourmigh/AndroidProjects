package org.caojun.walletlogin

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Reader Mode 测试页：读取 HCE 出示方的响应并向后端验证。
 * 显示完整链路日志（读到什么 / 发了什么 / 服务端返回什么 / 判定）。
 * 顶部提供「设置」入口，底部提供服务端已绑定用户列表与解绑。
 */
class NfcReaderActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private val statusState = mutableStateOf("等待手机靠近（HCE 出示方）…")
    private val logState = mutableStateOf<List<String>>(emptyList())
    private val bindingsState = mutableStateOf<List<ApiClient.NfcBinding>>(emptyList())
    private val listStatusState = mutableStateOf("正在加载…")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            statusState.value = "本机不支持 NFC"
        }
        loadBindings()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReaderScreen(
                        status = statusState.value,
                        logs = logState.value,
                        bindings = bindingsState.value,
                        listStatus = listStatusState.value,
                        onOpenSettings = { startActivity(Intent(this, NfcSettingsActivity::class.java)) },
                        onRefresh = { loadBindings() },
                        onUnbind = { unbind(it) }
                    )
                }
            }
        }
    }

    private fun loadBindings() {
        listStatusState.value = "正在加载…"
        Thread {
            val result = runCatching { ApiClient.listNfcBindings() }
            runOnUiThread {
                result.fold(
                    onSuccess = {
                        bindingsState.value = it
                        listStatusState.value = if (it.isEmpty()) "暂无绑定用户" else ""
                    },
                    onFailure = { listStatusState.value = "加载失败：${it.message}" }
                )
            }
        }.start()
    }

    private fun unbind(userId: String) {
        listStatusState.value = "正在解绑 $userId…"
        Thread {
            val result = runCatching { ApiClient.unbindNfc(userId) }
            runOnUiThread {
                result.fold(
                    onSuccess = { loadBindings() },
                    onFailure = { listStatusState.value = "解绑失败：${it.message}" }
                )
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        val options = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        }
        nfcAdapter?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            options
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag) {
        setLogs(emptyList())
        val isoDep = IsoDep.get(tag)
        if (isoDep == null) {
            postStatus("检测到标签，但不是 HCE（ISO-DEP）卡")
            addLog("[判定] 失败：读到的标签不含 ISO-DEP 技术")
            return
        }
        postStatus("已检测到设备，正在读取…")
        Thread {
            try {
                val cfg = runCatching { ApiClient.getConfig() }.getOrNull() ?: JSONObject()
                val serverChallenge = cfg.optBoolean("serverChallenge")
                val shortTimeout = cfg.optBoolean("shortTimeout")
                val shortTimeoutMs = cfg.optInt("shortTimeoutMs", 2000)

                // 1) 获取 challenge
                val challenge: String
                if (serverChallenge) {
                    challenge = ApiClient.getChallenge()
                    addLog("[平板→服务端] POST /api/nfc/challenge → challenge=$challenge")
                } else {
                    challenge = randomHex(16)
                    addLog("[平板] 本地生成 challenge=$challenge")
                }

                // 2) 与手机通信
                isoDep.connect()
                isoDep.timeout = if (shortTimeout) shortTimeoutMs else 3000
                val selectResp = isoDep.transceive(SELECT_AID)
                addLog("[手机→平板] SELECT AID → ${selectResp.toHex()}")

                val apdu = byteArrayOf(0x80.toByte(), 0x10, 0x00, 0x00, 16) + hexToBytes(challenge)
                val response = isoDep.transceive(apdu)
                addLog("[手机→平板] 响应(hex)=${response.toHex()}")

                val parsed = parseResponse(response)
                if (parsed == null) {
                    addLog("[判定] 失败：响应解析失败（对方是否已绑定 NFC？）")
                    postStatus("响应解析失败")
                    return@Thread
                }
                val (id, proof) = parsed
                addLog("[手机→平板] 解析 id=$id, proof(hex)=${proof.toHex()} (${proof.size} 字节)")

                // 3) 提交后端验证
                addLog("[平板→服务端] POST /api/nfc/verify { id=$id, challenge=$challenge, proof=… }")
                val result = ApiClient.verifyNfc(id, challenge, proof.toHex())
                addLog("[服务端→平板] HTTP ${result.httpCode}  ${result.rawJson}")

                if (result.ok) {
                    val extra = result.sessionToken?.let { "，session=$it" } ?: ""
                    addLog("[判定] 成功：${result.userName} (${result.userId})$extra")
                    postStatus("NFC 登录成功：${result.userName} (${result.userId})")
                } else {
                    addLog("[判定] 失败：${result.error}")
                    postStatus("NFC 登录失败：${result.error}")
                }
            } catch (e: Exception) {
                addLog("[异常] ${e.message}")
                postStatus("读取失败：${e.message}")
            } finally {
                runCatching { isoDep.close() }
            }
        }.start()
    }

    private fun parseResponse(resp: ByteArray): Pair<String, ByteArray>? {
        if (resp.size < 4) return null
        val body = resp.copyOfRange(0, resp.size - 2) // 去掉 SW
        val sep = body.indexOf(0x00.toByte())
        if (sep <= 0 || body.size - sep - 1 < 16) return null
        return String(body, 0, sep, Charsets.UTF_8) to body.copyOfRange(sep + 1, body.size)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length / 2
        val out = ByteArray(len)
        for (i in 0 until len) {
            out[i] = ((Character.digit(hex[i * 2], 16) shl 4) +
                Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
        return out
    }

    private fun randomHex(bytes: Int): String {
        val buf = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return buf.toHex()
    }

    private fun postStatus(text: String) {
        runOnUiThread { statusState.value = text }
    }

    private fun addLog(line: String) {
        runOnUiThread { logState.value = logState.value + line }
    }

    private fun setLogs(lines: List<String>) {
        runOnUiThread { logState.value = lines }
    }

    companion object {
        private val SELECT_AID = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x06,
            0xF0.toByte(), 0x4C, 0x4F, 0x47, 0x49, 0x4E, 0x00
        )
    }
}

@Composable
private fun ReaderScreen(
    status: String,
    logs: List<String>,
    bindings: List<ApiClient.NfcBinding>,
    listStatus: String,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onUnbind: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("NFC 读取（测试读卡器）", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onOpenSettings) { Text("设置") }
        }
        Text("让已绑定 NFC 的手机贴到本机背面 NFC 区域", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text("读取详情", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            items(logs) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("服务端已绑定 NFC 的用户", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onRefresh) { Text("刷新") }
        }
        if (listStatus.isNotBlank()) {
            Text(listStatus, style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(bindings) { binding ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.padding(end = 8.dp)) {
                        Text(binding.name, style = MaterialTheme.typography.bodyLarge)
                        Text(binding.userId, style = MaterialTheme.typography.bodySmall)
                        binding.credentials.forEach {
                            Text("· $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(onClick = { onUnbind(binding.userId) }) {
                        Text("解绑")
                    }
                }
            }
        }
    }
}
