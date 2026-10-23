package com.ayush.aynamusic.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ayush.aynamusic.data.model.PlaylistSummary
import com.ayush.aynamusic.data.model.Track
import com.ayush.aynamusic.data.model.UserTrackState
import com.ayush.aynamusic.ui.components.EmptyState
import com.ayush.aynamusic.ui.components.TrackRow
import com.ayush.aynamusic.ui.theme.Acid
import com.ayush.aynamusic.ui.theme.Mint
import com.ayush.aynamusic.ui.withUserState
import com.ayush.aynamusic.util.asClock
import com.ayush.aynamusic.util.initials
import java.util.Locale

enum class LibraryTab(val label: String) {
    PLAYLISTS("Playlists"),
    LIKED("Liked"),
    DOWNLOADS("Offline"),
    LOCAL("Local"),
    RECENT("Recent")
}

@Composable
fun LibraryScreen(
    playlists: List<PlaylistSummary>,
    importStates: Map<String, com.ayush.aynamusic.data.model.PlaylistImportState> = emptyMap(),
    favorites: List<Track>,
    downloads: List<Track>,
    recent: List<Track>,
    localTracks: List<Track>,
    userStates: Map<String, UserTrackState>,
    audioPermission: Boolean,
    localLoading: Boolean,
    currentTrackId: String?,
    isPlaying: Boolean,
    selectedTab: LibraryTab,
    onTabChange: (LibraryTab) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onImportSpotifyPlaylist: (link: String, onProgress: (String) -> Unit, onComplete: (String?) -> Unit) -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onShuffleTracks: (List<Track>) -> Unit,
    onTrackMore: (Track, String?) -> Unit,
    onOpenSettings: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onScanLocal: () -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val cleanQuery = searchQuery.trim()

    val filteredPlaylists = remember(playlists, cleanQuery) {
        if (cleanQuery.isEmpty()) playlists
        else playlists.filter {
            it.name.contains(cleanQuery, ignoreCase = true) ||
            it.description?.contains(cleanQuery, ignoreCase = true) == true
        }
    }

    val filteredFavorites = remember(favorites, cleanQuery) {
        if (cleanQuery.isEmpty()) favorites
        else favorites.filter {
            it.title.contains(cleanQuery, ignoreCase = true) ||
            it.artist.contains(cleanQuery, ignoreCase = true) ||
            it.album?.contains(cleanQuery, ignoreCase = true) == true
        }
    }

    val filteredDownloads = remember(downloads, cleanQuery) {
        if (cleanQuery.isEmpty()) downloads
        else downloads.filter {
            it.title.contains(cleanQuery, ignoreCase = true) ||
            it.artist.contains(cleanQuery, ignoreCase = true) ||
            it.album?.contains(cleanQuery, ignoreCase = true) == true
        }
    }

    val filteredLocalTracks = remember(localTracks, cleanQuery) {
        if (cleanQuery.isEmpty()) localTracks
        else localTracks.filter {
            it.title.contains(cleanQuery, ignoreCase = true) ||
            it.artist.contains(cleanQuery, ignoreCase = true) ||
            it.album?.contains(cleanQuery, ignoreCase = true) == true
        }
    }

    val filteredRecent = remember(recent, cleanQuery) {
        if (cleanQuery.isEmpty()) recent
        else recent.filter {
            it.title.contains(cleanQuery, ignoreCase = true) ||
            it.artist.contains(cleanQuery, ignoreCase = true) ||
            it.album?.contains(cleanQuery, ignoreCase = true) == true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Your library", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
                val saved = favorites.size + downloads.size
                Text(
                    "$saved saved · ${playlists.size} playlists",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp)
                )
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

        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            placeholder = {
                Text(
                    "Search in library…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear library search", modifier = Modifier.size(19.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LibraryTab.entries.forEach { tab ->
                LibraryTabPill(
                    tab = tab,
                    selected = tab == selectedTab,
                    onClick = { onTabChange(tab) }
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedTab) {
                LibraryTab.PLAYLISTS -> PlaylistsContent(
                    playlists = filteredPlaylists,
                    importStates = importStates,
                    onCreate = { showCreateDialog = true },
                    onOpenPlaylist = onOpenPlaylist
                )
                LibraryTab.LIKED -> TrackCollection(
                    tracks = filteredFavorites,
                    userStates = userStates,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    title = if (cleanQuery.isEmpty()) "Liked songs" else "Search results (${filteredFavorites.size})",
                    subtitle = if (cleanQuery.isNotEmpty()) "Matching “$cleanQuery” in Liked"
                    else if (favorites.isEmpty()) "Every song you love, in one place"
                    else "${favorites.size} songs · ${totalTime(favorites)}",
                    emptyTitle = if (cleanQuery.isEmpty()) "Nothing liked yet" else "No matches for “$cleanQuery”",
                    emptyMessage = if (cleanQuery.isEmpty()) "Tap the heart on any track and it will live here, online or off."
                    else "Try searching for a different song or artist.",
                    emptyIcon = Icons.Rounded.Favorite,
                    emptyAction = if (cleanQuery.isEmpty()) "Find music" else null,
                    onEmptyAction = onExplore,
                    onPlayTracks = onPlayTracks,
                    onShuffleTracks = onShuffleTracks,
                    onTrackMore = { onTrackMore(it, null) }
                )
                LibraryTab.DOWNLOADS -> TrackCollection(
                    tracks = filteredDownloads,
                    userStates = userStates,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    title = if (cleanQuery.isEmpty()) "Available offline" else "Search results (${filteredDownloads.size})",
                    subtitle = if (cleanQuery.isNotEmpty()) "Matching “$cleanQuery” in Offline"
                    else if (downloads.isEmpty()) "Music that travels with you"
                    else "${downloads.size} tracks · ${totalTime(downloads)}",
                    emptyTitle = if (cleanQuery.isEmpty()) "No offline music yet" else "No matches for “$cleanQuery”",
                    emptyMessage = if (cleanQuery.isEmpty()) "Download full tracks or previews for flights, commutes and dead zones."
                    else "Try searching for a different downloaded song.",
                    emptyIcon = Icons.Rounded.DownloadDone,
                    emptyAction = if (cleanQuery.isEmpty()) "Find music" else null,
                    onEmptyAction = onExplore,
                    onPlayTracks = onPlayTracks,
                    onShuffleTracks = onShuffleTracks,
                    onTrackMore = { onTrackMore(it, null) }
                )
                LibraryTab.LOCAL -> LocalContent(
                    tracks = filteredLocalTracks,
                    audioPermission = audioPermission,
                    localLoading = localLoading,
                    userStates = userStates,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    onRequestAudioPermission = onRequestAudioPermission,
                    onScanLocal = onScanLocal,
                    onPlayTracks = onPlayTracks,
                    onShuffleTracks = onShuffleTracks,
                    onTrackMore = { onTrackMore(it, null) }
                )
                LibraryTab.RECENT -> TrackCollection(
                    tracks = filteredRecent,
                    userStates = userStates,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    title = if (cleanQuery.isEmpty()) "Recently played" else "Search results (${filteredRecent.size})",
                    subtitle = if (cleanQuery.isNotEmpty()) "Matching “$cleanQuery” in Recent"
                    else if (recent.isEmpty()) "Your listening history lives here"
                    else "Last ${recent.size} tracks",
                    emptyTitle = if (cleanQuery.isEmpty()) "No listening history" else "No matches for “$cleanQuery”",
                    emptyMessage = if (cleanQuery.isEmpty()) "Play something and your recent rotation will appear here."
                    else "Try searching for a different song.",
                    emptyIcon = Icons.Rounded.History,
                    emptyAction = if (cleanQuery.isEmpty()) "Explore music" else null,
                    onEmptyAction = onExplore,
                    onPlayTracks = { tracks, index ->
                        tracks.getOrNull(index)?.let { track -> onPlayTracks(listOf(track), 0) }
                    },
                    onShuffleTracks = onShuffleTracks,
                    onTrackMore = { onTrackMore(it, null) }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                onCreatePlaylist(name)
                showCreateDialog = false
            },
            onImportSpotify = onImportSpotifyPlaylist
        )
    }
}

@Composable
private fun LibraryTabPill(tab: LibraryTab, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Text(
            tab.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun PlaylistsContent(
    playlists: List<PlaylistSummary>,
    importStates: Map<String, com.ayush.aynamusic.data.model.PlaylistImportState>,
    onCreate: () -> Unit,
    onOpenPlaylist: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Playlists", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        if (playlists.isEmpty()) "Build a set for every mood" else "${playlists.size} collections",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onCreate) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
            }
        }
        item {
            NewPlaylistTile(onClick = onCreate)
        }
        items(playlists, key = { it.id }) { playlist ->
            PlaylistTile(
                playlist = playlist,
                importState = importStates[playlist.id],
                onClick = { onOpenPlaylist(playlist.id) }
            )
        }
        if (playlists.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    title = "Your first playlist awaits",
                    message = "Collect full tracks, previews and local files into your own sets.",
                    icon = Icons.Rounded.PlaylistPlay,
                    actionLabel = "Create a playlist",
                    action = onCreate
                )
            }
        }
    }
}

@Composable
private fun PlaylistTile(
    playlist: PlaylistSummary,
    importState: com.ayush.aynamusic.data.model.PlaylistImportState?,
    onClick: () -> Unit
) {
    val isImporting = importState != null && !importState.isDone

    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(152.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                playlist.name.initials().ifBlank { "A" },
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                fontWeight = FontWeight.ExtraBold
            )
            if (!playlist.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                        )
                    )
            )
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isImporting && importState != null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = Mint
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${importState.current}/${importState.total}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Mint,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        Icons.Rounded.LibraryMusic,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "${playlist.trackCount} tracks",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            if (isImporting && importState != null) {
                LinearProgressIndicator(
                    progress = { importState.progressFraction },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(4.dp),
                    color = Mint,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }
        Text(
            playlist.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 9.dp)
        )
        Text(
            if (isImporting && importState != null) {
                "Importing • ${importState.current} of ${importState.total}"
            } else if (playlist.description.isNullOrBlank()) {
                "Playlist"
            } else {
                playlist.description
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (isImporting) Mint else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

@Composable
private fun NewPlaylistTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(152.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(20.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            "New playlist",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 9.dp)
        )
        Text(
            "Start from scratch",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

@Composable
private fun TrackCollection(
    tracks: List<Track>,
    userStates: Map<String, UserTrackState>,
    currentTrackId: String?,
    isPlaying: Boolean,
    title: String,
    subtitle: String,
    emptyTitle: String,
    emptyMessage: String,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyAction: String? = null,
    onEmptyAction: (() -> Unit)? = null,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onShuffleTracks: (List<Track>) -> Unit,
    onTrackMore: (Track) -> Unit
) {
    val decorated = remember(tracks, userStates) {
        tracks.map { it.withUserState(userStates[it.id]) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            CollectionHeader(
                title = title,
                subtitle = subtitle,
                tracks = decorated,
                icon = emptyIcon,
                onPlay = { if (decorated.isNotEmpty()) onPlayTracks(decorated, 0) },
                onShuffle = { onShuffleTracks(decorated) }
            )
        }
        if (decorated.isEmpty()) {
            item {
                EmptyState(
                    title = emptyTitle,
                    message = emptyMessage,
                    icon = emptyIcon,
                    actionLabel = emptyAction,
                    action = onEmptyAction
                )
            }
        } else {
            itemsIndexed(decorated, key = { _, track -> "library-${title}-${track.id}" }) { index, track ->
                TrackRow(
                    track = track,
                    isCurrent = track.id == currentTrackId,
                    isPlaying = isPlaying,
                    onClick = { onPlayTracks(decorated, index) },
                    onMore = { onTrackMore(track) }
                )
            }
        }
    }
}

@Composable
private fun CollectionHeader(
    title: String,
    subtitle: String,
    tracks: List<Track>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(62.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                        )
                    ),
                    RoundedCornerShape(18.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(26.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 13.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (tracks.isNotEmpty()) {
            IconButton(onClick = onShuffle) {
                Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledIconButton(
                onClick = onPlay,
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play all", modifier = Modifier.size(23.dp))
            }
        }
    }
}

@Composable
private fun LocalContent(
    tracks: List<Track>,
    audioPermission: Boolean,
    localLoading: Boolean,
    userStates: Map<String, UserTrackState>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onRequestAudioPermission: () -> Unit,
    onScanLocal: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onShuffleTracks: (List<Track>) -> Unit,
    onTrackMore: (Track) -> Unit
) {
    if (!audioPermission) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(76.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
            Text(
                "Your phone is a music library",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp)
            )
            Text(
                "AYNAMusic reads your on-device audio — no uploads, no servers. Grant audio access to bring your files into the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(
                onClick = onRequestAudioPermission,
                modifier = Modifier.padding(top = 20.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Allow audio access")
            }
        }
        return
    }

    val decorated = remember(tracks, userStates) {
        tracks.map { it.withUserState(userStates[it.id]) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("On this device", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        if (localLoading) "Scanning your storage…" else "${decorated.size} tracks · no connection needed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (decorated.isNotEmpty()) {
                    IconButton(onClick = { onShuffleTracks(decorated) }) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle local music", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onScanLocal) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Rescan local music", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (localLoading) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 42.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Scanning MediaStore…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (decorated.isEmpty()) {
            item {
                EmptyState(
                    title = "No audio found",
                    message = "Add music to your device, then rescan. Local files always play offline.",
                    icon = Icons.Rounded.MusicNote,
                    actionLabel = "Scan again",
                    action = onScanLocal
                )
            }
        } else {
            itemsIndexed(decorated, key = { _, track -> "local-${track.id}" }) { index, track ->
                TrackRow(
                    track = track,
                    isCurrent = track.id == currentTrackId,
                    isPlaying = isPlaying,
                    onClick = { onPlayTracks(decorated, index) },
                    onMore = { onTrackMore(track) }
                )
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onImportSpotify: (link: String, onProgress: (String) -> Unit, onComplete: (String?) -> Unit) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var spotifyLink by rememberSaveable { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        title = {
            Column {
                Text("Add playlist", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = { if (!isImporting) selectedTab = 0 },
                        shape = CircleShape,
                        color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "New playlist",
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                    Surface(
                        onClick = { if (!isImporting) selectedTab = 1 },
                        shape = CircleShape,
                        color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Import Spotify",
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }
        },
        text = {
            if (selectedTab == 0) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    singleLine = true,
                    label = { Text("Playlist name") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = spotifyLink,
                        onValueChange = { spotifyLink = it },
                        singleLine = true,
                        enabled = !isImporting,
                        label = { Text("Spotify playlist link") },
                        placeholder = { Text("open.spotify.com/playlist/...") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Paste a public Spotify playlist link.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isImporting) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                importStatus.ifBlank { "Importing..." },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                TextButton(
                    onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                    enabled = name.isNotBlank()
                ) {
                    Text("Create")
                }
            } else {
                TextButton(
                    onClick = {
                        if (spotifyLink.isNotBlank() && !isImporting) {
                            isImporting = true
                            onImportSpotify(
                                spotifyLink,
                                { status -> importStatus = status },
                                { createdId ->
                                    isImporting = false
                                    if (createdId != null) {
                                        onDismiss()
                                    }
                                }
                            )
                        }
                    },
                    enabled = spotifyLink.isNotBlank() && !isImporting
                ) {
                    Text(if (isImporting) "Importing..." else "Import")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isImporting
            ) {
                Text("Cancel")
            }
        }
    )
}

private fun totalTime(tracks: List<Track>): String {
    val total = tracks.sumOf { it.durationMs }
    if (total <= 0) return "0 min"
    val minutes = total / 60_000L
    return if (minutes >= 60) {
        String.format(Locale.US, "%d hr %d min", minutes / 60, minutes % 60)
    } else {
        "$minutes min"
    }
}
