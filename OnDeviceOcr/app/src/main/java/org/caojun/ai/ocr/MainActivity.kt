package org.caojun.ai.ocr

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.caojun.ai.ocr.api.IOcrCallback
import org.caojun.ai.ocr.api.IOcrService
import org.caojun.ai.ocr.api.OcrApi
import org.caojun.ai.ocr.api.OcrLine
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private var service: IOcrService? = null
    private var connected by mutableStateOf(false)
    private var ready by mutableStateOf(false)
    private var status by mutableStateOf("未连接")
    private var result by mutableStateOf("")

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder?.let { IOcrService.Stub.asInterface(it) }
            connected = service != null
            ready = runCatching { service?.isReady() == true }.getOrDefault(false)
            status = if (ready) "模型已加载" else "服务已连接，模型未加载"
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            connected = false
            ready = false
            status = "服务已断开"
        }
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { recognize(it) }
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
                            text = "OCR 识别服务",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "包名：${OcrApi.PACKAGE}",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("服务状态：$status")
                                Text("API 版本：${OcrApi.API_VERSION}")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = connected,
                                        onClick = {
                                            status = "正在加载模型..."
                                            service?.preload()
                                            status = "已请求预加载，首次加载需要 1-2 秒"
                                        }
                                    ) {
                                        Text("预加载模型")
                                    }
                                    OutlinedButton(
                                        enabled = connected,
                                        onClick = {
                                            val current = service ?: return@OutlinedButton
                                            ready = runCatching { current.isReady() }.getOrDefault(false)
                                            status = if (ready) "模型已加载" else "模型未加载"
                                        }
                                    ) {
                                        Text("刷新状态")
                                    }
                                }
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("识别测试")
                                Button(
                                    enabled = connected,
                                    onClick = { pickImage.launch("image/*") }
                                ) {
                                    Text("选择图片并识别")
                                }
                                if (result.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(result, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        Text(
                            text = "其他 App 通过 IOcrService（AIDL）调用本服务，" +
                                "任何声明了 ${OcrApi.PERMISSION} 权限的应用都能绑定。",
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
            Intent(this, OcrService::class.java),
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

    private fun recognize(uri: Uri) {
        val current = service ?: run {
            status = "服务未连接"
            return
        }

        val bitmap = runCatching {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: run {
            status = "无法读取图片"
            return
        }

        val file = File.createTempFile("ui-ocr-", ".png", cacheDir)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        status = "识别中..."
        result = ""
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

        runCatching {
            current.recognize(pfd, OcrApi.FORMAT_PNG, object : IOcrCallback.Stub() {
                override fun onSuccess(resultJson: String) {
                    val lines = OcrLine.listFromJson(resultJson)
                    runOnUiThread {
                        ready = true
                        status = "识别完成，共 ${lines.size} 行"
                        result = lines.joinToString("\n") { it.text }
                    }
                    closeQuietly(pfd, file)
                }

                override fun onError(code: Int, message: String?) {
                    runOnUiThread {
                        status = "识别失败（$code）：$message"
                    }
                    closeQuietly(pfd, file)
                }
            })
        }.onFailure {
            status = "调用失败：${it.message}"
            closeQuietly(pfd, file)
        }
    }

    private fun closeQuietly(pfd: ParcelFileDescriptor, file: File) {
        runCatching { pfd.close() }
        runCatching { file.delete() }
    }
}
