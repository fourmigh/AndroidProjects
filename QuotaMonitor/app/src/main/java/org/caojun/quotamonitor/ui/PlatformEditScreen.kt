package org.caojun.quotamonitor.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.caojun.quotamonitor.R
import org.caojun.quotamonitor.data.PlatformStore
import org.caojun.quotamonitor.model.MetricMode
import org.caojun.quotamonitor.model.MetricRule
import org.caojun.quotamonitor.model.PlatformConfig
import org.caojun.quotamonitor.ui.theme.Accent
import org.caojun.quotamonitor.ui.theme.Border
import org.caojun.quotamonitor.ui.theme.CardBg
import org.caojun.quotamonitor.ui.theme.Ink
import org.caojun.quotamonitor.ui.theme.Muted
import org.caojun.quotamonitor.ui.theme.Paper
import org.caojun.quotamonitor.viewmodel.QuotaController

private data class MetricDraft(
    var label: String = "",
    var mode: MetricMode = MetricMode.PERCENT_INLINE,
    var valueRegex: String = "",
    var resetRegex: String = "",
    var lookAhead: String = "6",
)

/**
 * 编辑平台配置：基础信息 + 指标规则 + 高级规则。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformEditScreen(
    controller: QuotaController,
    configId: String?,
    onBack: () -> Unit,
    onRecognize: (String) -> Unit,
    onToast: (String) -> Unit,
) {
    val existing = configId?.let { controller.findConfig(it) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var url by remember { mutableStateOf(existing?.url ?: "") }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var pageMarker by remember { mutableStateOf(existing?.pageMarkerRegex ?: "") }
    var planNameRegex by remember { mutableStateOf(existing?.planNameRegex ?: "") }
    var planLineRegex by remember { mutableStateOf(existing?.planLineRegex ?: "") }
    var remainKey by remember { mutableStateOf(existing?.remainKey ?: "") }
    var endKey by remember { mutableStateOf(existing?.endKey ?: "") }
    var loginKeywords by remember { mutableStateOf(existing?.loginKeywords?.joinToString(", ") ?: "") }
    var marketingKeywords by remember { mutableStateOf(existing?.marketingKeywords?.joinToString(", ") ?: "") }
    var advanced by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }

    val metrics = remember {
        mutableStateListOf<MetricDraft>().apply {
            existing?.metrics?.forEach { rule ->
                add(
                    MetricDraft(
                        label = rule.label,
                        mode = rule.mode,
                        valueRegex = rule.valueRegex.orEmpty(),
                        resetRegex = rule.resetRegex.orEmpty(),
                        lookAhead = rule.lookAhead.toString(),
                    )
                )
            }
            if (isEmpty()) add(MetricDraft())
        }
    }

    fun errorText(err: PlatformStore.ValidationError): String = when (err) {
        PlatformStore.ValidationError.INVALID_URL -> controller.text(R.string.error_invalid_url)
        PlatformStore.ValidationError.EMPTY_NAME -> controller.text(R.string.error_empty_name)
        PlatformStore.ValidationError.NO_METRICS -> controller.text(R.string.error_no_metrics)
        PlatformStore.ValidationError.INVALID_REGEX -> controller.text(R.string.error_invalid_regex)
        PlatformStore.ValidationError.EMPTY_METRIC_LABEL -> controller.text(R.string.error_empty_metric_label)
        PlatformStore.ValidationError.INVALID_METRIC_MODE -> controller.text(R.string.error_invalid_metric_mode)
    }

    Scaffold(
        containerColor = Paper,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (configId == null) R.string.edit_new_title else R.string.edit_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.console_back), color = Ink)
                    }
                },
                actions = {
                    existing?.let { config ->
                        TextButton(onClick = { showDiagnostics = true }) {
                            Text(stringResource(R.string.action_diagnostics), color = Muted)
                        }
                        TextButton(onClick = { onRecognize(config.id) }) {
                            Text(stringResource(R.string.action_recognize), color = Muted)
                        }
                    }
                    TextButton(onClick = {
                        val rules = metrics.mapNotNull { draft ->
                            draft.label.trim().takeIf { it.isNotEmpty() }?.let { label ->
                                MetricRule(
                                    label = label,
                                    mode = draft.mode,
                                    valueRegex = draft.valueRegex.trim().takeIf { it.isNotEmpty() },
                                    resetRegex = draft.resetRegex.trim().takeIf { it.isNotEmpty() },
                                    lookAhead = draft.lookAhead.toIntOrNull()?.coerceIn(1, 20) ?: 6,
                                )
                            }
                        }
                        val config = PlatformConfig(
                            id = existing?.id ?: controller.newConfigId(),
                            name = name.trim(),
                            url = url.trim(),
                            enabled = enabled,
                            loginKeywords = loginKeywords.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() },
                            marketingKeywords = marketingKeywords.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() },
                            pageMarkerRegex = pageMarker.trim().takeIf { it.isNotEmpty() },
                            planNameRegex = planNameRegex.trim().takeIf { it.isNotEmpty() },
                            planLineRegex = planLineRegex.trim().takeIf { it.isNotEmpty() },
                            remainKey = remainKey.trim().takeIf { it.isNotEmpty() },
                            endKey = endKey.trim().takeIf { it.isNotEmpty() },
                            metrics = rules,
                        )
                        val err = if (existing == null) controller.addConfig(config) else controller.updateConfig(config)
                        if (err == null) onBack() else onToast(errorText(err))
                    }) {
                        Text(stringResource(R.string.action_save), color = Accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Paper,
                    titleContentColor = Ink,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.field_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.field_metrics),
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            metrics.forEachIndexed { index, draft ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = BorderStroke(1.dp, Border),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = draft.label,
                                onValueChange = { metrics[index] = draft.copy(label = it) },
                                label = { Text(stringResource(R.string.field_metric_label)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { metrics.removeAt(index) }) {
                                Text(stringResource(R.string.action_delete), color = Muted, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilterChip(
                                selected = draft.mode == MetricMode.PERCENT_INLINE,
                                onClick = { metrics[index] = draft.copy(mode = MetricMode.PERCENT_INLINE) },
                                label = { Text(stringResource(R.string.mode_percent_inline), fontSize = 11.sp) },
                            )
                            FilterChip(
                                selected = draft.mode == MetricMode.PERCENT_NEXT_NUMBER,
                                onClick = { metrics[index] = draft.copy(mode = MetricMode.PERCENT_NEXT_NUMBER) },
                                label = { Text(stringResource(R.string.mode_percent_next_number), fontSize = 11.sp) },
                            )
                            FilterChip(
                                selected = draft.mode == MetricMode.USED_TOTAL,
                                onClick = { metrics[index] = draft.copy(mode = MetricMode.USED_TOTAL) },
                                label = { Text(stringResource(R.string.mode_used_total), fontSize = 11.sp) },
                            )
                        }
                        OutlinedTextField(
                            value = draft.valueRegex,
                            onValueChange = { metrics[index] = draft.copy(valueRegex = it) },
                            label = { Text(stringResource(R.string.field_value_regex)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                        )
                        OutlinedTextField(
                            value = draft.resetRegex,
                            onValueChange = { metrics[index] = draft.copy(resetRegex = it) },
                            label = { Text(stringResource(R.string.field_reset_regex)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                        )
                        OutlinedTextField(
                            value = draft.lookAhead,
                            onValueChange = { metrics[index] = draft.copy(lookAhead = it) },
                            label = { Text(stringResource(R.string.field_look_ahead)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .padding(top = 6.dp),
                        )
                    }
                }
            }
            TextButton(onClick = {
                metrics.add(MetricDraft())
            }) {
                Text(stringResource(R.string.field_add_metric), color = Accent)
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { advanced = !advanced }) {
                Text(stringResource(R.string.advanced_section), color = Muted, fontSize = 13.sp)
            }
            if (advanced) {
                AdvancedFields(
                    pageMarker = pageMarker,
                    onPageMarker = { pageMarker = it },
                    planNameRegex = planNameRegex,
                    onPlanNameRegex = { planNameRegex = it },
                    planLineRegex = planLineRegex,
                    onPlanLineRegex = { planLineRegex = it },
                    remainKey = remainKey,
                    onRemainKey = { remainKey = it },
                    endKey = endKey,
                    onEndKey = { endKey = it },
                    loginKeywords = loginKeywords,
                    onLoginKeywords = { loginKeywords = it },
                    marketingKeywords = marketingKeywords,
                    onMarketingKeywords = { marketingKeywords = it },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDiagnostics && existing != null) {
        val diagnostics = controller.diagnose(existing.id)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            title = { Text(stringResource(R.string.diagnostics_title)) },
            text = {
                if (diagnostics.isEmpty()) {
                    Text(stringResource(R.string.diagnostics_empty))
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        diagnostics.forEach { diag ->
                            Text(
                                stringResource(R.string.diagnostics_rule) + "： " + diag.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Accent,
                            )
                            Text(
                                if (diag.matchedIndex < 0) {
                                    stringResource(R.string.diagnostics_unmatched)
                                } else {
                                    stringResource(R.string.diagnostics_matched, diag.matchedIndex + 1) +
                                        "： " + (diag.matchedLine ?: "")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted,
                                fontSize = 11.sp,
                            )
                            if (diag.window.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.diagnostics_window) + "： " + diag.window.joinToString(" ⏎ "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Muted,
                                    fontSize = 11.sp,
                                )
                            }
                            Text(
                                diag.value?.let { stringResource(R.string.diagnostics_value, it) }
                                    ?: stringResource(R.string.diagnostics_undetected),
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink,
                                fontSize = 11.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiagnostics = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Composable
private fun AdvancedFields(
    pageMarker: String,
    onPageMarker: (String) -> Unit,
    planNameRegex: String,
    onPlanNameRegex: (String) -> Unit,
    planLineRegex: String,
    onPlanLineRegex: (String) -> Unit,
    remainKey: String,
    onRemainKey: (String) -> Unit,
    endKey: String,
    onEndKey: (String) -> Unit,
    loginKeywords: String,
    onLoginKeywords: (String) -> Unit,
    marketingKeywords: String,
    onMarketingKeywords: (String) -> Unit,
) {
    Column {
        listOf(
            R.string.field_page_marker to (pageMarker to onPageMarker),
            R.string.field_plan_name_regex to (planNameRegex to onPlanNameRegex),
            R.string.field_plan_line_regex to (planLineRegex to onPlanLineRegex),
            R.string.field_remain_key to (remainKey to onRemainKey),
            R.string.field_end_key to (endKey to onEndKey),
            R.string.field_login_keywords to (loginKeywords to onLoginKeywords),
            R.string.field_marketing_keywords to (marketingKeywords to onMarketingKeywords),
        ).forEach { (labelRes, pair) ->
            OutlinedTextField(
                value = pair.first,
                onValueChange = pair.second,
                label = { Text(stringResource(labelRes), fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
    }
}
