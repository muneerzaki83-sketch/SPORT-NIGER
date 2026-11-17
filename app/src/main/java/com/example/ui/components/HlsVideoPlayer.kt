package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.localization.LocalStrings
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.BrightBlueAccent
import com.example.ui.theme.CardNavySurface
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PureWhiteText
import com.example.ui.theme.SecondaryBlack
import com.example.ui.theme.SoftSteelText

enum class StreamQualityOption(val label: String, val maxWidth: Int, val maxHeight: Int, val maxBitrate: Int) {
    AUTO("Auto", Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE),
    HD_1080("1080p", 1920, 1080, 5_000_000),
    HD_720("720p", 1280, 720, 2_800_000),
    SD_480("480p", 854, 480, 1_400_000),
    LOW_360("360p", 640, 360, 700_000)
}

@OptIn(UnstableApi::class)
@Composable
fun HlsVideoPlayer(
    playbackUrl: String,
    streamType: String,
    youtubeVideoId: String?,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalStrings.current

    if (streamType == "YOUTUBE_LIVE" || playbackUrl.contains("youtube.com") || playbackUrl.contains("youtu.be")) {
        val targetUrl = if (!youtubeVideoId.isNullOrBlank()) {
            "https://www.youtube.com/watch?v=$youtubeVideoId"
        } else {
            playbackUrl
        }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(SecondaryBlack),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "YouTube Live Stream",
                    style = MaterialTheme.typography.titleLarge,
                    color = PureWhiteText
                )
                Text(
                    text = targetUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftSteelText
                )
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("open_youtube_live_button")
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.openYoutubeLive)
                }
            }
        }
        return
    }

    var isBuffering by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorDetail by remember { mutableStateOf<String?>(null) }
    var selectedQuality by remember { mutableStateOf(StreamQualityOption.AUTO) }
    var showQualityMenu by remember { mutableStateOf(false) }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
                if (playbackState == Player.STATE_READY) {
                    hasError = false
                    errorDetail = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                hasError = true
                errorDetail = error.localizedMessage
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(playbackUrl) {
        if (playbackUrl.isBlank()) {
            hasError = true
            isBuffering = false
            return@LaunchedEffect
        }
        hasError = false
        isBuffering = true
        val mediaItemBuilder = MediaItem.Builder().setUri(playbackUrl)
        if (playbackUrl.contains(".m3u8", ignoreCase = true) || streamType == "HLS" || streamType == "RTMP_HLS") {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        exoPlayer.setMediaItem(mediaItemBuilder.build())
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(selectedQuality) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setMaxVideoSize(selectedQuality.maxWidth, selectedQuality.maxHeight)
            .setMaxVideoBitrate(selectedQuality.maxBitrate)
            .build()
    }

    Box(
        modifier = modifier
            .then(
                if (isFullscreen) Modifier.fillMaxSize()
                else Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
            .background(SecondaryBlack)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    keepScreenOn = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) {
                    view.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering && !hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(color = BrightBlueAccent)
                    Text(
                        text = strings.streamLoading,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PureWhiteText
                    )
                }
            }
        }

        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CardNavySurface.copy(alpha = 0.94f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = LiveCrimson,
                        modifier = Modifier.size(34.dp)
                    )
                    Text(
                        text = errorDetail ?: strings.streamConnectionError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PureWhiteText
                    )
                    Button(
                        onClick = {
                            hasError = false
                            isBuffering = true
                            exoPlayer.prepare()
                            exoPlayer.playWhenReady = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("retry_stream_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.retryStream)
                    }
                }
            }
        }

        // Top-right overlay controls: Quality selector & Fullscreen
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, BorderSlate)
                ) {
                    IconButton(
                        onClick = { showQualityMenu = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("stream_quality_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HighQuality,
                            contentDescription = strings.streamQuality,
                            tint = PureWhiteText
                        )
                    }
                }
                DropdownMenu(
                    expanded = showQualityMenu,
                    onDismissRequest = { showQualityMenu = false }
                ) {
                    StreamQualityOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (option == StreamQualityOption.AUTO) strings.qualityAuto else option.label,
                                    color = if (option == selectedQuality) BrightBlueAccent else PureWhiteText
                                )
                            },
                            onClick = {
                                selectedQuality = option
                                showQualityMenu = false
                            }
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, BorderSlate)
            ) {
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("stream_fullscreen_button")
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) strings.exitFullscreen else strings.fullscreen,
                        tint = PureWhiteText
                    )
                }
            }
        }
    }
}
