@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.caojun.player

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.util.Locale

/** Media3 / ExoPlayer 播放页，供宿主通过 [PlayerLauncher] 启动。 */
class Media3PlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private var historyKey: String = ""
    private var episodeKey: String = ""
    private var episodeName: String = ""
    private var lastSavedPosition: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        episodeName = intent.getStringExtra(EXTRA_EPISODE).orEmpty()
        historyKey = intent.getStringExtra(EXTRA_HISTORY_KEY).orEmpty()
        episodeKey = intent.getStringExtra(EXTRA_EPISODE_KEY).orEmpty()
        val referer = intent.getStringExtra(EXTRA_REFERER).orEmpty()
        val startMs = intent.getLongExtra(EXTRA_START_MS, 0L)

        if (historyKey.isNotBlank()) {
            PlayerProgressBus.listener?.onLastPlayerChanged(historyKey, PlayerType.MEDIA3)
            PlayerProgressBus.listener?.onVideoUrl(historyKey, url)
        }

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to referer,
                    "User-Agent" to DEFAULT_UA,
                )
            )
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                if (startMs > 10_000L) seekTo(startMs)
                playWhenReady = true
            }

        setContent {
            PlayerTheme {
                PlayerScreen(
                    player = player,
                    title = title.ifBlank { episodeName },
                    onSaveProgress = { saveProgress() },
                    onFullscreenChange = { fullscreen -> applyFullscreen(fullscreen) },
                    onBack = { finish() },
                )
            }
        }
    }

    private fun applyFullscreen(fullscreen: Boolean) {
        requestedOrientation = if (fullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun saveProgress() {
        if (!::player.isInitialized) return
        if (historyKey.isBlank() || episodeKey.isBlank()) return
        val duration = player.duration
        if (duration <= 0L) return
        val position = player.currentPosition.coerceAtLeast(0L)
        if (position == lastSavedPosition) return
        lastSavedPosition = position
        PlayerProgressBus.listener?.onProgress(
            key = historyKey,
            episodeKey = episodeKey,
            episodeName = episodeName,
            positionMs = position,
            durationMs = duration,
        )
    }

    override fun onPause() {
        super.onPause()
        if (::player.isInitialized) {
            saveProgress()
            player.pause()
        }
    }

    override fun onDestroy() {
        if (::player.isInitialized) {
            saveProgress()
            player.release()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_EPISODE = "episode"
        const val EXTRA_HISTORY_KEY = "history_key"
        const val EXTRA_EPISODE_KEY = "episode_key"
        const val EXTRA_REFERER = "referer"
        const val EXTRA_START_MS = "start_ms"

        private const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}

@Composable
private fun PlayerScreen(
    player: ExoPlayer,
    title: String,
    onSaveProgress: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1f) }
    var error by remember { mutableStateOf<String?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                playing = value
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                error = e.message ?: "playback error"
            }
        }
        player.addListener(listener)
        try {
            awaitCancellation()
        } finally {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            if (duration <= 0L) duration = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            onSaveProgress()
        }
    }

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor.Black),
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    this.player = player
                    setBackgroundColor(Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.player_back), color = ComposeColor.White)
            }
            Text(
                text = title,
                color = ComposeColor.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Text(
                text = "${formatDuration(position)} / ${formatDuration(duration)}",
                color = ComposeColor.White,
                fontSize = 12.sp,
            )
            Slider(
                value = if (duration > 0L) position.toFloat() / duration else 0f,
                onValueChange = { fraction ->
                    if (duration > 0L) {
                        val target = (fraction * duration).toLong()
                        position = target
                        player.seekTo(target)
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                }) {
                    Text("-10s", color = ComposeColor.White)
                }
                TextButton(onClick = {
                    if (player.isPlaying) player.pause() else player.play()
                }) {
                    Text(
                        text = stringResource(
                            if (playing) R.string.player_pause else R.string.player_play
                        ),
                        color = ComposeColor.White,
                    )
                }
                TextButton(onClick = {
                    player.seekTo(player.currentPosition + 10_000L)
                }) {
                    Text("+10s", color = ComposeColor.White)
                }
                TextButton(onClick = {
                    speed = when (speed) {
                        1f -> 1.25f
                        1.25f -> 1.5f
                        1.5f -> 2f
                        else -> 1f
                    }
                    player.setPlaybackSpeed(speed)
                }) {
                    Text("${speed}x", color = ComposeColor.White)
                }
                TextButton(onClick = {
                    val next = !isFullscreen
                    isFullscreen = next
                    onFullscreenChange(next)
                }) {
                    Text(
                        text = stringResource(
                            if (isFullscreen) {
                                R.string.player_exit_fullscreen
                            } else {
                                R.string.player_fullscreen
                            }
                        ),
                        color = ComposeColor.White,
                    )
                }
            }
        }

        error?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.player_error),
                    color = ComposeColor.White,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = message,
                    color = ComposeColor.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack) {
                    Text(stringResource(R.string.player_back))
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
