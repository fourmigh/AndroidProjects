package org.caojun.shotocr.batch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import org.caojun.shotocr.R

/** 批量识别入口：选年月后自动启动服务并打开支付宝，不再要求手动点悬浮按钮。 */
@Composable
fun BatchCaptureCard(
    shizukuReady: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(YearMonth.now()) }
    var yearMenu by remember { mutableStateOf(false) }
    var monthMenu by remember { mutableStateOf(false) }
    val years = remember {
        (YearMonth.now().year - 5..YearMonth.now().year).toList().reversed()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.batch_capture_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.batch_capture_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { yearMenu = true }) {
                        Text(stringResource(R.string.batch_capture_year, selected.year))
                    }
                    DropdownMenu(
                        expanded = yearMenu,
                        onDismissRequest = { yearMenu = false }
                    ) {
                        years.forEach { year ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.batch_capture_year, year)) },
                                onClick = {
                                    selected = selected.withYear(year)
                                    yearMenu = false
                                }
                            )
                        }
                    }
                }
                Box {
                    OutlinedButton(onClick = { monthMenu = true }) {
                        Text(stringResource(R.string.batch_capture_month, selected.monthValue))
                    }
                    DropdownMenu(
                        expanded = monthMenu,
                        onDismissRequest = { monthMenu = false }
                    ) {
                        (1..12).forEach { month ->
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.batch_capture_month, month))
                                },
                                onClick = {
                                    selected = selected.withMonth(month)
                                    monthMenu = false
                                }
                            )
                        }
                    }
                }
            }

            if (!shizukuReady) {
                Text(
                    text = stringResource(R.string.batch_capture_need_shizuku),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = { BatchAutoStart.start(context, selected) },
                enabled = shizukuReady
            ) {
                Text(stringResource(R.string.batch_capture_arm))
            }
        }
    }
}
