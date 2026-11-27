package org.caojun.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** 播放方式选择菜单，供宿主复用。 */
@Composable
fun PlaybackMethodDialog(
    title: String,
    subtitle: String,
    progressText: String?,
    hasVideo: Boolean,
    onDismiss: () -> Unit,
    onWeb: () -> Unit,
    onMedia3: () -> Unit,
    onExternal: () -> Unit,
    onCopy: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progressText != null) {
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onWeb,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.player_web))
                }
                TextButton(
                    onClick = onMedia3,
                    enabled = hasVideo,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.player_media3))
                }
                TextButton(
                    onClick = onExternal,
                    enabled = hasVideo,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.player_external))
                }
                TextButton(
                    onClick = onCopy,
                    enabled = hasVideo,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.player_copy))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.player_cancel))
            }
        },
    )
}
