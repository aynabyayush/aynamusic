package com.ayush.aynamusic.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ayush.aynamusic.PlaybackUiState
import com.ayush.aynamusic.data.model.DownloadState
import com.ayush.aynamusic.data.model.Track
import com.ayush.aynamusic.ui.components.AynaArtwork
import com.ayush.aynamusic.ui.components.EmptyState
import com.ayush.aynamusic.ui.components.SourceBadge
import com.ayush.aynamusic.ui.theme.Acid
import com.ayush.aynamusic.util.asClock
import java.util.Locale

@Composable
fun NowPlayingScreen(
    track: Track?,
    playback: PlaybackUiState,
    equalizerEnabled: Boolean,
    onBack: () -> Unit,
    onMore: (Track) -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onFavorite: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onSleepTimer: () -> Unit,
    onSpeed: () -> Unit,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    onEqualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        KineticAmbientGlow(isPlaying = playback.isPlaying)

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val availableWidth = maxWidth
            val availableHeight = maxHeight
            val landscape = availableWidth > availableHeight * 1.08f

            Column(Modifier.fillMaxSize()) {
                InnovativePlayerTopBar(
                    track = track,
                    onBack = onBack,
                    onMore = { track?.let(onMore) }
                )

                if (track == null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            title = "Nothing playing",
                            message = "Choose a track from Home, Search or your Library.",
                            icon = Icons.Rounded.PlayArrow
                        )
                    }
                } else if (landscape) {
                    LandscapeInnovativePlayer(
                        track = track,
                        playback = playback,
                        equalizerEnabled = equalizerEnabled,
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek,
                        onShuffle = onShuffle,
                        onRepeat = onRepeat,
                        onFavorite = onFavorite,
                        onDownload = onDownload,
                        onSleepTimer = onSleepTimer,
                        onSpeed = onSpeed,
                        onLyrics = onLyrics,
                        onQueue = onQueue,
                        onEqualizer = onEqualizer
                    )
                } else {
                    PortraitInnovativePlayer(
                        track = track,
                        playback = playback,
                        availableHeight = availableHeight,
                        equalizerEnabled = equalizerEnabled,
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onSeek = onSeek,
                        onShuffle = onShuffle,
                        onRepeat = onRepeat,
                        onFavorite = onFavorite,
                        onDownload = onDownload,
                        onSleepTimer = onSleepTimer,
                        onSpeed = onSpeed,
                        onLyrics = onLyrics,
                        onQueue = onQueue,
                        onEqualizer = onEqualizer
                    )
                }
            }
        }
    }
}

@Composable
private fun KineticAmbientGlow(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.26f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(420.dp)
            .graphicsLayer { alpha = if (isPlaying) pulseAlpha else 0.12f }
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    radius = 900f
                )
            )
    )
}

@Composable
private fun InnovativePlayerTopBar(
    track: Track?,
    onBack: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Close player",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = track?.sourceLabel?.uppercase() ?: "ACOUSTIC DECK",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Surface(
            onClick = onMore,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.MoreHoriz,
                    contentDescription = "Track options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PortraitInnovativePlayer(
    track: Track,
    playback: PlaybackUiState,
    availableHeight: Dp,
    equalizerEnabled: Boolean,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onFavorite: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onSleepTimer: () -> Unit,
    onSpeed: () -> Unit,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    onEqualizer: () -> Unit
) {
    val deckSize = minOf(320.dp, (availableHeight - 380.dp).coerceAtLeast(180.dp))

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            InnovativeVinylDeck(track = track, isPlaying = playback.isPlaying, size = deckSize)
        }

        InnovativeTrackHeader(
            track = track,
            onFavorite = onFavorite,
            onDownload = onDownload
        )

        Spacer(Modifier.height(14.dp))

        WaveformScrubber(
            playback = playback,
            trackDuration = track.durationMs,
            onSeek = onSeek
        )

        Spacer(Modifier.height(12.dp))

        SculptedControlDeck(
            playback = playback,
            onTogglePlayPause = onTogglePlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onShuffle = onShuffle,
            onRepeat = onRepeat
        )

        Spacer(Modifier.height(14.dp))

        FloatingUtilityBar(
            playback = playback,
            equalizerEnabled = equalizerEnabled,
            onSleepTimer = onSleepTimer,
            onSpeed = onSpeed,
            onLyrics = onLyrics,
            onQueue = onQueue,
            onEqualizer = onEqualizer
        )

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun LandscapeInnovativePlayer(
    track: Track,
    playback: PlaybackUiState,
    equalizerEnabled: Boolean,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onFavorite: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onSleepTimer: () -> Unit,
    onSpeed: () -> Unit,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    onEqualizer: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            InnovativeVinylDeck(track = track, isPlaying = playback.isPlaying, size = 260.dp)
        }
        Column(
            modifier = Modifier
                .weight(1.1f)
                .padding(start = 28.dp)
        ) {
            InnovativeTrackHeader(
                track = track,
                onFavorite = onFavorite,
                onDownload = onDownload
            )
            Spacer(Modifier.weight(1f))
            WaveformScrubber(
                playback = playback,
                trackDuration = track.durationMs,
                onSeek = onSeek
            )
            Spacer(Modifier.height(10.dp))
            SculptedControlDeck(
                playback = playback,
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onShuffle = onShuffle,
                onRepeat = onRepeat
            )
            Spacer(Modifier.height(10.dp))
            FloatingUtilityBar(
                playback = playback,
                equalizerEnabled = equalizerEnabled,
                onSleepTimer = onSleepTimer,
                onSpeed = onSpeed,
                onLyrics = onLyrics,
                onQueue = onQueue,
                onEqualizer = onEqualizer
            )
        }
    }
}

