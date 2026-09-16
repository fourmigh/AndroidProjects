package org.caojun.shotocr.parser.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.caojun.shotocr.database.data.ParserConfigDao
import org.caojun.shotocr.database.data.ParserConfigEntity
import org.caojun.shotocr.parser.R
import org.caojun.shotocr.parser.ReceiptParseConfig
import org.caojun.shotocr.patternpicker.PatternPickerContract
import org.caojun.shotocr.patternpicker.PatternPickerInput
import org.caojun.shotocr.patternpicker.PresetType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParserConfigScreen(dao: ParserConfigDao, onBack: () -> Unit = {}) {
    val configs by dao.getAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editingConfig by remember { mutableStateOf<ReceiptParseConfig?>(null) }
    var showNewDialog by remember { mutableStateOf(false) }

    if (showNewDialog) {
        NewConfigDialog(
            onDismiss = { showNewDialog = false },
            onConfirm = { name ->
                val newConfig = ReceiptParseConfig(name = name)
                scope.launch {
                    dao.insert(ParserConfigEntity.fromConfig(newConfig))
                }
                showNewDialog = false
            }
        )
    }

    editingConfig?.let { config ->
        ConfigEditScreen(
            config = config,
            onBack = { editingConfig = null },
            onSave = { updated ->
                scope.launch {
                    dao.update(ParserConfigEntity.fromConfig(updated))
                }
                editingConfig = null
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.parser_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_config))
            }
        }
    ) { padding ->
        if (configs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_configs),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(configs, key = { it.id }) { entity ->
                    ConfigItem(
                        entity = entity,
                        onClick = { editingConfig = entity.toConfig() },
                        onSetDefault = {
                            scope.launch {
                                dao.clearDefault()
                                dao.update(entity.copy(isDefault = true))
                            }
                        },
                        onDelete = {
                            scope.launch {
                                dao.delete(entity)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ConfigItem(
    entity: ParserConfigEntity,
    onClick: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_config)) },
            text = { Text(stringResource(R.string.delete_config_confirm, entity.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (entity.isDefault)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entity.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (entity.isDefault) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = stringResource(R.string.default_config),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.config_item_count, countKeywords(entity)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (!entity.isDefault) {
                IconButton(onClick = onSetDefault) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = stringResource(R.string.set_default)
                    )
                }
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete)
                )
            }
        }
    }
}

private fun countKeywords(entity: ParserConfigEntity): Int {
    var count = 0
    try {
        count += org.json.JSONArray(entity.originalAmountKeywordsJson).length()
        count += org.json.JSONArray(entity.amountKeywordsJson).length()
        count += org.json.JSONArray(entity.discountKeywordsJson).length()
    } catch (_: Exception) {}
    return count
}

