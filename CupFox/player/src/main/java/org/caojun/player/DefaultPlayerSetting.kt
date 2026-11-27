package org.caojun.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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

/** 设置页里的「默认播放器」卡片。 */
@Composable
fun DefaultPlayerSetting(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = remember { PlayerPreferences(context) }
    var selected by remember { mutableStateOf(preferences.defaultPlayer) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.player_default_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.player_default_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selected == PlayerType.WEB,
                    onClick = {
                        selected = PlayerType.WEB
                        preferences.defaultPlayer = PlayerType.WEB
                    },
                    label = { Text(stringResource(R.string.player_web_short)) },
                )
                FilterChip(
                    selected = selected == PlayerType.MEDIA3,
                    onClick = {
                        selected = PlayerType.MEDIA3
                        preferences.defaultPlayer = PlayerType.MEDIA3
                    },
                    label = { Text(stringResource(R.string.player_media3_short)) },
                )
                FilterChip(
                    selected = selected == PlayerType.EXTERNAL,
                    onClick = {
                        selected = PlayerType.EXTERNAL
                        preferences.defaultPlayer = PlayerType.EXTERNAL
                    },
                    label = { Text(stringResource(R.string.player_external_short)) },
                )
            }
        }
    }
}