@Composable
private fun InnovativeVinylDeck(
    track: Track,
    isPlaying: Boolean,
    size: Dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vinylRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing)
        ),
        label = "rotationAngle"
    )
    val currentRotation = if (isPlaying) rotationAngle else 0f
    val sleeveSize = size * 0.82f
    val vinylSize = size * 0.88f
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Spinning Vinyl Disc Behind
        Box(
            modifier = Modifier
                .size(vinylSize)
                .offset(x = 22.dp, y = (-8).dp)
                .rotate(currentRotation)
                .shadow(16.dp, CircleShape)
                .background(Color(0xFF0F1115), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.toPx() / 2f
                // Vinyl Groove Lines
                for (r in listOf(0.88f, 0.76f, 0.64f, 0.52f, 0.40f)) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.07f),
                        radius = radius * r,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
                // Vinyl Sheen Arc
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.01f),
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.01f),
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.01f)
                        )
                    ),
                    radius = radius * 0.94f
                )
            }
            // Center Vinyl Artwork Label
            Box(
                modifier = Modifier
                    .size(vinylSize * 0.35f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                AynaArtwork(track = track, corner = 0.dp, modifier = Modifier.fillMaxSize())
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Color(0xFF050608), CircleShape)
            )
        }

        // Album Artwork Sleeve Front
        AynaArtwork(
            track = track,
            corner = 24.dp,
            modifier = Modifier
                .size(sleeveSize)
                .offset(x = (-22).dp, y = 8.dp)
                .shadow(
                    elevation = 28.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color.Black.copy(alpha = 0.4f),
                    spotColor = primaryColor.copy(alpha = 0.3f)
                )
        )

        // Tonearm Stylus Indicator
        Canvas(
            modifier = Modifier
                .size(size)
                .graphicsLayer { alpha = 0.85f }
        ) {
            val start = Offset(size.toPx() * 0.86f, size.toPx() * 0.08f)
            val end = Offset(size.toPx() * 0.68f, size.toPx() * 0.36f)
            drawLine(
                color = primaryColor.copy(alpha = 0.6f),
                start = start,
                end = end,
                strokeWidth = 3.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            )
            drawCircle(
                color = primaryColor,
                radius = 5.dp.toPx(),
                center = end
            )
        }
    }
}

