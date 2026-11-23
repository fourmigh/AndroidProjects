package org.caojun.ai.translate.hub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.caojun.ai.translate.hub.core.CatalogModel
import org.caojun.ai.translate.hub.core.InstallState
import org.caojun.ai.translate.hub.llama.LlamaBridge
import org.caojun.ai.translate.provider.LanguageCatalog

class MainActivity : ComponentActivity() {

    private val viewModel: HubViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialModelId = intent?.data?.lastPathSegment
            ?: intent?.getStringExtra(EXTRA_MODEL_ID)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HubScreen(viewModel, initialModelId)
                }
            }
        }
    }

    private companion object {
        const val EXTRA_MODEL_ID = "modelId"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HubScreen(vm: HubViewModel, highlightModelId: String?) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val states by vm.states.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("离线翻译模型") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = {},
                    label = { Text("模型") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = {},
                    label = { Text("已安装") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = {},
                    label = { Text("翻译测试") },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                0 -> StoreTab(vm, states, highlightModelId)
                1 -> InstalledTab(vm, states)
                else -> TestTab(vm)
            }
        }
    }
}

// ------------------------------------------------------------------ 商店

@Composable
private fun StoreTab(
    vm: HubViewModel,
    states: Map<String, InstallState>,
    highlightModelId: String?,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "默认不内置模型。按机型和使用场景选一个下载，下载后可随时删除。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!LlamaBridge.isAvailable) {
            item {
                Text(
                    text = "当前设备不支持推理（本工程只带 arm64-v8a 的 native 库）。",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        items(vm.catalog, key = { it.id }) { model ->
            ModelCard(
                vm = vm,
                model = model,
                state = vm.resolveState(model, states),
                highlighted = model.id == highlightModelId,
            )
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "除粤语外的汉语方言（吴语/上海话、闽南语、客家话、四川话等）目前没有可用的开源文本翻译模型。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ModelCard(
    vm: HubViewModel,
    model: CatalogModel,
    state: InstallState,
    highlighted: Boolean,
) {
    var showLicense by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (model.recommended) Tag("推荐", MaterialTheme.colorScheme.primary)
                if (highlighted) Tag("来自客户端", MaterialTheme.colorScheme.tertiary)
            }

            Text("${model.vendor} · ${model.params} · ${model.summary}", style = MaterialTheme.typography.bodySmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(formatBytes(model.sizeBytes), MaterialTheme.colorScheme.secondary)
                Tag("${model.langs.size} 语种", MaterialTheme.colorScheme.secondary)
                Tag("${model.minRamMb / 1024} GB 内存", MaterialTheme.colorScheme.secondary)
                if (model.dialects.isNotEmpty()) Tag("含方言/民汉", MaterialTheme.colorScheme.tertiary)
            }

            Text("许可：${model.license.name}", style = MaterialTheme.typography.bodySmall)

            when (state) {
                is InstallState.Ready -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "已安装",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    if (vm.defaultModelId == model.id) {
                        Text("默认模型", style = MaterialTheme.typography.bodySmall)
                    } else {
                        TextButton(onClick = { vm.setDefault(model.id) }) { Text("设为默认") }
                    }
                }

                is InstallState.Downloading -> {
                    val total = if (state.total > 0) state.total else model.sizeBytes
                    val progress = if (total > 0) {
                        (state.bytes.toFloat() / total).coerceIn(0f, 1f)
                    } else 0f
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${formatBytes(state.bytes)} / ${formatBytes(total)}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.cancelInstall(model) }) { Text("取消") }
                    }
                }

                is InstallState.Verifying -> Text("校验中…")

                is InstallState.Failed -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { vm.install(model) }) { Text("重试") }
                }

                else -> {
                    Button(
                        enabled = model.available,
                        onClick = {
                            // 许可不做持久化：每次下载都重新提示
                            if (model.license.requiresAccept) {
                                showLicense = true
                            } else {
                                vm.install(model)
                            }
                        },
                    ) { Text(if (model.available) "下载 ${formatBytes(model.sizeBytes)}" else "暂不可用") }
                }
            }
        }
    }

    if (showLicense) {
        AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text("确认许可") },
            text = { Text("${model.license.name}\n\n${model.license.summary}") },
            confirmButton = {
                TextButton(onClick = {
                    showLicense = false
                    vm.install(model)
                }) { Text("同意并下载") }
            },
            dismissButton = {
                TextButton(onClick = { showLicense = false }) { Text("取消") }
            },
        )
    }
}

