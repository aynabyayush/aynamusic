package com.ayush.aynamusic.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayush.aynamusic.data.model.HomeFeed
import com.ayush.aynamusic.data.model.Track
import com.ayush.aynamusic.data.model.UserTrackState
import com.ayush.aynamusic.ui.components.LoadingRow
import com.ayush.aynamusic.ui.components.OfflineBanner
import com.ayush.aynamusic.ui.components.OfflineChip
import com.ayush.aynamusic.ui.components.SectionHeader
import com.ayush.aynamusic.ui.components.TrackCard
import com.ayush.aynamusic.ui.isAvailableOffline
import com.ayush.aynamusic.ui.theme.Acid
import com.ayush.aynamusic.ui.theme.Mint
import com.ayush.aynamusic.ui.withUserState
import com.ayush.aynamusic.util.asCompactCount
import java.util.Calendar

@Composable
fun HomeScreen(
    home: HomeFeed?,
    homeLoading: Boolean,
    homeError: String?,
    recent: List<Track>,
    favorites: List<Track>,
    downloads: List<Track>,
    userStates: Map<String, UserTrackState>,
    currentTrackId: String?,
    isPlaying: Boolean,
    online: Boolean,
    offlineOnly: Boolean,
    audioPermission: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLiked: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenLocal: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onShuffleTracks: (List<Track>) -> Unit,
    onOpenPlayer: () -> Unit,
    onTrackMore: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val cardWidth: Dp = when {
            maxWidth >= 840.dp -> 196.dp
            maxWidth >= 600.dp -> 174.dp
            else -> 150.dp
        }
        val decoratedRecent = recent.map { it.withUserState(userStates[it.id]) }
        val sections = home?.sections.orEmpty()
        val allPicks = buildList {
            addAll(favorites)
            addAll(decoratedRecent)
            addAll(sections.flatMap { it.tracks })
        }.distinctBy { it.id }
        val dailyPicks = allPicks.filter { it.isAvailableOffline() || online }.ifEmpty { allPicks }
        val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Late night, huh?"
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp)
        ) {
            item {
                HomeHeader(
                    greeting = greeting,
                    online = online,
                    offlineOnly = offlineOnly,
                    onOpenSettings = onOpenSettings
                )
            }
            if (!online || offlineOnly) {
                item { OfflineBanner(offlineOnly = offlineOnly) }
            }
            item {
                DailyHero(
                    enabled = dailyPicks.isNotEmpty(),
                    onPlay = {
                        if (dailyPicks.isNotEmpty()) onShuffleTracks(dailyPicks.take(60))
                    }
                )
            }
            item {
                QuickActions(
                    likedCount = favorites.size,
                    offlineCount = downloads.size,
                    localAvailable = audioPermission,
                    onOpenLiked = onOpenLiked,
                    onOpenDownloads = onOpenDownloads,
                    onOpenLocal = onOpenLocal
                )
            }
            if (homeLoading && home == null) {
                item { LoadingRow("Tuning the catalog…") }
                item { HomeSkeleton(cardWidth) }
            }
            if (homeError != null && home == null) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            homeError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onRefresh) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Try again")
                        }
                    }
                }
            }
            if (decoratedRecent.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Continue listening",
                        subtitle = "Pick up where you left off",
                        actionLabel = "Open player",
                        onAction = onOpenPlayer
                    )
                }
                item {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(decoratedRecent.take(10), key = { "home-recent-${it.id}" }) { track ->
                            TrackCard(
                                track = track,
                                isCurrent = track.id == currentTrackId,
                                onClick = {
                                    onPlayTracks(listOf(track), 0)
                                },
                                onMore = { onTrackMore(track) },
                                cardWidth = cardWidth
                            )
                        }
                    }
                }
            }
            sections.forEach { section ->
                item(key = "section-header-${section.id}") {
                    SectionHeader(
                        title = section.title,
                        subtitle = section.subtitle,
                        actionLabel = "Play all",
                        onAction = { onPlayTracks(section.tracks, 0) }
                    )
                }
                item(key = "section-row-${section.id}") {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(section.tracks, key = { _, track -> "home-${section.id}-${track.id}" }) { index, track ->
                            TrackCard(
                                track = track,
                                isCurrent = track.id == currentTrackId && isPlaying,
                                onClick = { onPlayTracks(section.tracks, index) },
                                onMore = { onTrackMore(track) },
                                cardWidth = cardWidth
                            )
                        }
                    }
                }
            }
            item {
                HomeFooter()
            }
        }
    }
}

