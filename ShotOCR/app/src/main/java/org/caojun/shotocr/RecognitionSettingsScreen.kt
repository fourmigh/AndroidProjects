package org.caojun.shotocr

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.caojun.shotocr.llm.CustomPlatform
import org.caojun.shotocr.llm.LlmReceiptEngine
import org.caojun.shotocr.llm.LlmReceiptParser
import org.caojun.shotocr.llm.LlmStores
import org.caojun.shotocr.llm.Platform
import org.caojun.shotocr.llm.PlatformDetector
import org.caojun.shotocr.llm.SystemPromptProvider

private val PLATFORM_LABELS: Map<Platform, String> = mapOf(
    Platform.ALIPAY to "支付宝",
    Platform.WECHAT to "微信",
    Platform.UNIONPAY to "云闪付",
    Platform.TAOBAO to "淘宝",
    Platform.JD to "京东",
    Platform.DOUYIN to "抖音",
    Platform.GENERIC to "通用"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionSettingsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val promptStore = remember { LlmStores.promptStore(context) }
    val customStore = remember { LlmStores.customPlatformStore(context) }
    val correctionStore = remember { LlmStores.correctionStore(context) }

    var selectedPlatform by remember { mutableStateOf(Platform.GENERIC) }
    var promptText by remember { mutableStateOf(promptStore.systemPrompt(selectedPlatform)) }
    var customized by remember { mutableStateOf(promptStore.isCustomized(selectedPlatform)) }
    var customPlatforms by remember { mutableStateOf(customStore.list()) }
    var corrections by remember { mutableStateOf(correctionStore.list()) }
    var showAddCustom by remember { mutableStateOf(false) }
    var editingCustom by remember { mutableStateOf<CustomPlatform?>(null) }
    var testOutput by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    fun selectPlatform(platform: Platform) {
        selectedPlatform = platform
        promptText = promptStore.systemPrompt(platform)
        customized = promptStore.isCustomized(platform)
    }

    if (showAddCustom) {
        AddCustomPlatformDialog(
            initial = editingCustom,
            onDismiss = {
                showAddCustom = false
                editingCustom = null
            },
            onConfirm = { name, keywords, prompt ->
                val editing = editingCustom
                if (editing != null) {
                    customStore.update(editing.copy(name = name, keywords = keywords, prompt = prompt))
                } else {
                    customStore.add(name, keywords, prompt)
                }
                customPlatforms = customStore.list()
                showAddCustom = false
                editingCustom = null
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recognition_settings)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.recognition_back)) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- 平台提示词 ----
            Text(
                text = stringResource(R.string.recognition_platform_title),
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Platform.entries.forEach { platform ->
                    FilterChip(
                        selected = platform == selectedPlatform,
                        onClick = { selectPlatform(platform) },
                        label = { Text(PLATFORM_LABELS[platform] ?: platform.id) }
                    )
                }
            }
            Text(
                text = stringResource(
                    if (customized) R.string.recognition_prompt_customized
                    else R.string.recognition_prompt_default
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                maxLines = 30
            )
            Text(
                text = stringResource(R.string.recognition_prompt_count, promptText.length),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    promptStore.setSystemPrompt(selectedPlatform, promptText)
                    customized = promptStore.isCustomized(selectedPlatform)
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.recognition_prompt_saved)) }
                }) {
                    Text(stringResource(R.string.recognition_prompt_save))
                }
                OutlinedButton(onClick = {
                    promptStore.reset(selectedPlatform)
                    promptText = promptStore.systemPrompt(selectedPlatform)
                    customized = false
                }) {
                    Text(stringResource(R.string.recognition_prompt_reset))
                }
            }

            Button(
                onClick = {
                    testing = true
                    testOutput = null
                    scope.launch {
                        testOutput = runPromptTest(context, promptStore, customStore, correctionStore)
                        testing = false
                    }
                },
                enabled = !testing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.recognition_prompt_test))
            }
            testOutput?.let { output ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = output,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            HorizontalDivider()

            // ---- 自定义平台 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.recognition_custom_title),
                    style = MaterialTheme.typography.titleSmall
                )
                TextButton(onClick = {
                    editingCustom = null
                    showAddCustom = true
                }) {
                    Text(stringResource(R.string.recognition_custom_add))
                }
            }
            if (customPlatforms.isEmpty()) {
                Text(
                    text = stringResource(R.string.recognition_custom_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                customPlatforms.forEach { platform ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(platform.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = platform.keywords.joinToString("、"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row {
                                TextButton(onClick = {
                                    editingCustom = platform
                                    showAddCustom = true
                                }) {
                                    Text(stringResource(R.string.recognition_custom_edit))
                                }
                                TextButton(onClick = {
                                    customStore.remove(platform.id)
                                    customPlatforms = customStore.list()
                                }) {
                                    Text(stringResource(R.string.recognition_corrections_delete))
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // ---- 纠错示例 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.recognition_corrections_title),
                    style = MaterialTheme.typography.titleSmall
                )
                TextButton(onClick = {
                    correctionStore.clear()
                    corrections = correctionStore.list()
                }) {
                    Text(stringResource(R.string.recognition_corrections_clear))
                }
            }
            if (corrections.isEmpty()) {
                Text(
                    text = stringResource(R.string.recognition_corrections_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                corrections.forEachIndexed { index, correction ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = Platform.fromId(correction.platformId)
                                    ?.let { PLATFORM_LABELS[it] } ?: correction.platformId,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = correction.rawTextSnippet.lineSequence().firstOrNull().orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            Text(
                                text = correction.changesJson,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3
                            )
                            TextButton(onClick = {
                                correctionStore.removeAt(index)
                                corrections = correctionStore.list()
                            }) {
                                Text(stringResource(R.string.recognition_corrections_delete))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCustomPlatformDialog(
    initial: CustomPlatform? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, keywords: List<String>, prompt: String) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var keywords by remember(initial) { mutableStateOf(initial?.keywords?.joinToString(",") ?: "") }
    var prompt by remember(initial) { mutableStateOf(initial?.prompt ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recognition_custom_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.recognition_custom_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text(stringResource(R.string.recognition_custom_keywords)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.recognition_custom_prompt)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    maxLines = 20
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val kw = keywords.split(",", "，", "、", " ")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    onConfirm(name.trim(), kw, prompt)
                },
                enabled = name.isNotBlank() && prompt.isNotBlank()
            ) {
                Text(stringResource(R.string.recognition_prompt_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.recognition_back)) }
        }
    )
}

private const val SAMPLE_OCR_TEXT = """全部账单
上海市闵行中学
-461.38
原价
¥462.00
银行卡立减金优惠¥0.62
支付时间
2026-10-1317:11:55
兴业银行储蓄卡(5719)
支付方式
交易单号
4500000426202610139083205733"""

private suspend fun runPromptTest(
    context: Context,
    promptStore: org.caojun.shotocr.llm.PromptStore,
    customStore: org.caojun.shotocr.llm.CustomPlatformStore,
    correctionStore: org.caojun.shotocr.llm.CorrectionStore
): String {
    return try {
        val detected = PlatformDetector.detect(null, SAMPLE_OCR_TEXT)?.id ?: "generic"
        val provider = SystemPromptProvider(promptStore, customStore, correctionStore)
        val engine = LlmReceiptEngine.get(context)
        withContext(Dispatchers.Default) { engine.initialize() }
        val data = withContext(Dispatchers.Default) {
            LlmReceiptParser(engine, provider).parse(SAMPLE_OCR_TEXT)
        }
        "platform=$detected\namount=${data.amount}, discount=${data.discount}, " +
            "originalAmount=${data.originalAmount}, storeName=${data.storeName}, " +
            "paymentTime=${data.paymentTime}, paymentMethod=${data.paymentMethod}, " +
            "orderNumber=${data.orderNumber}, sourceApp=${data.sourceApp}"
    } catch (e: Exception) {
        "测试失败：${e.message}"
    }
}