@Composable
private fun InnovativeTrackHeader(
    track: Track,
    onFavorite: (Track) -> Unit,
    onDownload: (Track) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val artistAndAlbum = buildString {
                append(track.artist)
                if (!track.album.isNullOrBlank()) append("  ·  ${track.album}")
            }
            Text(
                text = artistAndAlbum,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SourceBadge(track = track, modifier = Modifier)
            }
        }
        Surface(
            onClick = { onFavorite(track) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (track.isFavorite) "Remove from Liked" else "Like track",
                    tint = if (track.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (!track.isLocal) {
            DownloadAction(track = track, onDownload = onDownload)
        }
    }
}

@Composable
private fun DownloadAction(track: Track, onDownload: (Track) -> Unit) {
    when (track.downloadState) {
        DownloadState.QUEUED, DownloadState.DOWNLOADING -> Box(
            Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        DownloadState.DOWNLOADED -> Surface(
            onClick = { onDownload(track) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.DownloadDone,
                    contentDescription = "Remove download",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
        else -> Surface(
            onClick = { onDownload(track) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Download,
                    contentDescription = "Download track",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

@Composable
private fun WaveformScrubber(
    playback: PlaybackUiState,
    trackDuration: Long,
    onSeek: (Long) -> Unit
) {
    val duration = playback.durationMs.takeIf { it > 0 } ?: trackDuration
    var scrubPosFraction by remember(playback.currentTrack?.id) { mutableStateOf<Float?>(null) }
    val currentProgress = if (duration > 0) {
        (scrubPosFraction ?: (playback.positionMs.toFloat() / duration)).coerceIn(0f, 1f)
    } else 0f

    val primaryColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest

    // Generates a visual audio spectrum curve pattern
    val barHeights = remember {
        listOf(
            0.35f, 0.55f, 0.85f, 0.40f, 0.65f, 0.95f, 0.50f, 0.75f,
            0.30f, 0.80f, 0.60f, 1.00f, 0.70f, 0.45f, 0.90f, 0.65f,
            0.85f, 0.50f, 0.75f, 0.40f, 0.95f, 0.60f, 0.80f, 0.35f,
            0.70f, 0.50f, 0.90f, 0.45f, 0.65f, 0.85f, 0.55f, 0.30f
        )
    }

    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .pointerInput(duration) {
                    detectTapGestures { offset ->
                        if (duration > 0) {
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek((fraction * duration).toLong())
                        }
                    }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scrubPosFraction?.let { onSeek((it * duration).toLong()) }
                            scrubPosFraction = null
                        },
                        onHorizontalDrag = { change, _ ->
                            if (duration > 0) {
                                scrubPosFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barCount = barHeights.size
                val gap = 4.dp.toPx()
                val totalGapWidth = gap * (barCount - 1)
                val barWidth = (size.width - totalGapWidth) / barCount
                val maxHeight = size.height

                for (i in 0 until barCount) {
                    val x = i * (barWidth + gap)
                    val h = barHeights[i] * maxHeight
                    val y = (maxHeight - h) / 2f
                    val barProgress = (i.toFloat() + 0.5f) / barCount
                    val isPassed = barProgress <= currentProgress

                    drawRoundRect(
                        color = if (isPassed) primaryColor else inactiveColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(barWidth / 2f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val displayedMs = if (scrubPosFraction != null && duration > 0) {
                (scrubPosFraction!! * duration).toLong()
            } else {
                playback.positionMs
            }
            Text(
                text = displayedMs.coerceAtLeast(0L).asClock(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            if (playback.isBuffering) {
                Text(
                    text = "BUFFERING",
                    style = MaterialTheme.typography.labelSmall,
                    color = primaryColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Text(
                text = duration.coerceAtLeast(0L).asClock(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SculptedControlDeck(
    playback: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.8f),
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onShuffle, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (playback.shuffle) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous track",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Central Tactile Hero Play Button
            FilledIconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(68.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (playback.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playback.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "Next track",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(onClick = onRepeat, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = if (playback.repeatMode == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    contentDescription = "Repeat",
                    tint = if (playback.repeatMode != 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingUtilityBar(
    playback: PlaybackUiState,
    equalizerEnabled: Boolean,
    onSleepTimer: () -> Unit,
    onSpeed: () -> Unit,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    onEqualizer: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        UtilityPill(
            icon = Icons.Rounded.Timer,
            label = sleepLabel(playback.sleepEndsAt),
            active = playback.sleepEndsAt != null,
            onClick = onSleepTimer
        )
        UtilityPill(
            icon = Icons.Rounded.Speed,
            label = speedLabel(playback.speed),
            active = playback.speed != 1f,
            onClick = onSpeed
        )
        UtilityPill(
            icon = Icons.Rounded.Subtitles,
            label = "Lyrics",
            active = false,
            onClick = onLyrics
        )
        UtilityPill(
            icon = Icons.Rounded.QueueMusic,
            label = "Queue",
            active = false,
            onClick = onQueue
        )
        UtilityPill(
            icon = Icons.Rounded.Equalizer,
            label = "EQ",
            active = equalizerEnabled,
            onClick = onEqualizer
        )
    }
}

@Composable
private fun UtilityPill(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private fun sleepLabel(endsAt: Long?): String {
    if (endsAt == null) return "Timer"
    val remaining = (endsAt - System.currentTimeMillis()).coerceAtLeast(0L)
    val totalMinutes = (remaining + 59_999L) / 60_000L
    return when {
        totalMinutes <= 0 -> "Ending"
        totalMinutes >= 60 -> "${totalMinutes / 60}h ${totalMinutes % 60}m"
        else -> "${totalMinutes}m"
    }
}

private fun speedLabel(speed: Float): String {
    if (speed == 1f) return "Speed"
    return if (speed == speed.toInt().toFloat()) {
        "${speed.toInt()}×"
    } else {
        String.format(Locale.US, "%.2f×", speed).replace("0×", "×")
    }
}
