package org.caojun.shotocr.accounting.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import org.caojun.shotocr.accounting.AccountingSettings
import org.caojun.shotocr.accounting.R

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var weekStart by remember { mutableStateOf(AccountingSettings.getWeekStart(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.week_start),
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            WeekStartOption(
                label = stringResource(R.string.monday),
                selected = weekStart == DayOfWeek.MONDAY,
                onClick = {
                    weekStart = DayOfWeek.MONDAY
                    AccountingSettings.setWeekStart(context, DayOfWeek.MONDAY)
                }
            )
            WeekStartOption(
                label = stringResource(R.string.sunday),
                selected = weekStart == DayOfWeek.SUNDAY,
                onClick = {
                    weekStart = DayOfWeek.SUNDAY
                    AccountingSettings.setWeekStart(context, DayOfWeek.SUNDAY)
                }
            )
        }
    }
}

@Composable
private fun WeekStartOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, fontSize = 16.sp)
    }
}