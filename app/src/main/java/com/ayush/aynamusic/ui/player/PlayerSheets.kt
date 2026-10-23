package com.ayush.aynamusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ayush.aynamusic.LyricsUiState
import com.ayush.aynamusic.data.model.Lyrics
import com.ayush.aynamusic.data.model.Track
import com.ayush.aynamusic.playback.EqualizerBand
import com.ayush.aynamusic.playback.EqualizerState
import com.ayush.aynamusic.ui.components.AynaArtwork
import com.ayush.aynamusic.ui.components.NowPlayingBars
import com.ayush.aynamusic.util.asClock
import java.util.Locale
import kotlin.math.roundToInt

@Composable
private fun SheetHeader(
    title: String,
    subtitle: String? = null,
    icon: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSheet(
    endsAt: Long?,
    onSelect: (Int?) -> Unit
) {
    val remaining = endsAt?.minus(System.currentTimeMillis())?.coerceAtLeast(0L)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 2.dp, bottom = 26.dp)
    ) {
        SheetHeader(
            title = "Sleep timer",
            subtitle = if (remaining != null) {
                "Playback pauses in ${remaining.asClock()}. It keeps running in the background."
            } else {
                "Let the music fade out when you drift off."
            },
            icon = {
                Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        )
        FlowRow(
            modifier = Modifier.padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            listOf(5, 10, 15, 30, 45, 60, 90).forEach { minutes ->
                Surface(
                    onClick = { onSelect(minutes) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        if (minutes >= 60) "${minutes / 60} hr${if (minutes % 60 != 0) " ${minutes % 60} min" else ""}"
                        else "$minutes min",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 17.dp, vertical = 13.dp)
                    )
                }
            }
        }
        if (endsAt != null) {
            TextButton(
                onClick = { onSelect(null) },
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("Turn off timer")
            }
        }
        Text(
            "Tip: the timer pauses playback, not the app. Audio effects are released when playback stops.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 18.dp)
        )
    }
}