@Composable
fun NewConfigDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_config)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.config_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditScreen(
    config: ReceiptParseConfig,
    onBack: () -> Unit,
    onSave: (ReceiptParseConfig) -> Unit
) {
    var name by remember { mutableStateOf(config.name) }
    var originalAmountKeywords by remember { mutableStateOf(config.originalAmountKeywords.joinToString("\n")) }
    var originalAmountPreset by remember { mutableStateOf(config.originalAmountPatternPreset) }
    var originalAmountCustom by remember { mutableStateOf(config.originalAmountRegexCustom) }
    var amountKeywords by remember { mutableStateOf(config.amountKeywords.joinToString("\n")) }
    var amountExcludePatterns by remember { mutableStateOf(config.amountExcludePatterns.joinToString("\n")) }
    var amountPreset by remember { mutableStateOf(config.amountPatternPreset) }
    var amountCustom by remember { mutableStateOf(config.amountRegexCustom) }
    var discountKeywords by remember { mutableStateOf(config.discountKeywords.joinToString("\n")) }
    var discountExcludeKeywords by remember { mutableStateOf(config.discountExcludeKeywords.joinToString("\n")) }
    var storeNameExcludeKeywords by remember { mutableStateOf(config.storeNameExcludeKeywords.joinToString("\n")) }
    var paymentTimePreset by remember { mutableStateOf(config.paymentTimePatternPreset) }
    var paymentTimeCustom by remember { mutableStateOf(config.paymentTimeRegexCustom) }
    var paymentTimeKeywords by remember { mutableStateOf(config.paymentTimeKeywords.joinToString("\n")) }
    var paymentMethodKeywords by remember { mutableStateOf(config.paymentMethodKeywords.joinToString("\n")) }
    var paymentMethodValues by remember { mutableStateOf(config.paymentMethodValues.joinToString("\n")) }
    var orderNumberKeywords by remember { mutableStateOf(config.orderNumberKeywords.joinToString("\n")) }
    var orderNumberPreset by remember { mutableStateOf(config.orderNumberPatternPreset) }
    var orderNumberCustom by remember { mutableStateOf(config.orderNumberRegexCustom) }
    var itemPreset by remember { mutableStateOf(config.itemPatternPreset) }
    var itemCustom by remember { mutableStateOf(config.itemRegexCustom) }

    val originalAmountPatternTitle = stringResource(R.string.original_amount_pattern)
    val amountPatternTitle = stringResource(R.string.amount_pattern)
    val paymentTimePatternTitle = stringResource(R.string.payment_time_pattern)
    val orderNumberPatternTitle = stringResource(R.string.order_number_pattern)
    val itemPatternTitle = stringResource(R.string.item_pattern)

    val originalAmountLauncher = rememberLauncherForActivityResult(
        contract = PatternPickerContract()
    ) { result ->
        if (result != null) {
            originalAmountPreset = result.presetName
            originalAmountCustom = result.customRegex
        }
    }

    val amountLauncher = rememberLauncherForActivityResult(
        contract = PatternPickerContract()
    ) { result ->
        if (result != null) {
            amountPreset = result.presetName
            amountCustom = result.customRegex
        }
    }

    val paymentTimeLauncher = rememberLauncherForActivityResult(
        contract = PatternPickerContract()
    ) { result ->
        if (result != null) {
            paymentTimePreset = result.presetName
            paymentTimeCustom = result.customRegex
        }
    }

    val orderNumberLauncher = rememberLauncherForActivityResult(
        contract = PatternPickerContract()
    ) { result ->
        if (result != null) {
            orderNumberPreset = result.presetName
            orderNumberCustom = result.customRegex
        }
    }

    val itemLauncher = rememberLauncherForActivityResult(
        contract = PatternPickerContract()
    ) { result ->
        if (result != null) {
            itemPreset = result.presetName
            itemCustom = result.customRegex
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_config)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val updated = config.copy(
                                name = name,
                                originalAmountKeywords = originalAmountKeywords.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                originalAmountPatternPreset = originalAmountPreset,
                                originalAmountRegexCustom = originalAmountCustom,
                                amountKeywords = amountKeywords.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                amountExcludePatterns = amountExcludePatterns.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                amountPatternPreset = amountPreset,
                                amountRegexCustom = amountCustom,
                                discountKeywords = discountKeywords.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                discountExcludeKeywords = discountExcludeKeywords.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                storeNameExcludeKeywords = storeNameExcludeKeywords.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                paymentTimePatternPreset = paymentTimePreset,
                                paymentTimeRegexCustom = paymentTimeCustom,
                                paymentTimeKeywords = paymentTimeKeywords.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                paymentMethodKeywords = paymentMethodKeywords.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                paymentMethodValues = paymentMethodValues.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                orderNumberKeywords = orderNumberKeywords.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                                orderNumberPatternPreset = orderNumberPreset,
                                orderNumberRegexCustom = orderNumberCustom,
                                itemPatternPreset = itemPreset,
                                itemRegexCustom = itemCustom
                            )
                            onSave(updated)
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.config_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                ConfigSection(title = stringResource(R.string.original_amount_section)) {
                    KeywordsField(
                        value = originalAmountKeywords,
                        onValueChange = { originalAmountKeywords = it },
                        label = stringResource(R.string.keywords_hint)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PatternPickerField(
                        presetType = PresetType.AMOUNT,
                        currentPreset = originalAmountPreset,
                        currentCustom = originalAmountCustom,
                        onClick = {
                            originalAmountLauncher.launch(
                                PatternPickerInput(
                                    title = originalAmountPatternTitle,
                                    presetType = PresetType.AMOUNT,
                                    currentPreset = originalAmountPreset,
                                    currentCustom = originalAmountCustom
                                )
                            )
                        }
                    )
                }
            }

            item {
                ConfigSection(title = stringResource(R.string.amount_section)) {
                    KeywordsField(
                        value = amountKeywords,
                        onValueChange = { amountKeywords = it },
                        label = stringResource(R.string.keywords_hint)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    KeywordsField(
                        value = amountExcludePatterns,
                        onValueChange = { amountExcludePatterns = it },
                        label = stringResource(R.string.exclude_patterns_hint)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PatternPickerField(
                        presetType = PresetType.AMOUNT,
                        currentPreset = amountPreset,
                        currentCustom = amountCustom,
                        onClick = {
                            amountLauncher.launch(
                                PatternPickerInput(
                                    title = amountPatternTitle,
                                    presetType = PresetType.AMOUNT,
                                    currentPreset = amountPreset,
                                    currentCustom = amountCustom
                                )
                            )
                        }
                    )
                }
            }

            item {
                ConfigSection(title = stringResource(R.string.discount_section)) {
                    KeywordsField(
                        value = discountKeywords,
                        onValueChange = { discountKeywords = it },
                        label = stringResource(R.string.keywords_hint)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    KeywordsField(
                        value = discountExcludeKeywords,
                        onValueChange = { discountExcludeKeywords = it },
                        label = stringResource(R.string.exclude_keywords_hint)
                    )
                }
            }

            item {
                ConfigSection(title = stringResource(R.string.store_name_section)) {
                    KeywordsField(
                        value = storeNameExcludeKeywords,
                        onValueChange = { storeNameExcludeKeywords = it },
                        label = stringResource(R.string.exclude_keywords_hint)
                    )
                }
            }

            item {
                ConfigSection(title = stringResource(R.string.payment_time_section)) {
                    KeywordsField(
                        value = paymentTimeKeywords,
                        onValueChange = { paymentTimeKeywords = it },
                        label = stringResource(R.string.keywords_hint)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PatternPickerField(
                        presetType = PresetType.TIME,
                        currentPreset = paymentTimePreset,
                        currentCustom = paymentTimeCustom,
                        onClick = {
                            paymentTimeLauncher.launch(
                                PatternPickerInput(
                                    title = paymentTimePatternTitle,
                                    presetType = PresetType.TIME,
                                    currentPreset = paymentTimePreset,
                                    currentCustom = paymentTimeCustom
                                )
                            )
                        }
                    )
                }
            }

            item {
                ConfigSection(title = stringResource(R.string.payment_method_section)) {
                    KeywordsField(
                        value = paymentMethodKeywords,
                        onValueChange = { paymentMethodKeywords = it },
                        label = stringResource(R.string.keywords_hint)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    KeywordsField(
                        value = paymentMethodValues,
                        onValueChange = { paymentMethodValues = it },
                        label = stringResource(R.string.method_values_hint)
                    )
                }
            }

            item {
                ConfigSection(title = stringResource(R.string.order_number_section)) {
                    KeywordsField(
                        value = orderNumberKeywords,
                        onValueChange = { orderNumberKeywords = it },
                        label = stringResource(R.string.keywords_hint)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PatternPickerField(
                        presetType = PresetType.ORDER_NUMBER,
                        currentPreset = orderNumberPreset,
                        currentCustom = orderNumberCustom,
                        onClick = {
                            orderNumberLauncher.launch(
                                PatternPickerInput(
                                    title = orderNumberPatternTitle,
                                    presetType = PresetType.ORDER_NUMBER,
                                    currentPreset = orderNumberPreset,
                                    currentCustom = orderNumberCustom
                                )
                            )
                        }
                    )
                }
            }

            item {
                ConfigSection(title = stringResource(R.string.item_section)) {
                    PatternPickerField(
                        presetType = PresetType.ITEM,
                        currentPreset = itemPreset,
                        currentCustom = itemCustom,
                        onClick = {
                            itemLauncher.launch(
                                PatternPickerInput(
                                    title = itemPatternTitle,
                                    presetType = PresetType.ITEM,
                                    currentPreset = itemPreset,
                                    currentCustom = itemCustom
                                )
                            )
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun PatternPickerField(
    presetType: PresetType,
    currentPreset: String,
    currentCustom: String,
    onClick: () -> Unit
) {
    val displayName = if (currentCustom.isNotEmpty()) {
        currentCustom
    } else {
        org.caojun.shotocr.patternpicker.RegexPreset.getPatterns(presetType)
            .find { it.name == currentPreset }?.displayName ?: currentPreset
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.pattern_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.edit_pattern),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun ConfigSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
fun KeywordsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp),
        maxLines = 5
    )
}
