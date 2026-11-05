package org.caojun.autoocrtask.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.caojun.autoocrtask.R
import org.caojun.expandablecard.ExpandableCard

@Composable
fun MainScreen(onOpenTasks: () -> Unit) {
    val shizukuState = rememberShizukuState()
    val statusText = stringResource(shizukuStatusRes(shizukuState))
    val statusColor = when (shizukuState) {
        ShizukuState.READY -> MaterialTheme.colorScheme.primary
        ShizukuState.CHECKING -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.error
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_title),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.home_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Column(
                    modifier = Modifier.padding(start = 16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = stringResource(R.string.home_status_label),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = statusText,
                        fontSize = 13.sp,
                        color = statusColor,
                        textAlign = TextAlign.End
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ExpandableCard(
                        title = stringResource(R.string.guide_title)
                    ) {
                        Text(
                            text = stringResource(R.string.guide_1),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = stringResource(R.string.guide_2),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = stringResource(R.string.guide_3),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = stringResource(R.string.guide_4),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = stringResource(R.string.guide_5),
                            fontSize = 14.sp
                        )
                    }

                    ShizukuStatusCard()

                    OverlayPermissionCard()

                    NotificationPermissionCard()

                    UsageAccessCard()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = onOpenTasks,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.tasks_title))
                }
                Text(
                    text = stringResource(R.string.powered_by),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                )
            }
        }
    }
}

private fun shizukuStatusRes(state: ShizukuState): Int = when (state) {
    ShizukuState.CHECKING -> R.string.status_checking
    ShizukuState.NOT_INSTALLED -> R.string.status_not_installed
    ShizukuState.NOT_RUNNING -> R.string.status_not_running
    ShizukuState.NOT_GRANTED -> R.string.status_not_granted
    ShizukuState.READY -> R.string.status_ready
}