@Composable
fun SpeedSheet(currentSpeed: Float, onSelect: (Float) -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 2.dp, bottom = 26.dp)
    ) {
        SheetHeader(
            title = "Playback speed",
            subtitle = "Slow down a language track or breeze through a podcast.",
            icon = {
                Icon(Icons.Rounded.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        )
        Column(Modifier.padding(top = 18.dp)) {
            speeds.forEach { speed ->
                val selected = kotlin.math.abs(speed - currentSpeed) < 0.01f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(speed) }
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when {
                            speed == 1f -> "Normal"
                            speed < 1f -> "Slower"
                            else -> "Faster"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatSpeed(speed),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun QueueSheet(
    queue: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 2.dp, bottom = 18.dp)
    ) {
        SheetHeader(
            title = "Up next",
            subtitle = "${queue.size} ${if (queue.size == 1) "track" else "tracks"} in queue",
            icon = {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        )
        if (queue.isEmpty()) {
            Text(
                "Your queue is empty. Play something to start.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 28.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .padding(top = 8.dp)
            ) {
                itemsIndexed(queue, key = { index, track -> "$index-${track.id}" }) { index, track ->
                    val current = track.id == currentTrackId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelect(index) }
                            .background(
                                if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 6.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (current) {
                            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                                NowPlayingBars(isPlaying)
                            }
                        } else {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(24.dp)
                            )
                        }
                        AynaArtwork(
                            track = track,
                            modifier = Modifier
                                .padding(start = 7.dp)
                                .size(44.dp),
                            corner = 10.dp
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 11.dp)
                        ) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (current) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            track.durationMs.asClock(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = { onRemove(index) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Remove from queue",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LyricsSheet(
    state: LyricsUiState,
    positionMs: Long,
    onRetry: () -> Unit,
    onSeekToLine: (Long) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 2.dp, bottom = 20.dp)
    ) {
        SheetHeader(
            title = "Lyrics",
            subtitle = state.lyrics?.let {
                if (it.synced.isNotEmpty()) "Live lyrics · synced by LRCLIB" else "Lyrics · LRCLIB"
            } ?: "Time-synced lyrics from LRCLIB",
            icon = {
                Icon(Icons.Rounded.Lyrics, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        )
        when {
            state.loading -> Box(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Finding the words…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            state.lyrics == null -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    state.error ?: "No lyrics available for this track yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Try again")
                }
            }
            else -> LyricsContent(
                lyrics = state.lyrics,
                positionMs = positionMs,
                onSeekToLine = onSeekToLine
            )
        }
    }
}

@Composable
private fun LyricsContent(
    lyrics: Lyrics,
    positionMs: Long,
    onSeekToLine: (Long) -> Unit
) {
    if (lyrics.instrumental) {
        Text(
            "This track is instrumental.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 34.dp)
        )
        return
    }
    if (lyrics.synced.isNotEmpty()) {
        val listState = rememberLazyListState()
        val activeIndex = lyrics.synced.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
        LaunchedEffect(activeIndex) {
            if (activeIndex >= 0) {
                listState.animateScrollToItem(activeIndex.coerceAtMost(lyrics.synced.lastIndex))
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 440.dp)
                .padding(top = 12.dp),
            contentPadding = PaddingValues(vertical = 36.dp)
        ) {
            itemsIndexed(lyrics.synced) { index, line ->
                Text(
                    line.text.ifBlank { "♪" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (index == activeIndex) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == activeIndex) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (index < activeIndex) 0.55f else 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSeekToLine(line.timeMs) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    } else {
        val plain = lyrics.plain?.takeIf { it.isNotBlank() }
        if (plain == null) {
            Text(
                "No lyrics available for this track yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 28.dp)
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 18.dp, bottom = 10.dp)
            ) {
                Text(
                    plain,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3f
                )
            }
        }
    }
}

@Composable
fun EqualizerSheet(
    state: EqualizerState,
    onEnabledChange: (Boolean) -> Unit,
    onBandChange: (Int, Int) -> Unit,
    onPresetSelected: (Int) -> Unit,
    onBassChanged: (Int) -> Unit,
    onReset: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.82f)
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, top = 2.dp, bottom = 28.dp)
    ) {
        SheetHeader(
            title = "Sound shaping",
            subtitle = if (state.available) "System equalizer · applied to the active output"
            else "Audio effects are unavailable for this output",
            icon = {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        )

        if (!state.available) {
            Text(
                state.error ?: "Start playback with wired or Bluetooth audio to enable the equalizer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 22.dp)
            )
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Enable effects", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "A tiny CPU cost, only while you listen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = state.enabled, onCheckedChange = onEnabledChange)
        }

        if (state.presets.isNotEmpty()) {
            Text(
                "Presets",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.presets.size) { index ->
                    val selected = state.selectedPreset == index
                    Surface(
                        onClick = { onPresetSelected(index) },
                        shape = RoundedCornerShape(50),
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            state.presets[index],
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 22.dp, bottom = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        Text(
            "Bands",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
        )
        state.bands.forEach { band ->
            EqualizerBandRow(
                band = band,
                enabled = state.enabled,
                onValueChange = { onBandChange(band.index, it) }
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bass boost", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "${(state.bassBoost / 10)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onReset) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset")
            }
        }
        Slider(
            value = state.bassBoost.toFloat(),
            onValueChange = { onBassChanged(it.roundToInt()) },
            valueRange = 0f..1000f,
            enabled = state.enabled,
            colors = eqSliderColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Equalizer and bass boost are off by default to preserve battery and keep playback neutral.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun EqualizerBandRow(
    band: EqualizerBand,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            formatFrequency(band.centerFrequencyHz),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(46.dp)
        )
        Slider(
            value = band.levelMb.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = band.minLevelMb.toFloat()..band.maxLevelMb.toFloat(),
            enabled = enabled,
            colors = eqSliderColors(),
            modifier = Modifier.weight(1f)
        )
        Text(
            String.format(Locale.US, "%+d", band.levelMb / 100),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(34.dp)
        )
    }
}

@Composable
private fun eqSliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent
)

private fun formatFrequency(hz: Int): String = when {
    hz >= 1000 -> String.format(Locale.US, "%.1f k", hz / 1000f)
    else -> "$hz Hz"
}

private fun formatSpeed(speed: Float): String {
    return if (speed == speed.toInt().toFloat()) "${speed.toInt()}×"
    else String.format(Locale.US, "%.2f×", speed)
}
