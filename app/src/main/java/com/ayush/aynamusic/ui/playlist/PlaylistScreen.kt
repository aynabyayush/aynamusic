package com.ayush.aynamusic.ui.playlist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ayush.aynamusic.data.model.PlaylistSummary
import com.ayush.aynamusic.data.model.Track
import com.ayush.aynamusic.data.model.UserTrackState
import com.ayush.aynamusic.ui.components.AynaArtwork
import com.ayush.aynamusic.ui.components.EmptyState
import com.ayush.aynamusic.ui.components.PillButton
import com.ayush.aynamusic.ui.components.ScreenTopBar
import com.ayush.aynamusic.ui.components.TrackRow
import com.ayush.aynamusic.ui.withUserState
import com.ayush.aynamusic.util.initials

@Composable
fun PlaylistScreen(
    playlist: PlaylistSummary?,
    importState: com.ayush.aynamusic.data.model.PlaylistImportState? = null,
    tracks: List<Track>,
    userStates: Map<String, UserTrackState>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onShuffleTracks: (List<Track>) -> Unit,
    onTrackMore: (Track, String?) -> Unit,
    onAddSongs: () -> Unit,
    onRename: (String) -> Unit,
    onUpdateCover: (String?) -> Unit = {},
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showEditCover by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onUpdateCover(uri.toString())
        }
    }

    val decorated = remember(tracks, userStates) {
        tracks.map { it.withUserState(userStates[it.id]) }
    }

    val cleanQuery = searchQuery.trim()
    val filteredTracks = remember(decorated, cleanQuery) {
        if (cleanQuery.isEmpty()) decorated
        else decorated.filter { track ->
            track.title.contains(cleanQuery, ignoreCase = true) ||
            track.artist.contains(cleanQuery, ignoreCase = true) ||
            track.album?.contains(cleanQuery, ignoreCase = true) == true
        }
    }

    if (playlist == null) {
        Column(modifier.fillMaxSize()) {
            ScreenTopBar(title = "Playlist", onBack = onBack)
            EmptyState(
                title = "Playlist not found",
                message = "This playlist may have been deleted or hasn't synced yet.",
                icon = Icons.Rounded.MusicNote
            )
        }
        return
    }

    Column(modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "Playlist",
            onBack = onBack,
            actions = {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Rounded.MoreHoriz,
                            contentDescription = "Playlist options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename playlist") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                showRename = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Change cover image") },
                            leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                showEditCover = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete playlist") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuOpen = false
                                showDelete = true
                            }
                        )
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            item {
                PlaylistHero(
                    playlist = playlist,
                    tracks = decorated,
                    searchQuery = searchQuery,
                    onQueryChange = { searchQuery = it },
                    isSearchExpanded = isSearchExpanded,
                    onToggleSearch = { expanded ->
                        isSearchExpanded = expanded
                        if (!expanded) searchQuery = ""
                    },
                    onPlay = {
                        if (filteredTracks.isNotEmpty()) onPlayTracks(filteredTracks, 0)
                    },
                    onShuffle = { if (filteredTracks.isNotEmpty()) onShuffleTracks(filteredTracks) },
                    onAddSongs = onAddSongs,
                    onEditCover = { showEditCover = true }
                )
            }

            if (importState != null && !importState.isDone) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Importing tracks in background...",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${importState.current}/${importState.total}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { importState.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }

            if (decorated.isEmpty()) {
                item {
                    EmptyState(
                        title = "This playlist is empty",
                        message = "Add full tracks, Apple Music previews or files from your phone.",
                        icon = Icons.Rounded.PlaylistAdd,
                        actionLabel = "Add songs",
                        action = onAddSongs
                    )
                }
            } else if (filteredTracks.isEmpty()) {
                item {
                    EmptyState(
                        title = "No matching songs",
                        message = "No tracks in playlist match “$cleanQuery”.",
                        icon = Icons.Rounded.Search
                    )
                }
            } else {
                itemsIndexed(
                    filteredTracks,
                    key = { _, track -> "${playlist.id}-${track.id}" }
                ) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = track.id == currentTrackId,
                        isPlaying = isPlaying,
                        onClick = { onPlayTracks(filteredTracks, index) },
                        onMore = { onTrackMore(track, playlist.id) }
                    )
                }
                item {
                    TextButton(
                        onClick = onAddSongs,
                        modifier = Modifier.padding(start = 14.dp, top = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add more songs")
                    }
                }
            }
        }
    }

    if (showRename) {
        RenamePlaylistDialog(
            currentName = playlist.name,
            onDismiss = { showRename = false },
            onRename = { name ->
                onRename(name)
                showRename = false
            }
        )
    }

    if (showEditCover) {
        EditCoverDialog(
            currentCoverUrl = playlist.coverUrl,
            onDismiss = { showEditCover = false },
            onPickDeviceImage = { imagePickerLauncher.launch("image/*") },
            onSaveUrl = { url ->
                onUpdateCover(url)
                showEditCover = false
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete playlist?") },
            text = { Text("“${playlist.name}” will be removed everywhere it is synced. The downloaded audio files stay on your device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PlaylistHero(
    playlist: PlaylistSummary,
    tracks: List<Track>,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    isSearchExpanded: Boolean,
    onToggleSearch: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddSongs: () -> Unit,
    onEditCover: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0f)
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        val wide = this.maxWidth >= 560.dp
        val coverSize = if (wide) 196.dp else 218.dp
        val cover: @Composable () -> Unit = {
            PlaylistArtwork(playlist = playlist, tracks = tracks, size = coverSize, onEditCover = onEditCover)
        }
        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                cover()
                PlaylistMeta(
                    playlist = playlist,
                    tracks = tracks,
                    searchQuery = searchQuery,
                    onQueryChange = onQueryChange,
                    isSearchExpanded = isSearchExpanded,
                    onToggleSearch = onToggleSearch,
                    onPlay = onPlay,
                    onShuffle = onShuffle,
                    onAddSongs = onAddSongs,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 24.dp)
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                cover()
                PlaylistMeta(
                    playlist = playlist,
                    tracks = tracks,
                    searchQuery = searchQuery,
                    onQueryChange = onQueryChange,
                    isSearchExpanded = isSearchExpanded,
                    onToggleSearch = onToggleSearch,
                    onPlay = onPlay,
                    onShuffle = onShuffle,
                    onAddSongs = onAddSongs,
                    centered = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaylistMeta(
    playlist: PlaylistSummary,
    tracks: List<Track>,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    isSearchExpanded: Boolean,
    onToggleSearch: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddSongs: () -> Unit,
    modifier: Modifier = Modifier,
    centered: Boolean = false
) {
    val offlineCount = tracks.count { it.isLocal || it.isDownloaded }
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            playlist.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            buildString {
                append("${playlist.trackCount} ${if (playlist.trackCount == 1) "track" else "tracks"}")
                totalDuration(tracks)?.let { append(" · $it") }
                if (offlineCount > 0) append(" · $offlineCount offline")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 5.dp)
        )
        if (!playlist.description.isNullOrBlank()) {
            Text(
                playlist.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        PlaylistActionRow(
            playlistName = playlist.name,
            tracksEmpty = tracks.isEmpty(),
            searchQuery = searchQuery,
            onQueryChange = onQueryChange,
            isSearchExpanded = isSearchExpanded,
            onToggleSearch = onToggleSearch,
            onPlay = onPlay,
            onShuffle = onShuffle,
            onAddSongs = onAddSongs
        )
    }
}

@Composable
private fun PlaylistActionRow(
    playlistName: String,
    tracksEmpty: Boolean,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    isSearchExpanded: Boolean,
    onToggleSearch: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddSongs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .animateContentSize(),
        horizontalArrangement = if (isSearchExpanded) Arrangement.Start else Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = !isSearchExpanded,
            enter = fadeIn(tween(220)) + expandHorizontally(expandFrom = Alignment.Start),
            exit = fadeOut(tween(180)) + shrinkHorizontally(shrinkTowards = Alignment.Start)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tracksEmpty) {
                    Button(
                        onClick = onAddSongs,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Add songs")
                    }
                } else {
                    PillButton(
                        text = "Play",
                        icon = Icons.Rounded.PlayArrow,
                        filled = true,
                        onClick = onPlay
                    )
                    PillButton(
                        text = "Shuffle",
                        icon = Icons.Rounded.Shuffle,
                        filled = false,
                        onClick = onShuffle
                    )
                }
                IconButton(onClick = onAddSongs) {
                    Icon(
                        Icons.Rounded.PlaylistAdd,
                        contentDescription = "Add songs",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isSearchExpanded) {
            val focusRequester = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        "Search in $playlistName...",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (searchQuery.isNotEmpty()) {
                                onQueryChange("")
                            } else {
                                onToggleSearch(false)
                            }
                        }
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Close search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        } else {
            IconButton(
                onClick = { onToggleSearch(true) }
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = "Search in playlist",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlaylistArtwork(
    playlist: PlaylistSummary,
    tracks: List<Track>,
    size: androidx.compose.ui.unit.Dp,
    onEditCover: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f),
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
            )
            .clickable(onClick = onEditCover),
        contentAlignment = Alignment.Center
    ) {
        Text(
            playlist.name.initials().ifBlank { "A" },
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
            fontWeight = FontWeight.ExtraBold
        )
        val first = tracks.firstOrNull()
        when {
            !playlist.coverUrl.isNullOrBlank() -> AsyncImage(
                model = playlist.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            first != null -> AynaArtwork(
                track = first,
                modifier = Modifier.fillMaxSize(),
                corner = 28.dp
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f), CircleShape)
                .size(38.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.CameraAlt,
                contentDescription = "Edit playlist cover",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun EditCoverDialog(
    currentCoverUrl: String?,
    onDismiss: () -> Unit,
    onPickDeviceImage: () -> Unit,
    onSaveUrl: (String?) -> Unit
) {
    var coverUrlInput by rememberSaveable { mutableStateOf(currentCoverUrl.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit playlist cover") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        onPickDeviceImage()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Choose image from device")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        " OR ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                OutlinedTextField(
                    value = coverUrlInput,
                    onValueChange = { coverUrlInput = it.trim() },
                    singleLine = true,
                    label = { Text("Image URL") },
                    placeholder = { Text("https://example.com/cover.jpg") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSaveUrl(coverUrlInput.ifBlank { null })
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (!currentCoverUrl.isNullOrBlank()) {
                    TextButton(onClick = {
                        onSaveUrl(null)
                        onDismiss()
                    }) {
                        Text("Remove cover", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun RenamePlaylistDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                singleLine = true,
                label = { Text("Playlist name") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onRename(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun totalDuration(tracks: List<Track>): String? {
    val total = tracks.sumOf { it.durationMs }
    if (total <= 0) return null
    val minutes = total / 60_000L
    val hours = minutes / 60
    return if (hours > 0) {
        val remMins = minutes % 60
        "${hours}h ${remMins}m"
    } else {
        "${minutes}m"
    }
}
