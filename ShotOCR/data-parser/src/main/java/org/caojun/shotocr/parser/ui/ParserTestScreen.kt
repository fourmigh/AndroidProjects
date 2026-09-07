package org.caojun.shotocr.parser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.caojun.shotocr.parser.ParserRegistry
import org.caojun.shotocr.parser.R
import org.caojun.shotocr.parser.ReceiptData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParserTestScreen() {
    var inputText by remember { mutableStateOf("") }
    var selectedParser by remember { mutableStateOf("receipt") }
    var result by remember { mutableStateOf<ReceiptData?>(null) }
    var expanded by remember { mutableStateOf(false) }

    val parsers = remember { ParserRegistry.listParsers() }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.parser_test_title)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.select_parser),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                TextField(
                    value = selectedParser,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    parsers.forEach { parser ->
                        DropdownMenuItem(
                            text = { Text(parser) },
                            onClick = {
                                selectedParser = parser
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.input_ocr_text),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))

            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                placeholder = { Text(stringResource(R.string.ocr_text_placeholder)) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val parser = ParserRegistry.getParser<ReceiptData>(selectedParser)
                    result = parser?.parse(inputText)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.parse))
            }

            Spacer(modifier = Modifier.height(16.dp))

            result?.let { data ->
                Text(
                    text = stringResource(R.string.parse_result),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        ResultRow(stringResource(R.string.amount_label), data.amount?.toString() ?: "N/A")
                        ResultRow(stringResource(R.string.discount_label), data.discount?.toString() ?: "N/A")
                        ResultRow(stringResource(R.string.original_label), data.originalAmount?.toString() ?: "N/A")
                        ResultRow(stringResource(R.string.items_label), data.items.size.toString())

                        if (data.items.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.items_detail_label),
                                style = MaterialTheme.typography.labelMedium
                            )
                            data.items.forEach { item ->
                                ResultRow(
                                    item.name,
                                    "price=${item.price}, qty=${item.quantity}, sub=${item.subtotal}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
