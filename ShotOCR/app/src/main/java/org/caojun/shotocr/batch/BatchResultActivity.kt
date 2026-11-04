package org.caojun.shotocr.batch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.caojun.shotocr.R
import org.caojun.shotocr.accounting.ui.BillListActivity

class BatchResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BatchResultScreen(
                    result = BatchResultStore.last,
                    onClose = { finish() },
                    onViewRecords = {
                        startActivity(Intent(this, BillListActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchResultScreen(
    result: BatchResult?,
    onClose: () -> Unit,
    onViewRecords: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.batch_result_title)) },
                navigationIcon = {
                    TextButton(onClick = onClose) {
                        Text(stringResource(R.string.batch_result_close))
                    }
                }
            )
        }
    ) { padding ->
        if (result == null) {
            Text(
                text = stringResource(R.string.batch_result_empty),
                modifier = Modifier.padding(padding).padding(24.dp)
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.batch_result_counts,
                    result.success,
                    result.duplicate,
                    result.income,
                    result.skipped,
                    result.failed
                ),
                style = MaterialTheme.typography.titleMedium
            )
            result.message?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (result.failures.isEmpty()) {
                Text(
                    text = stringResource(R.string.batch_result_no_failures),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(result.failures) { failure ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.batch_result_failure_item,
                                        failure.index,
                                        failureReasonLabel(failure.reason)
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (failure.summary.isNotBlank()) {
                                    Text(
                                        text = failure.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                failure.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                                    Text(
                                        text = detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Button(
                onClick = onViewRecords,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.batch_result_view_records))
            }
        }
    }
}

@Composable
private fun failureReasonLabel(reason: BatchFailureReason): String = stringResource(
    when (reason) {
        BatchFailureReason.TAP_FAILED -> R.string.batch_failure_tap
        BatchFailureReason.DETAIL_MISSING -> R.string.batch_failure_detail_missing
        BatchFailureReason.DETAIL_INCOMPLETE -> R.string.batch_failure_detail_incomplete
        BatchFailureReason.SCREENSHOT_FAILED -> R.string.batch_failure_screenshot
        BatchFailureReason.OCR_OR_AI_FAILED -> R.string.batch_failure_ocr_ai
        BatchFailureReason.NOT_RECEIPT -> R.string.batch_failure_not_receipt
        BatchFailureReason.SAVE_FAILED -> R.string.batch_failure_save
        BatchFailureReason.RETURN_LIST_FAILED -> R.string.batch_failure_return_list
        BatchFailureReason.UNKNOWN -> R.string.batch_failure_unknown
    }
)
