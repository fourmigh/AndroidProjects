package org.caojun.quotamonitor.ui

import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.caojun.quotamonitor.R
import org.caojun.quotamonitor.data.PlatformNaming
import org.caojun.quotamonitor.data.PlatformStore
import org.caojun.quotamonitor.data.QuotaAutoDetector
import org.caojun.quotamonitor.model.DetectedMetric
import org.caojun.quotamonitor.model.MetricRule
import org.caojun.quotamonitor.model.PlatformConfig
import org.caojun.quotamonitor.model.SyncStatus
import org.caojun.quotamonitor.ui.theme.Accent
import org.caojun.quotamonitor.ui.theme.Border
import org.caojun.quotamonitor.ui.theme.CardBg
import org.caojun.quotamonitor.ui.theme.Ink
import org.caojun.quotamonitor.ui.theme.Muted
import org.caojun.quotamonitor.ui.theme.Paper
import org.caojun.quotamonitor.ui.theme.WarnAmber
import org.caojun.quotamonitor.viewmodel.QuotaController

private enum class WizardPhase { INPUT, BROWSER, CANDIDATES }

private data class CandidateDraft(
    val metric: DetectedMetric,
    var selected: Boolean,
    var label: String,
)

/**
 * 平台识别向导：
 * - 新增模式（editingConfigId = null）：填写 URL → 打开页面登录 → 自动识别 → 勾选保存
 * - 编辑模式（editingConfigId != null）：复用该平台已有的 WebView（登录态保留），
 *   直接重新识别并用识别结果替换指标列表。
 *
 * 用户无需编写任何正则。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlatformWizardScreen(
    controller: QuotaController,
    editingConfigId: String? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onToast: (String) -> Unit,
) {
    val editing = editingConfigId?.let { controller.findConfig(it) }
    var url by remember { mutableStateOf(editing?.url ?: "") }
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var phase by remember { mutableStateOf(if (editing != null) WizardPhase.BROWSER else WizardPhase.INPUT) }
    val candidates = remember { mutableStateListOf<CandidateDraft>() }
    val defaultLoginKeywords = stringArrayResource(R.array.default_login_keywords)
    val activeId = editingConfigId ?: controller.draftId()
    val pageTitle = controller.pageTitles[activeId]

    fun errorText(err: PlatformStore.ValidationError): String = when (err) {
        PlatformStore.ValidationError.INVALID_URL -> controller.text(R.string.error_invalid_url)
        PlatformStore.ValidationError.EMPTY_NAME -> controller.text(R.string.error_empty_name)
        PlatformStore.ValidationError.NO_METRICS -> controller.text(R.string.error_no_metrics)
        PlatformStore.ValidationError.INVALID_REGEX -> controller.text(R.string.error_invalid_regex)
        PlatformStore.ValidationError.EMPTY_METRIC_LABEL -> controller.text(R.string.error_empty_metric_label)
        PlatformStore.ValidationError.INVALID_METRIC_MODE -> controller.text(R.string.error_invalid_metric_mode)
    }

    // 编辑模式：确保页面已加载（WebView 与登录态直接复用）
    LaunchedEffect(activeId, editing != null) {
        if (editing != null) {
            val status = controller.states[activeId]?.status
            if (status != SyncStatus.OK && status != SyncStatus.LOADING) {
                controller.refresh(activeId)
            }
        }
    }

    fun detect() {
        val scan = controller.scanJson(activeId)
        if (scan == null) {
            onToast(controller.text(R.string.wizard_no_candidates))
            return
        }
        val found = QuotaAutoDetector.detect(scan)
        if (found.isEmpty()) {
            onToast(controller.text(R.string.wizard_no_candidates))
            return
        }
        candidates.clear()
        found.forEach { candidates.add(CandidateDraft(metric = it, selected = true, label = it.label)) }
        if (name.isBlank()) {
            name = PlatformNaming.derive(controller.pageTitle(activeId), url)
        }
        phase = WizardPhase.CANDIDATES
    }

    Scaffold(
        containerColor = Paper,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(if (editing != null) R.string.recognize_title else R.string.wizard_title),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            when (phase) {
                                WizardPhase.INPUT -> stringResource(R.string.wizard_step_hint)
                                WizardPhase.BROWSER -> pageTitle ?: stringResource(
                                    if (editing != null) R.string.recognize_hint else R.string.wizard_detect,
                                )
                                WizardPhase.CANDIDATES -> stringResource(R.string.wizard_candidates_title)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = {
                        when {
                            phase == WizardPhase.CANDIDATES -> phase = WizardPhase.BROWSER
                            editing == null -> {
                                controller.closeDraft()
                                onBack()
                            }
                            else -> onBack()
                        }
                    }) {
                        Text(stringResource(R.string.console_back), color = Ink)
                    }
                },
                actions = {
                    if (phase == WizardPhase.BROWSER) {
                        TextButton(onClick = { detect() }) {
                            Text(stringResource(R.string.wizard_detect), color = Accent)
                        }
                        TextButton(onClick = { controller.refresh(activeId) }) {
                            Text(stringResource(R.string.action_refresh), color = Accent)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Paper,
                    titleContentColor = Ink,
                ),
            )
        },
    ) { padding ->
        when (phase) {
            WizardPhase.INPUT -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.field_url)) },
                    placeholder = { Text(stringResource(R.string.wizard_url_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = {
                    if (url.isBlank() || !url.startsWith("http")) {
                        onToast(controller.text(R.string.wizard_need_url))
                        return@TextButton
                    }
                    controller.openDraft(url.trim(), "")
                    phase = WizardPhase.BROWSER
                }) {
                    Text(stringResource(R.string.wizard_open), color = Accent)
                }
            }

            WizardPhase.BROWSER -> AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                factory = { context -> android.widget.FrameLayout(context) },
                update = { container ->
                    controller.attachForDisplay(activeId, container as ViewGroup)
                },
                onRelease = { container ->
                    controller.detachFromDisplay(activeId, container as ViewGroup)
                },
            )

            WizardPhase.CANDIDATES -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    supportingText = { Text(stringResource(R.string.wizard_name_auto), fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(candidates.size) { index ->
                        val draft = candidates[index]
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            border = BorderStroke(1.dp, Border),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = draft.selected,
                                    onCheckedChange = { candidates[index] = draft.copy(selected = it) },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = draft.label,
                                        onValueChange = { candidates[index] = draft.copy(label = it) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        draft.metric.sampleText +
                                            draft.metric.resetHint?.let { " · $it" }.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Muted,
                                        fontSize = 11.sp,
                                    )
                                    val prev = candidates.getOrNull(index - 1)
                                    if (prev != null && prev.metric.sampleText == draft.metric.sampleText) {
                                        Text(
                                            stringResource(R.string.wizard_duplicate_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = WarnAmber,
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { phase = WizardPhase.BROWSER },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.wizard_back_to_page), fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        val selected = candidates.filter { it.selected }
                        if (selected.isEmpty()) {
                            onToast(controller.text(R.string.wizard_nothing_selected))
                            return@TextButton
                        }
                        val rules = selected.map { draft ->
                            MetricRule(
                                label = draft.label.trim().ifBlank { draft.metric.label },
                                mode = draft.metric.mode,
                                valueRegex = draft.metric.valueRegex,
                                resetRegex = draft.metric.resetRegex,
                                lookAhead = 6,
                            )
                        }
                        if (editing != null) {
                            val targetName = name.trim().ifBlank { editing.name }
                            // 现有配置若缺少高级规则，按主机名从内置模板回填（恢复套餐名等信息）
                            val template = controller.templateForUrl(editing.url)
                            val updated = editing.copy(
                                name = targetName,
                                metrics = rules,
                                planNameRegex = editing.planNameRegex ?: template?.planNameRegex,
                                planLineRegex = editing.planLineRegex ?: template?.planLineRegex,
                                pageMarkerRegex = editing.pageMarkerRegex ?: template?.pageMarkerRegex,
                                remainKey = editing.remainKey ?: template?.remainKey,
                                endKey = editing.endKey ?: template?.endKey,
                                loginKeywords = editing.loginKeywords.ifEmpty {
                                    template?.loginKeywords ?: emptyList()
                                },
                                marketingKeywords = editing.marketingKeywords.ifEmpty {
                                    template?.marketingKeywords ?: emptyList()
                                },
                            )
                            val err = controller.updateConfig(updated)
                            if (err != null) {
                                onToast(errorText(err))
                            } else {
                                onToast(controller.text(R.string.recognize_saved))
                                onSaved()
                            }
                        } else {
                            // 与内置平台同主机时，继承模板的高级规则（套餐名、页面特征、登录判定等）
                            val template = controller.templateForUrl(url.trim())
                            val config = PlatformConfig(
                                id = controller.newConfigId(),
                                name = name.trim().ifBlank {
                                    PlatformNaming.derive(controller.pageTitle(activeId), url)
                                },
                                url = url.trim(),
                                enabled = true,
                                loginKeywords = template?.loginKeywords?.takeIf { it.isNotEmpty() }
                                    ?: defaultLoginKeywords.toList(),
                                marketingKeywords = template?.marketingKeywords ?: emptyList(),
                                pageMarkerRegex = template?.pageMarkerRegex,
                                planNameRegex = template?.planNameRegex,
                                planLineRegex = template?.planLineRegex,
                                remainKey = template?.remainKey,
                                endKey = template?.endKey,
                                metrics = rules,
                            )
                            val err = controller.addConfig(config)
                            if (err != null) {
                                onToast(errorText(err))
                            } else {
                                controller.closeDraft()
                                onToast(controller.text(R.string.wizard_saved, config.name))
                                onSaved()
                            }
                        }
                    }) {
                        Text(stringResource(R.string.wizard_save), color = Accent)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
