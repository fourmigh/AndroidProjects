package org.caojun.ai.llm

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.caojun.ai.llm.api.ILlmCallback
import org.caojun.ai.llm.api.ILlmService
import org.caojun.ai.llm.api.LlmApi

class MainActivity : ComponentActivity() {

    private var service: ILlmService? = null
    private var connected by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var status by mutableStateOf("未连接")
    private var system by mutableStateOf("")
    private var input by mutableStateOf("提取这段文字里的金额、时间和商户名。")
    private var output by mutableStateOf("")

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder?.let { ILlmService.Stub.asInterface(it) }
            connected = service != null
            status = if (connected) "服务已连接" else "服务连接失败"
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            connected = false
            status = "服务已断开"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "本地大模型服务",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "包名：${LlmApi.PACKAGE}",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("服务状态：$status")
                                Text("API 版本：${LlmApi.API_VERSION}")
                                Button(
                                    enabled = connected,
                                    onClick = {
                                        service?.preload()
                                        status = "已请求预加载，首次加载约 10 秒"
                                    }
                                ) {
                                    Text("预加载模型")
                                }
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("生成测试")
                                OutlinedTextField(
                                    value = system,
                                    onValueChange = { system = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("系统提示（可留空）") },
                                    minLines = 2
                                )
                                OutlinedTextField(
                                    value = input,
                                    onValueChange = { input = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("用户消息") },
                                    minLines = 3
                                )
                                Button(
                                    enabled = connected && !busy && input.isNotBlank(),
                                    onClick = { generate() }
                                ) {
                                    Text(if (busy) "生成中..." else "生成")
                                }
                                if (output.isNotEmpty()) {
                                    Text("结果：", style = MaterialTheme.typography.labelLarge)
                                    Text(output, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        Text(
                            text = "其他 App 通过 ILlmService（AIDL）调用本服务，" +
                                "任何声明了 ${LlmApi.PERMISSION} 权限的应用都能绑定。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        bindService(
            Intent(this, LlmService::class.java),
            connection,
            BIND_AUTO_CREATE
        )
    }

    override fun onStop() {
        unbindService(connection)
        service = null
        connected = false
        super.onStop()
    }

    private fun generate() {
        val current = service ?: return
        busy = true
        output = ""
        status = "生成中..."
        runCatching {
            current.generate(system, input, object : ILlmCallback.Stub() {
                override fun onSuccess(resultText: String) {
                    runOnUiThread {
                        output = resultText
                        status = "生成完成"
                        busy = false
                    }
                }

                override fun onError(code: Int, message: String?) {
                    runOnUiThread {
                        status = "生成失败（$code）：$message"
                        busy = false
                    }
                }
            })
        }.onFailure {
            status = "调用失败：${it.message}"
            busy = false
        }
    }
}