// ------------------------------------------------------------------ 已安装

@Composable
private fun InstalledTab(vm: HubViewModel, states: Map<String, InstallState>) {
    var pendingDelete by remember { mutableStateOf<CatalogModel?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("已占用 ${formatBytes(vm.installedBytes)} · 可用 ${formatBytes(vm.freeBytes)}")
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("允许移动网络下载")
                        Text("关闭时只在 Wi-Fi 下下载大模型", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = vm.allowMetered, onCheckedChange = { vm.updateAllowMetered(it) })
                }
            }

            if (vm.installedModels.isEmpty()) {
                item { Text("还没有安装模型。去「模型」页挑一个下载。") }
            } else {
                item { Text("默认模型：Router 在 AUTO 策略下优先使用它", style = MaterialTheme.typography.bodySmall) }
                items(vm.installedModels, key = { it.id }) { model ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = vm.defaultModelId == model.id,
                                onClick = { vm.setDefault(model.id) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(model.name, fontWeight = FontWeight.Medium)
                                Text(
                                    "${model.langs.size} 语种 · ${formatBytes(model.sizeBytes)} · ${model.speedClass}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                val state = vm.resolveState(model, states)
                                if (state is InstallState.Failed) {
                                    Text(state.message, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            TextButton(onClick = { pendingDelete = model }) { Text("删除") }
                        }
                    }
                }

                item {
                    Text(
                        "提示：一个时刻只加载一个模型，切换模型需要重新加载（2–5 秒）。空闲 15 分钟自动释放内存。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        pendingDelete?.let { model ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("删除模型？") },
                text = {
                    Text(
                        "将删除「${model.name}」（约 ${formatBytes(model.sizeBytes)}）。" +
                            "删除后需要重新下载才能使用。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.delete(model)
                        pendingDelete = null
                    }) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("取消") }
                },
            )
        }
    }
}

// ------------------------------------------------------------------ 测试

@Composable
private fun TestTab(vm: HubViewModel) {
    val languages = vm.availableLanguages
    val enabled = languages.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!enabled) {
            Text("先下载一个模型。")
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            LanguagePicker(
                label = "源语言",
                selected = if (vm.fromLang in languages) vm.fromLang else languages.first(),
                options = languages,
                modifier = Modifier.weight(1f),
                alignEnd = false,
                onSelect = { vm.fromLang = it },
            )
            TextButton(
                onClick = { vm.swapLanguages() },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            ) {
                Text("⇄", fontSize = 22.sp)
            }
            LanguagePicker(
                label = "目标语言",
                selected = if (vm.toLang in languages) vm.toLang else languages.last(),
                options = languages,
                modifier = Modifier.weight(1f),
                alignEnd = true,
                onSelect = { vm.toLang = it },
            )
        }

        if (!LanguageCatalog.ocrReadable(vm.fromLang)) {
            Text(
                "当前 OCR 只支持中英，该语种只能手动输入/粘贴。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        OutlinedTextField(
            value = vm.input,
            onValueChange = { vm.input = it },
            label = { Text("源文本") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { vm.translate() },
            enabled = !vm.busy && vm.input.isNotBlank(),
        ) { Text(if (vm.busy) "翻译中…" else "翻译") }

        if (vm.status.isNotEmpty()) {
            Text(vm.status, style = MaterialTheme.typography.bodySmall)
        }
        if (vm.output.isNotEmpty()) {
            OutlinedTextField(
                value = vm.output,
                onValueChange = { vm.output = it },
                label = { Text("译文") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("耗时 ${vm.elapsedMs} ms", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LanguagePicker(
    label: String,
    selected: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = LanguageCatalog.of(selected)

    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = current?.native ?: selected,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { code ->
                    val lang = LanguageCatalog.of(code)
                    DropdownMenuItem(
                        text = {
                            Text("${lang?.native ?: code} · ${lang?.code ?: code}")
                        },
                        onClick = {
                            expanded = false
                            onSelect(code)
                        },
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 工具

@Composable
private fun Tag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024) String.format("%.2f GB", mb / 1024) else String.format("%.0f MB", mb)
}
