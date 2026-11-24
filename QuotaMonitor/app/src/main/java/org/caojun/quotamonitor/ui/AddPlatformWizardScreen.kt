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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.caojun.quotamonitor.data.QuotaAutoDetector
import org.caojun.quotamonitor.model.DetectedMetric
import org.caojun.quotamonitor.model.MetricRule
import org.caojun.quotamonitor.model.PlatformConfig
import org.caojun.quotamonitor.ui.theme.Accent
import org.caojun.quotamonitor.ui.theme.Border
import org.caojun.quotamonitor.ui.theme.CardBg
import org.caojun.quotamonitor.ui.theme.Ink
import org.caojun.quotamonitor.ui.theme.Muted
import org.caojun.quotamonitor.ui.theme.Paper
import org.caojun.quotamonitor.viewmodel.QuotaController

private enum class WizardPhase { INPUT, BROWSER, CANDIDATES }

private data class CandidateDraft(
    val metric: DetectedMetric,
    var selected: Boolean,
    var label: String,
)

/**
 * 新增平台向导：填写 URL → 打开页面登录 → 自动识别 → 勾选确认 → 保存。
 * 用户无需编写任何正则。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlatformWizardScreen(
    controller: QuotaController,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onToast: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf(WizardPhase.INPUT) }
    val candidates = remember { mutableStateListOf<CandidateDraft>() }
    val defaultLoginKeywords = stringArrayResource(R.array.default_login_keywords)

    fun detect() {
        val scan = controller.draftScanJson()
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
        phase = WizardPhase.CANDIDATES
    }

    Scaffold(
        containerColor = Paper,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.wizard_title), fontWeight = FontWeight.Bold)
                        Text(
                            when (phase) {
                                WizardPhase.INPUT -> stringResource(R.string.wizard_step_hint)
                                WizardPhase.BROWSER -> stringResource(R.string.wizard_detect)
                                WizardPhase.CANDIDATES -> stringResource(R.string.wizard_candidates_title)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = {
                        if (phase == WizardPhase.BROWSER) {
                            controller.closeDraft()
                        }
                        if (phase == WizardPhase.CANDIDATES) {
                            phase = WizardPhase.BROWSER
                        } else {
                            onBack()
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
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    placeholder = { Text(stringResource(R.string.wizard_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = {
                    if (url.isBlank() || !url.startsWith("http")) {
                        onToast(controller.text(R.string.wizard_need_url))
                        return@TextButton
                    }
                    controller.openDraft(url.trim(), name.trim())
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
                    controller.attachForDisplay(controller.draftId(), container as ViewGroup)
                },
                onRelease = { container ->
                    controller.detachFromDisplay(controller.draftId(), container as ViewGroup)
                },
            )

            WizardPhase.CANDIDATES -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
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
                                        draft.metric.sampleText + draft.metric.resetHint?.let { " · $it" }.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Muted,
                                        fontSize = 11.sp,
                                    )
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
                        val config = PlatformConfig(
                            id = controller.newConfigId(),
                            name = name.trim().ifBlank { url.trim() },
                            url = url.trim(),
                            enabled = true,
                            loginKeywords = defaultLoginKeywords.toList(),
                            marketingKeywords = emptyList(),
                            pageMarkerRegex = null,
                            planNameRegex = null,
                            planLineRegex = null,
                            remainKey = null,
                            endKey = null,
                            metrics = selected.map { draft ->
                                MetricRule(
                                    label = draft.label.trim().ifBlank { draft.metric.label },
                                    mode = draft.metric.mode,
                                    valueRegex = draft.metric.valueRegex,
                                    resetRegex = draft.metric.resetRegex,
                                    lookAhead = 6,
                                )
                            },
                        )
                        val err = controller.addConfig(config)
                        if (err != null) {
                            onToast(controller.text(R.string.error_no_metrics))
                        } else {
                            controller.closeDraft()
                            onToast(controller.text(R.string.wizard_saved, config.name))
                            onSaved()
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
