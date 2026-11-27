package org.caojun.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import org.caojun.widgets.segmented.SegmentedOption
import org.caojun.widgets.segmented.SegmentedSelector

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
            SegmentedSelector(
                options = listOf(
                    SegmentedOption(
                        PlayerType.WEB,
                        stringResource(R.string.player_web_short),
                    ),
                    SegmentedOption(
                        PlayerType.MEDIA3,
                        stringResource(R.string.player_media3_short),
                    ),
                    SegmentedOption(
                        PlayerType.EXTERNAL,
                        stringResource(R.string.player_external_short),
                    ),
                ),
                selected = selected,
                onSelected = {
                    selected = it
                    preferences.defaultPlayer = it
                },
            )
        }
    }
}
