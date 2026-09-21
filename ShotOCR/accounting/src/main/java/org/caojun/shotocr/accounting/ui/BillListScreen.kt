package org.caojun.shotocr.accounting.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.caojun.shotocr.accounting.AccountingManager
import org.caojun.shotocr.accounting.AccountingSettings
import org.caojun.shotocr.accounting.EditableReceipt
import org.caojun.shotocr.accounting.MonthSummary
import org.caojun.shotocr.accounting.ReceiptTotals
import org.caojun.shotocr.accounting.WeekSummary
import org.caojun.shotocr.accounting.buildGroupedData
import org.caojun.shotocr.accounting.formatAmount
import org.caojun.shotocr.accounting.R
import java.math.BigDecimal
import java.math.RoundingMode

class BillListActivity : ComponentActivity() {

    private var resumeTick by mutableStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeTick += 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BillListScreen(
                    resumeTick = resumeTick,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillListScreen(
    onBack: () -> Unit = {},
    resumeTick: Int = 0
) {
    val records by AccountingManager.records.collectAsState()
    val context = LocalContext.current
    val weekStart by remember(resumeTick) {
        mutableStateOf(AccountingSettings.getWeekStart(context))
    }
    val groupedData = remember(records, weekStart) {
        buildGroupedData(records, weekStart)
    }

    val expandedMonths = remember { mutableStateMapOf<String, Boolean>() }
    val expandedWeeks = remember { mutableStateMapOf<String, Boolean>() }
    val unclassifiedExpanded = remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accounting_records)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Text(stringResource(R.string.settings))
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
                    text = stringResource(R.string.no_records),
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
                groupedData.months.forEach { month ->
                    item(key = "month-${month.key}") {
                        MonthSummaryRow(
                            month = month,
                            expanded = expandedMonths[month.key] == true,
                            onClick = { expandedMonths[month.key] = expandedMonths[month.key] != true }
                        )
                    }
                    if (expandedMonths[month.key] == true) {
                        month.weeks.forEach { week ->
                            item(key = "week-${week.key}") {
                                WeekSummaryRow(
                                    week = week,
                                    expanded = expandedWeeks[week.key] == true,
                                    onClick = { expandedWeeks[week.key] = expandedWeeks[week.key] != true }
                                )
                            }
                            if (expandedWeeks[week.key] == true) {
                                items(week.receipts, key = { "receipt-${month.key}-${it.id}" }) { receipt ->
                                    BillCard(
                                        receipt = receipt,
                                        index = records.indexOfFirst { it.id == receipt.id }
                                    )
                                }
                            }
                        }
                    }
                }

                if (groupedData.unclassified.isNotEmpty()) {
                    item(key = "unclassified-header") {
                        SummaryCard(
                            title = stringResource(R.string.unclassified_records),
                            subtitle = "",
                            totals = groupedData.unclassifiedTotals,
                            expanded = unclassifiedExpanded.value,
                            onClick = { unclassifiedExpanded.value = !unclassifiedExpanded.value }
                        )
                    }
                    if (unclassifiedExpanded.value) {
                        items(groupedData.unclassified, key = { "unclassified-${it.id}" }) { receipt ->
                            BillCard(
                                receipt = receipt,
                                index = records.indexOfFirst { it.id == receipt.id }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSummaryRow(
    month: MonthSummary,
    expanded: Boolean,
    onClick: () -> Unit
) {
    SummaryCard(
        title = month.label,
        subtitle = month.rangeLabel,
        totals = month.totals,
        expanded = expanded,
        onClick = onClick
    )
}

@Composable
private fun WeekSummaryRow(
    week: WeekSummary,
    expanded: Boolean,
    onClick: () -> Unit
) {
    SummaryCard(
        title = week.label,
        subtitle = "",
        totals = week.totals,
        expanded = expanded,
        onClick = onClick
    )
}

@Composable
private fun SummaryCard(
    title: String,
    subtitle: String,
    totals: ReceiptTotals,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(if (expanded) R.string.collapse else R.string.expand),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.summary_count_amount,
                    totals.count,
                    formatAmount(totals.totalAmount)
                ),
                fontSize = 13.sp
            )
            if (totals.totalOrderAmount.signum() != 0) {
                if (totals.totalDiscount.signum() != 0) {
                    val rate = totals.discountRate
                        .multiply(BigDecimal(100))
                        .setScale(1, RoundingMode.HALF_UP)
                        .toPlainString()
                    Text(
                        text = stringResource(
                            R.string.summary_order_discount,
                            formatAmount(totals.totalOrderAmount),
                            formatAmount(totals.totalDiscount),
                            rate
                        ),
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.summary_order_no_discount,
                            formatAmount(totals.totalOrderAmount)
                        ),
                        fontSize = 13.sp
                    )
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
                        else stringResource(R.string.record_number, index + 1),
                        fontSize = 16.sp
                    )
                    TextButton(onClick = {
                        showDeleteDialog = true
                    }) {
                        Text(
                            stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (receipt.amount.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.paid_amount, receipt.amount),
                        fontSize = 14.sp
                    )
                }
                if (receipt.discount.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.discount_display, receipt.discount),
                        fontSize = 14.sp
                    )
                }
                if (receipt.paymentTime.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.payment_time_display, receipt.paymentTime),
                        fontSize = 14.sp
                    )
                }
                if (receipt.paymentMethod.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.payment_method_display, receipt.paymentMethod),
                        fontSize = 14.sp
                    )
                }
                if (receipt.orderNumber.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.order_number_display, receipt.orderNumber),
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
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        AccountingManager.delete(receipt)
                    }
                    showDeleteDialog = false
                }) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}