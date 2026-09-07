package org.caojun.shotocr.accounting.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import org.caojun.shotocr.accounting.AccountingManager
import org.caojun.shotocr.accounting.EditableReceipt
import org.caojun.shotocr.accounting.EditableReceiptItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmScreen(
    receipt: EditableReceipt,
    onSaved: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    var amount by remember { mutableStateOf(receipt.amount) }
    var discount by remember { mutableStateOf(receipt.discount) }
    var originalAmount by remember { mutableStateOf(receipt.originalAmount) }
    var items by remember { mutableStateOf(receipt.items) }
    var showSaveToast by remember { mutableStateOf(false) }
    var showFullscreenImage by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(org.caojun.shotocr.accounting.R.string.confirm_title)) },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(org.caojun.shotocr.accounting.R.string.cancel))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (receipt.screenshotUri != Uri.EMPTY) {
                Image(
                    painter = rememberAsyncImagePainter(receipt.screenshotUri),
                    contentDescription = stringResource(org.caojun.shotocr.accounting.R.string.screenshot_desc),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable { showFullscreenImage = true },
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (showFullscreenImage) {
                Dialog(
                    onDismissRequest = { showFullscreenImage = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showFullscreenImage = false }
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(receipt.screenshotUri),
                            contentDescription = stringResource(org.caojun.shotocr.accounting.R.string.screenshot_desc),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(org.caojun.shotocr.accounting.R.string.amount_info),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text(stringResource(org.caojun.shotocr.accounting.R.string.actual_amount)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = discount,
                        onValueChange = { discount = it },
                        label = { Text(stringResource(org.caojun.shotocr.accounting.R.string.discount_amount)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = originalAmount,
                        onValueChange = { originalAmount = it },
                        label = { Text(stringResource(org.caojun.shotocr.accounting.R.string.original_amount_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(org.caojun.shotocr.accounting.R.string.items_detail),
                            fontSize = 16.sp
                        )
                        TextButton(onClick = {
                            items = items + EditableReceiptItem()
                        }) {
                            Text(stringResource(org.caojun.shotocr.accounting.R.string.add_item))
                        }
                    }

                    items.forEachIndexed { index, item ->
                        EditableItemCard(
                            item = item,
                            onItemChange = { updated ->
                                items = items.toMutableList().apply {
                                    set(index, updated)
                                }.toList()
                            },
                            onDelete = {
                                items = items.toMutableList().apply {
                                    removeAt(index)
                                }.toList()
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(org.caojun.shotocr.accounting.R.string.ocr_raw_text),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = receipt.rawText.ifEmpty { stringResource(org.caojun.shotocr.accounting.R.string.no_text) },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = {
                    val savedReceipt = EditableReceipt(
                        screenshotUri = receipt.screenshotUri,
                        rawText = receipt.rawText,
                        amount = amount,
                        discount = discount,
                        originalAmount = originalAmount,
                        items = items
                    )
                    AccountingManager.save(savedReceipt)
                    showSaveToast = true
                    onSaved()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(org.caojun.shotocr.accounting.R.string.save))
            }

            if (showSaveToast) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(org.caojun.shotocr.accounting.R.string.saved),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun EditableItemCard(
    item: EditableReceiptItem,
    onItemChange: (EditableReceiptItem) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(org.caojun.shotocr.accounting.R.string.item_label),
                    fontSize = 14.sp
                )
                TextButton(onClick = onDelete) {
                    Text(
                        stringResource(org.caojun.shotocr.accounting.R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            OutlinedTextField(
                value = item.name,
                onValueChange = { onItemChange(item.copy(name = it)) },
                label = { Text(stringResource(org.caojun.shotocr.accounting.R.string.name)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = item.price,
                    onValueChange = { onItemChange(item.copy(price = it)) },
                    label = { Text(stringResource(org.caojun.shotocr.accounting.R.string.unit_price)) },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = item.quantity,
                    onValueChange = { onItemChange(item.copy(quantity = it)) },
                    label = { Text(stringResource(org.caojun.shotocr.accounting.R.string.quantity)) },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = item.subtotal,
                    onValueChange = { onItemChange(item.copy(subtotal = it)) },
                    label = { Text(stringResource(org.caojun.shotocr.accounting.R.string.subtotal)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
