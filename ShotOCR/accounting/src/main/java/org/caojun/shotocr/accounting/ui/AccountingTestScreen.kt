package org.caojun.shotocr.accounting.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.caojun.shotocr.accounting.AccountingManager
import org.caojun.shotocr.accounting.EditableReceipt

class AccountingTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AccountingTestScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountingTestScreen() {
    val records by AccountingManager.records.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(org.caojun.shotocr.accounting.R.string.accounting_records)) })
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
                    RecordCard(receipt = receipt, index = index)
                }
            }
        }
    }
}

@Composable
fun RecordCard(receipt: EditableReceipt, index: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(org.caojun.shotocr.accounting.R.string.record_number, index + 1),
                    fontSize = 14.sp
                )
                TextButton(onClick = { AccountingManager.delete(index) }) {
                    Text(
                        stringResource(org.caojun.shotocr.accounting.R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
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
            if (receipt.items.isNotEmpty()) {
                Text(
                    text = stringResource(org.caojun.shotocr.accounting.R.string.items_count, receipt.items.size),
                    fontSize = 14.sp
                )
            }
            Text(
                text = receipt.rawText.take(100) + if (receipt.rawText.length > 100) "..." else "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