@Composable
private fun HomeHeader(
    greeting: String,
    online: Boolean,
    offlineOnly: Boolean,
    onOpenSettings: () -> Unit
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(
                    text = "AYNA",
                    style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 2.4.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Box(
                    Modifier
                        .padding(start = 2.dp, top = 2.dp)
                        .size(7.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Text(
                    text = "MUSIC",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                )
            }
            if (!online || offlineOnly) {
                OfflineChip()
                Spacer(Modifier.width(10.dp))
            }
            Surface(
                onClick = onOpenSettings,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
        Text(
            greeting,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            text = "Your music, everywhere.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun DailyHero(enabled: Boolean, onPlay: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .height(174.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Acid, Mint)))
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(170.dp)
                .background(Color.White.copy(alpha = 0.14f), CircleShape)
                .padding(14.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 19.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "MADE FOR YOU · DAILY",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF243000).copy(alpha = 0.7f)
                )
                Text(
                    "Your day,\nin sound.",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color(0xFF111709),
                    modifier = Modifier.padding(top = 7.dp)
                )
                Text(
                    "A fresh mix of full tracks and global picks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF243000).copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Button(
                onClick = onPlay,
                enabled = enabled,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0B0D10),
                    contentColor = Acid,
                    disabledContainerColor = Color(0xFF0B0D10).copy(alpha = 0.4f),
                    disabledContentColor = Acid.copy(alpha = 0.4f)
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play daily mix", modifier = Modifier.size(28.dp))
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 94.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            listOf(8, 15, 22, 13, 7).forEach { h ->
                Box(
                    Modifier
                        .width(3.dp)
                        .height(h.dp)
                        .background(Color(0xFF0B0D10).copy(alpha = 0.18f), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun QuickActions(
    likedCount: Int,
    offlineCount: Int,
    localAvailable: Boolean,
    onOpenLiked: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenLocal: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickAction(
            icon = Icons.Rounded.Favorite,
            title = "Liked",
            value = "${likedCount.toLong().asCompactCount()} songs",
            onClick = onOpenLiked,
            modifier = Modifier.weight(1f)
        )
        QuickAction(
            icon = Icons.Rounded.DownloadDone,
            title = "Offline",
            value = "${offlineCount.toLong().asCompactCount()} saved",
            onClick = onOpenDownloads,
            modifier = Modifier.weight(1f)
        )
        QuickAction(
            icon = Icons.Rounded.Headphones,
            title = "On device",
            value = if (localAvailable) "Local audio" else "Allow access",
            onClick = onOpenLocal,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.height(88.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    value,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HomeSkeleton(cardWidth: Dp) {
    Column(Modifier.padding(top = 8.dp)) {
        repeat(2) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                repeat(3) {
                    Column(Modifier.width(cardWidth)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(cardWidth)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                        )
                        Box(
                            Modifier
                                .padding(top = 10.dp)
                                .fillMaxWidth(0.72f)
                                .height(10.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(4.dp))
                        )
                        Box(
                            Modifier
                                .padding(top = 6.dp)
                                .fillMaxWidth(0.45f)
                                .height(8.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeFooter() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 30.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                "  Full tracks by Audius artists · Global previews by Apple Music",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
        Text(
            "Artists keep their rights. Previews are 30 seconds — open the full release from any track menu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
