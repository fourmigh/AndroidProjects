package org.caojun.shotocr.patternpicker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternPickerScreen(
    title: String,
    presetType: PresetType,
    currentPreset: String,
    currentCustom: String,
    onSelect: (presetName: String, customRegex: String) -> Unit,
    onBack: () -> Unit
) {
    val presets = remember(presetType) { RegexPreset.getPatterns(presetType) }
    var selectedPreset by remember { mutableStateOf(currentPreset.ifEmpty { presets.firstOrNull()?.name ?: "" }) }
    var isCustom by remember { mutableStateOf(currentCustom.isNotEmpty()) }
    var customRegex by remember { mutableStateOf(currentCustom) }
    var testInput by remember { mutableStateOf("") }

    val activeRegex = remember(selectedPreset, isCustom, customRegex) {
        if (isCustom) customRegex
        else presets.find { it.name == selectedPreset }?.regex ?: ""
    }

    val matchResult = remember(testInput, activeRegex) {
        if (testInput.isBlank() || activeRegex.isBlank()) null
        else try {
            val regex = Regex(activeRegex)
            val matches = regex.findAll(testInput).toList()
            if (matches.isEmpty()) emptyList()
            else matches.map { it.value to it.range }
        } catch (_: Exception) {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.ifEmpty { stringResource(R.string.pattern_picker_title) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            val resultPreset = if (isCustom) "" else selectedPreset
                            val resultCustom = if (isCustom) customRegex else ""
                            onSelect(resultPreset, resultCustom)
                        }
                    ) {
                        Text(stringResource(R.string.confirm_select))
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.preset_templates),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            items(presets) { preset ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedPreset = preset.name
                            isCustom = false
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (!isCustom && selectedPreset == preset.name)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !isCustom && selectedPreset == preset.name,
                            onClick = {
                                selectedPreset = preset.name
                                isCustom = false
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.displayName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.example_prefix, preset.example),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCustom = true },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCustom)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isCustom,
                            onClick = { isCustom = true }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.custom_regex),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                if (isCustom) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customRegex,
                        onValueChange = { customRegex = it },
                        label = { Text(stringResource(R.string.custom_regex_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.test_area),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = testInput,
                    onValueChange = { testInput = it },
                    label = { Text(stringResource(R.string.test_input_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5
                )
            }

            if (testInput.isNotBlank()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.match_result),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (activeRegex.isBlank()) {
                                Text(
                                    text = "—",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else if (matchResult == null) {
                                Text(
                                    text = stringResource(R.string.no_match),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (matchResult.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.no_match),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            } else {
                                matchResult.forEach { (value, _) ->
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}
