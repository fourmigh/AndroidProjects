package org.caojun.shotocr.accounting.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import org.caojun.shotocr.accounting.AccountingManager
import org.caojun.shotocr.accounting.EditableReceipt
import org.caojun.shotocr.accounting.formatPaymentTime
import org.caojun.shotocr.classification.repository.CategoryRepository
import org.caojun.shotocr.classification.ui.CategorySelectField

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConfirmScreen(
    receipt: EditableReceipt,
    onSaved: (EditableReceipt) -> Unit = {},
    onCancel: () -> Unit = {},
    onDeleteScreenshot: () -> Unit = {},
    askDeleteOnCancel: Boolean = false,
    autoSuggestedCategoryId: String? = null,
    initialDeleteScreenshot: Boolean = false,
    onRememberCorrection: (String) -> Unit = {}
) {
    var amount by remember { mutableStateOf(receipt.amount) }
    var discount by remember { mutableStateOf(receipt.discount) }
    var originalAmount by remember { mutableStateOf(receipt.originalAmount) }
    var storeName by remember { mutableStateOf(receipt.storeName) }
    var sourceApp by remember { mutableStateOf(receipt.sourceApp) }
    var paymentTime by remember { mutableStateOf(formatPaymentTime(receipt.paymentTime)) }
    var paymentMethod by remember { mutableStateOf(receipt.paymentMethod) }
    var orderNumber by remember { mutableStateOf(receipt.orderNumber) }
    var categoryId by remember { mutableStateOf(receipt.categoryId) }
    var showSaveToast by remember { mutableStateOf(false) }
    var showFullscreenImage by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showInitialDeleteDialog by remember { mutableStateOf(initialDeleteScreenshot) }
    var rawTextExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = !showCancelDialog) {
        if (askDeleteOnCancel) showCancelDialog = true else onCancel()
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(stringResource(org.caojun.shotocr.accounting.R.string.cancel_delete_screenshot_title)) },
            text = { Text(stringResource(org.caojun.shotocr.accounting.R.string.cancel_delete_screenshot_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showCancelDialog = false
                    onDeleteScreenshot()
                }) {
                    Text(stringResource(org.caojun.shotocr.accounting.R.string.delete_screenshot))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCancelDialog = false
                    onCancel()
                }) {
                    Text(stringResource(org.caojun.shotocr.accounting.R.string.keep_screenshot))
                }
            }
        )
    }

    if (showInitialDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showInitialDeleteDialog = false },
            title = { Text(stringResource(org.caojun.shotocr.accounting.R.string.duplicate_delete_screenshot_title)) },
            text = { Text(stringResource(org.caojun.shotocr.accounting.R.string.duplicate_delete_screenshot_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showInitialDeleteDialog = false
                    onDeleteScreenshot()
                }) {
                    Text(stringResource(org.caojun.shotocr.accounting.R.string.delete_screenshot))
                }
            },
            dismissButton = {
                TextButton(onClick = { showInitialDeleteDialog = false }) {
                    Text(stringResource(org.caojun.shotocr.accounting.R.string.keep_screenshot))
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(org.caojun.shotocr.accounting.R.string.confirm_title)) },
                navigationIcon = {
                    TextButton(onClick = {
                        if (askDeleteOnCancel) showCancelDialog = true else onCancel()
                    }) {
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            val screenshotBitmap = remember(receipt.screenshotImage) {
                receipt.screenshotImage?.let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }

            if (screenshotBitmap != null) {
                Image(
                    bitmap = screenshotBitmap.asImageBitmap(),
                    contentDescription = stringResource(org.caojun.shotocr.accounting.R.string.screenshot_desc),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable { showFullscreenImage = true },
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else if (receipt.screenshotUri != Uri.EMPTY) {
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
                        if (screenshotBitmap != null) {
                            Image(
                                bitmap = screenshotBitmap.asImageBitmap(),
                                contentDescription = stringResource(org.caojun.shotocr.accounting.R.string.screenshot_desc),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Image(
                                painter = rememberAsyncImagePainter(receipt.screenshotUri),
                                contentDescription = stringResource(org.caojun.shotocr.accounting.R.string.screenshot_desc),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
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
                    Text(
                        text = stringResource(org.caojun.shotocr.accounting.R.string.bill_info),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = storeName,
                        onValueChange = { storeName = it },
                        label = { Text(stringResource(org.caojun.shotocr.accounting.R.string.store_name)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = sourceApp,
                        onValueChange = { sourceApp = it },
                        label = { Text(stringResource(org.caojun.shotocr.accounting.R.string.source_app)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = paymentTime,
                        onValueChange = { paymentTime = it },
                        label = { Text(stringResource(org.caojun.shotocr.accounting.R.string.payment_time)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = paymentMethod,
                        onValueChange = { paymentMethod = it },
                        label = { Text(stringResource(org.caojun.shotocr.accounting.R.string.payment_method)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = orderNumber,
                        onValueChange = { orderNumber = it },
                        label = { Text(stringResource(org.caojun.shotocr.accounting.R.string.order_number)) },
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
                    val copyTextHint = stringResource(org.caojun.shotocr.accounting.R.string.copy_raw_text)
                    val copyLineHint = stringResource(org.caojun.shotocr.accounting.R.string.copy_line)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { rawTextExpanded = !rawTextExpanded },
                                onLongClick = {
                                    if (receipt.rawText.isNotEmpty()) {
                                        clipboardManager.setText(AnnotatedString(receipt.rawText))
                                        scope.launch { snackbarHostState.showSnackbar(copyTextHint) }
                                    }
                                }
                            )
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(org.caojun.shotocr.accounting.R.string.ocr_raw_text),
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (rawTextExpanded) stringResource(org.caojun.shotocr.accounting.R.string.collapse)
                               else stringResource(org.caojun.shotocr.accounting.R.string.expand),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    AnimatedVisibility(visible = rawTextExpanded) {
                        if (receipt.rawText.isEmpty()) {
                            Text(
                                text = stringResource(org.caojun.shotocr.accounting.R.string.no_text),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                receipt.rawText.lines().forEach { line ->
                                    if (line.isEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                    } else {
                                        Text(
                                            text = line,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    clipboardManager.setText(AnnotatedString(line))
                                                    scope.launch { snackbarHostState.showSnackbar(copyLineHint) }
                                                }
                                                .padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (autoSuggestedCategoryId != null && categoryId == autoSuggestedCategoryId) {
                        Text(
                            text = stringResource(org.caojun.shotocr.accounting.R.string.category_auto_suggested),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    CategorySelectField(
                        categoryId = categoryId,
                        onCategorySelected = { categoryId = it }
                    )
                }
            }

            val currentReceipt = EditableReceipt(
                id = receipt.id,
                screenshotUri = receipt.screenshotUri,
                screenshotImage = receipt.screenshotImage,
                rawText = receipt.rawText,
                amount = amount,
                discount = discount,
                originalAmount = originalAmount,
                storeName = storeName,
                paymentTime = paymentTime,
                paymentMethod = paymentMethod,
                orderNumber = orderNumber,
                sourceApp = sourceApp,
                categoryId = categoryId
            )
            val saveEnabled = receipt.id <= 0 || currentReceipt != receipt

            Button(
                onClick = {
                    scope.launch {
                        showSaveToast = true
                        onSaved(currentReceipt)
                    }
                },
                enabled = saveEnabled,
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

            val changedFieldsJson = buildChangesJson(
                originalAmount = receipt.amount, currentAmount = amount,
                originalDiscount = receipt.discount, currentDiscount = discount,
                originalOriginalAmount = receipt.originalAmount, currentOriginalAmount = originalAmount,
                originalStoreName = receipt.storeName, currentStoreName = storeName,
                originalPaymentTime = receipt.paymentTime, currentPaymentTime = paymentTime,
                originalPaymentMethod = receipt.paymentMethod, currentPaymentMethod = paymentMethod,
                originalOrderNumber = receipt.orderNumber, currentOrderNumber = orderNumber,
                originalSourceApp = receipt.sourceApp, currentSourceApp = sourceApp
            )
            if (receipt.rawText.isNotBlank() && changedFieldsJson != "{}") {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        onRememberCorrection(changedFieldsJson)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(org.caojun.shotocr.accounting.R.string.correction_remembered)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(org.caojun.shotocr.accounting.R.string.remember_correction))
                }
            }
        }
    }
}

/** 构造"被修改字段 → 正确值"的紧凑 JSON（仅包含确实改动且非空的字段）。 */
private fun buildChangesJson(
    originalAmount: String, currentAmount: String,
    originalDiscount: String, currentDiscount: String,
    originalOriginalAmount: String, currentOriginalAmount: String,
    originalStoreName: String, currentStoreName: String,
    originalPaymentTime: String, currentPaymentTime: String,
    originalPaymentMethod: String, currentPaymentMethod: String,
    originalOrderNumber: String, currentOrderNumber: String,
    originalSourceApp: String, currentSourceApp: String
): String {
    val json = org.json.JSONObject()
    fun putIfChanged(key: String, original: String, current: String) {
        val value = current.trim()
        if (value.isNotEmpty() && value != original.trim()) {
            json.put(key, value)
        }
    }
    putIfChanged("amount", originalAmount, currentAmount)
    putIfChanged("discount", originalDiscount, currentDiscount)
    putIfChanged("originalAmount", originalOriginalAmount, currentOriginalAmount)
    putIfChanged("storeName", originalStoreName, currentStoreName)
    putIfChanged("paymentTime", originalPaymentTime, currentPaymentTime)
    putIfChanged("paymentMethod", originalPaymentMethod, currentPaymentMethod)
    putIfChanged("orderNumber", originalOrderNumber, currentOrderNumber)
    putIfChanged("sourceApp", originalSourceApp, currentSourceApp)
    return json.toString()
}
