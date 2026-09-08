package org.caojun.shotocr.accounting.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.caojun.shotocr.accounting.AccountingManager
import org.caojun.shotocr.accounting.EditableReceipt

class BillListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BillListScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillListScreen(
    onBack: () -> Unit = {}
) {
    val records by AccountingManager.records.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(org.caojun.shotocr.accounting.R.string.accounting_records)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(org.caojun.shotocr.accounting.R.string.cancel))
                    }
                }
            )
        }
    ) { padding ->
        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(org.caojun.shotocr.accounting.R.string.no_records),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                itemsIndexed(records) { index, receipt ->
                    BillCard(receipt = receipt, index = index)
                }
            }
        }
    }
}

@Composable
fun BillCard(receipt: EditableReceipt, index: Int) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.startActivity(
                    Intent(context, ConfirmActivity::class.java).apply {
                        putExtra("receipt_id", receipt.id)
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val thumbnailBitmap = remember(receipt.screenshotImage) {
                receipt.screenshotImage?.let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }

            if (thumbnailBitmap != null) {
                Image(
                    bitmap = thumbnailBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clickable {
                            context.startActivity(
                                Intent(context, ConfirmActivity::class.java).apply {
                                    putExtra("receipt_id", receipt.id)
                                }
                            )
                        },
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (receipt.storeName.isNotEmpty()) receipt.storeName
                        else stringResource(org.caojun.shotocr.accounting.R.string.record_number, index + 1),
                        fontSize = 16.sp
                    )
                    TextButton(onClick = {
                        showDeleteDialog = true
                    }) {
                        Text(
                            stringResource(org.caojun.shotocr.accounting.R.string.delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (receipt.amount.isNotEmpty()) {
                    Text(
                        text = stringResource(org.caojun.shotocr.accounting.R.string.paid_amount, receipt.amount),
                        fontSize = 14.sp
                    )
                }
                if (receipt.discount.isNotEmpty()) {
                    Text(
                        text = stringResource(org.caojun.shotocr.accounting.R.string.discount_display, receipt.discount),
                        fontSize = 14.sp
                    )
                }
                if (receipt.paymentTime.isNotEmpty()) {
                    Text(
                        text = stringResource(org.caojun.shotocr.accounting.R.string.payment_time_display, receipt.paymentTime),
                        fontSize = 14.sp
                    )
                }
                if (receipt.paymentMethod.isNotEmpty()) {
                    Text(
                        text = stringResource(org.caojun.shotocr.accounting.R.string.payment_method_display, receipt.paymentMethod),
                        fontSize = 14.sp
                    )
                }
                if (receipt.orderNumber.isNotEmpty()) {
                    Text(
                        text = stringResource(org.caojun.shotocr.accounting.R.string.order_number_display, receipt.orderNumber),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(org.caojun.shotocr.accounting.R.string.confirm_delete_title)) },
            text = { Text(stringResource(org.caojun.shotocr.accounting.R.string.confirm_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        AccountingManager.delete(receipt)
                    }
                    showDeleteDialog = false
                }) {
                    Text(
                        stringResource(org.caojun.shotocr.accounting.R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(org.caojun.shotocr.accounting.R.string.cancel))
                }
            }
        )
    }
}
