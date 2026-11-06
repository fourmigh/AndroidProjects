package org.caojun.ai.translate

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.caojun.ai.translate.api.ITranslateCallback
import org.caojun.ai.translate.api.ITranslateService
import org.caojun.ai.translate.api.TranslateApi

class MainActivity : ComponentActivity() {

    private var service: ITranslateService? = null
    private var connected by mutableStateOf(false)
    private var status by mutableStateOf("未连接")
    private var busy by mutableStateOf(false)
    private var input by mutableStateOf("Hello, world!")
    private var output by mutableStateOf("")
    private var from by mutableStateOf(TranslateApi.LANG_EN)
    private var to by mutableStateOf(TranslateApi.LANG_ZH)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder?.let { ITranslateService.Stub.asInterface(it) }
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
                            text = "离线翻译服务",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "包名：${TranslateApi.PACKAGE}",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("服务状态：$status")
                                Text("API 版本：${TranslateApi.API_VERSION}")
                                Text("当前方向：$from -> $to")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        from = TranslateApi.LANG_ZH
                                        to = TranslateApi.LANG_EN
                                    }) { Text("中文→英文") }
                                    OutlinedButton(onClick = {
                                        from = TranslateApi.LANG_EN
                                        to = TranslateApi.LANG_ZH
                                    }) { Text("英文→中文") }
                                }
                                Button(
                                    enabled = connected,
                                    onClick = {
                                        service?.preload(from, to)
                                        status = "已请求预加载 $from -> $to（首次约 3-8 秒）"
                                    }
                                ) {
                                    Text("预加载当前方向")
                                }
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("翻译测试")
                                OutlinedTextField(
                                    value = input,
                                    onValueChange = { input = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("源文本") }
                                )
                                Button(
                                    enabled = connected && !busy && input.isNotBlank(),
                                    onClick = { translate() }
                                ) {
                                    Text(if (busy) "翻译中..." else "翻译")
                                }
                                if (output.isNotEmpty()) {
                                    Text("结果：", style = MaterialTheme.typography.labelLarge)
                                    Text(output, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        Text(
                            text = "其他 App 通过 ITranslateService（AIDL）调用本服务，" +
                                "任何声明了 ${TranslateApi.PERMISSION} 权限的应用都能绑定。",
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
            Intent(this, TranslateService::class.java),
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

    private fun translate() {
        val current = service ?: return
        busy = true
        output = ""
        status = "翻译中..."
        runCatching {
            current.translate(input, from, to, object : ITranslateCallback.Stub() {
                override fun onSuccess(translatedText: String) {
                    runOnUiThread {
                        output = translatedText
                        status = "翻译完成"
                        busy = false
                    }
                }

                override fun onError(code: Int, message: String?) {
                    runOnUiThread {
                        status = "翻译失败（$code）：$message"
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
