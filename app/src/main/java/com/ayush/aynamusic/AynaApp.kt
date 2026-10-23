package com.ayush.aynamusic

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ayush.aynamusic.data.model.Track
import com.ayush.aynamusic.data.remote.MusicCatalogRepository
import com.ayush.aynamusic.ui.actions.AddToPlaylistSheet
import com.ayush.aynamusic.ui.actions.TrackActionSheet
import com.ayush.aynamusic.ui.components.AynaBottomBar
import com.ayush.aynamusic.ui.components.AynaNavigationRail
import com.ayush.aynamusic.ui.components.MainTab
import com.ayush.aynamusic.ui.components.MiniPlayer
import com.ayush.aynamusic.ui.home.HomeScreen
import com.ayush.aynamusic.ui.library.LibraryScreen
import com.ayush.aynamusic.ui.library.LibraryTab
import com.ayush.aynamusic.ui.player.EqualizerSheet
import com.ayush.aynamusic.ui.player.LyricsSheet
import com.ayush.aynamusic.ui.player.NowPlayingScreen
import com.ayush.aynamusic.ui.player.QueueSheet
import com.ayush.aynamusic.ui.player.SleepTimerSheet
import com.ayush.aynamusic.ui.player.SpeedSheet
import com.ayush.aynamusic.ui.playlist.PlaylistScreen
import com.ayush.aynamusic.ui.search.SearchScreen
import com.ayush.aynamusic.ui.settings.CloudSyncScreen
import com.ayush.aynamusic.ui.settings.SettingsScreen
import com.ayush.aynamusic.ui.withUserState
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val PLAYLIST = "playlist/{playlistId}"
    const val SETTINGS = "settings"
    const val CLOUD = "cloud"
    const val PLAYER = "player"

    fun playlist(id: String) = "playlist/${Uri.encode(id)}"
}

private sealed interface PlayerSheet {
    data object Sleep : PlayerSheet
    data object Speed : PlayerSheet
    data object Queue : PlayerSheet
    data object Lyrics : PlayerSheet
    data object Equalizer : PlayerSheet
    data class Actions(val track: Track, val playlistId: String?) : PlayerSheet
    data class AddToPlaylist(val track: Track) : PlayerSheet
}

@Composable
fun AynaApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val homeState by viewModel.home.collectAsStateWithLifecycle()
    val searchState by viewModel.search.collectAsStateWithLifecycle()
    val playbackState by viewModel.playback.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyrics.collectAsStateWithLifecycle()
    val userStates by viewModel.userStates.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val localTracks by viewModel.localTracks.collectAsStateWithLifecycle()
    val localLoading by viewModel.localLoading.collectAsStateWithLifecycle()
    val audioPermission by viewModel.audioPermission.collectAsStateWithLifecycle()
    val equalizerState by viewModel.equalizer.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val activeRoute = backStackEntry?.destination?.route
    var libraryTab by rememberSaveable { mutableStateOf(LibraryTab.PLAYLISTS) }
    var playerSheet by remember { mutableStateOf<PlayerSheet?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val currentTrack = playbackState.currentTrack?.let { track ->
        track.withUserState(userStates[track.id])
    }

    val audioPermissionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[audioPermissionName]
            ?: (ContextCompat.checkSelfPermission(context, audioPermissionName) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED)
        viewModel.onAudioPermissionChanged(granted)
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Audio access wasn't granted. Streaming and downloads are still available."
                )
            }
        }
    }

    val requestAudioPermission: () -> Unit = {
        val permissions = buildList {
            add(audioPermissionName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    LaunchedEffect(Unit) {
        viewModel.toasts.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    val navigateTab: (MainTab) -> Unit = { tab ->
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val openPlayer = {
        if (playbackState.currentTrack != null) {
            navController.navigate(Routes.PLAYER) { launchSingleTop = true }
        }
    }

    fun openSettings() {
        navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
    }

    fun openLibraryTab(tab: LibraryTab) {
        libraryTab = tab
        navigateTab(MainTab.LIBRARY)
    }

    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        val rootRoute = MainTab.entries.any { it.route == activeRoute }
        val fullPlayer = activeRoute == Routes.PLAYER
        val hasBottomBar = rootRoute && !wide && !fullPlayer
        val hasMiniPlayer = currentTrack != null && !fullPlayer
        val needsOuterNavInset = !hasBottomBar && !fullPlayer

        Row(Modifier.fillMaxSize()) {
            if (wide && !fullPlayer) {
                AynaNavigationRail(
                    current = if (rootRoute) activeRoute.orEmpty() else "",
                    onSelect = navigateTab,
                    onSettings = { openSettings() }
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (needsOuterNavInset) Modifier.navigationBarsPadding() else Modifier)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.HOME,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { fadeIn(tween(180)) },
                        exitTransition = { fadeOut(tween(130)) },
                        popEnterTransition = { fadeIn(tween(180)) },
                        popExitTransition = { fadeOut(tween(130)) }
                    ) {
                        composable(Routes.HOME) {
                            HomeScreen(
                                home = homeState.feed,
                                homeLoading = homeState.loading,
                                homeError = homeState.error,
                                recent = recent,
                                favorites = favorites,
                                downloads = downloads,
                                userStates = userStates,
                                currentTrackId = playbackState.currentTrack?.id,
                                isPlaying = playbackState.isPlaying,
                                online = online,
                                offlineOnly = settings.offlineOnly,
                                audioPermission = audioPermission,
                                onRefresh = viewModel::refreshHome,
                                onOpenSettings = { openSettings() },
                                onOpenLiked = { openLibraryTab(LibraryTab.LIKED) },
                                onOpenDownloads = { openLibraryTab(LibraryTab.DOWNLOADS) },
                                onOpenLocal = { openLibraryTab(LibraryTab.LOCAL) },
                                onPlayTracks = viewModel::playTracks,
                                onShuffleTracks = viewModel::shufflePlay,
                                onOpenPlayer = {
                                    if (playbackState.currentTrack != null) {
                                        openPlayer()
                                    } else if (recent.isNotEmpty()) {
                                        viewModel.playTrack(recent.first())
                                        navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                                    }
                                },
                                onTrackMore = { track ->
                                    playerSheet = PlayerSheet.Actions(track, null)
                                }
                            )
                        }

                        composable(Routes.SEARCH) {
                            SearchScreen(
                                query = searchState.query,
                                local = searchState.local,
                                full = searchState.full,
                                previews = searchState.previews,
                                searching = searchState.searching,
                                error = searchState.error,
                                recentSearches = settings.recentSearches,
                                online = online,
                                offlineOnly = settings.offlineOnly,
                                audioPermission = audioPermission,
                                userStates = userStates,
                                currentTrackId = playbackState.currentTrack?.id,
                                isPlaying = playbackState.isPlaying,
                                onQueryChange = viewModel::setSearchQuery,
                                onSubmit = viewModel::submitSearch,
                                onClearRecent = viewModel::clearRecentSearches,
                                onPlayTracks = viewModel::playTracks,
                                onTrackMore = { track ->
                                    playerSheet = PlayerSheet.Actions(track, null)
                                },
                                onOpenLocal = { openLibraryTab(LibraryTab.LOCAL) },
                                onRequestAudioPermission = requestAudioPermission
                            )
                        }

                        composable(Routes.LIBRARY) {
                            LibraryScreen(
                                playlists = playlists,
                                favorites = favorites,
                                downloads = downloads,
                                recent = recent,
                                localTracks = localTracks,
                                userStates = userStates,
                                audioPermission = audioPermission,
                                localLoading = localLoading,
                                currentTrackId = playbackState.currentTrack?.id,
                                isPlaying = playbackState.isPlaying,
                                selectedTab = libraryTab,
                                onTabChange = { libraryTab = it },
                                onOpenPlaylist = { id ->
                                    navController.navigate(Routes.playlist(id))
                                },
                                onCreatePlaylist = { name ->
                                    viewModel.createPlaylist(name) { id ->
                                        navController.navigate(Routes.playlist(id))
                                    }
                                },
                                onImportSpotifyPlaylist = { link, onProgress, onComplete ->
                                    viewModel.importSpotifyPlaylist(link, onProgress) { id ->
                                        if (id != null) {
                                            navController.navigate(Routes.playlist(id))
                                        }
                                        onComplete(id)
                                    }
                                },
                                onPlayTracks = viewModel::playTracks,
                                onShuffleTracks = viewModel::shufflePlay,
                                onTrackMore = { track, playlistId ->
                                    playerSheet = PlayerSheet.Actions(track, playlistId)
                                },
                                onOpenSettings = { openSettings() },
                                onRequestAudioPermission = requestAudioPermission,
                                onScanLocal = viewModel::scanLocalMusic,
                                onExplore = { navigateTab(MainTab.SEARCH) }
                            )
                        }

                        composable(Routes.PLAYLIST) { entry ->
                            val encodedId = entry.arguments?.getString("playlistId").orEmpty()
                            val playlistId = runCatching { Uri.decode(encodedId) }.getOrDefault(encodedId)
                            val playlist = playlists.firstOrNull { it.id == playlistId }
                            val tracksFlow = remember(playlistId) { viewModel.playlistTracks(playlistId) }
                            val tracks by tracksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                            PlaylistScreen(
                                playlist = playlist,
                                tracks = tracks,
                                userStates = userStates,
                                currentTrackId = playbackState.currentTrack?.id,
                                isPlaying = playbackState.isPlaying,
                                onBack = { navController.popBackStack() },
                                onPlayTracks = viewModel::playTracks,
                                onShuffleTracks = viewModel::shufflePlay,
                                onTrackMore = { track, listId ->
                                    playerSheet = PlayerSheet.Actions(track, listId)
                                },
                                onAddSongs = {
                                    navController.navigate(Routes.SEARCH) { launchSingleTop = true }
                                },
                                onRename = { name -> viewModel.renamePlaylist(playlistId, name) },
                                onDelete = {
                                    navController.popBackStack()
                                    viewModel.deletePlaylist(playlistId)
                                }
                            )
                        }

                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                settings = settings,
                                online = online,
                                audioPermission = audioPermission,
                                onBack = { navController.popBackStack() },
                                onOpenCloud = { navController.navigate(Routes.CLOUD) },
                                onThemeChanged = viewModel::setTheme,
                                onAmoledChanged = viewModel::setAmoled,
                                onDynamicColorChanged = viewModel::setDynamicColor,
                                onOfflineOnlyChanged = viewModel::setOfflineOnly,
                                onWifiOnlyChanged = viewModel::setWifiOnlyDownloads,
                                onAudioQualityChanged = viewModel::setAudioQuality,
                                onAutoCloudChanged = viewModel::setAutoCloudSync,
                                onRegionChanged = viewModel::setRegion,
                                onSyncNow = viewModel::syncNow,
                                onRequestAudioPermission = requestAudioPermission,
                                onScanLocal = viewModel::scanLocalMusic
                            )
                        }

                        composable(Routes.CLOUD) {
                            CloudSyncScreen(
                                settings = settings,
                                online = online,
                                onBack = { navController.popBackStack() },
                                onSaveConfig = viewModel::saveCloudConfig,
                                onDisconnect = viewModel::disconnectCloud,
                                onSyncNow = viewModel::syncNow,
                                onAutoSyncChanged = viewModel::setAutoCloudSync
                            )
                        }

                        composable(Routes.PLAYER) {
                            NowPlayingScreen(
                                track = currentTrack,
                                playback = playbackState,
                                equalizerEnabled = equalizerState.enabled,
                                onBack = {
                                    if (!navController.popBackStack()) {
                                        navController.navigate(Routes.HOME)
                                    }
                                },
                                onMore = { track ->
                                    playerSheet = PlayerSheet.Actions(track, null)
                                },
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onNext = viewModel::next,
                                onPrevious = viewModel::previous,
                                onSeek = viewModel::seekTo,
                                onShuffle = viewModel::toggleShuffle,
                                onRepeat = viewModel::cycleRepeat,
                                onFavorite = viewModel::toggleFavorite,
                                onDownload = { track ->
                                    when {
                                        track.isDownloaded -> viewModel.removeDownload(track)
                                        track.downloadState == com.ayush.aynamusic.data.model.DownloadState.DOWNLOADING ||
                                            track.downloadState == com.ayush.aynamusic.data.model.DownloadState.QUEUED -> Unit
                                        else -> viewModel.downloadTrack(track)
                                    }
                                },
                                onSleepTimer = { playerSheet = PlayerSheet.Sleep },
                                onSpeed = { playerSheet = PlayerSheet.Speed },
                                onLyrics = {
                                    viewModel.loadLyrics()
                                    playerSheet = PlayerSheet.Lyrics
                                },
                                onQueue = { playerSheet = PlayerSheet.Queue },
                                onEqualizer = { playerSheet = PlayerSheet.Equalizer }
                            )
                        }
                    }
                }

                if (hasMiniPlayer && currentTrack != null) {
                    Box(
                        Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        MiniPlayer(
                            track = currentTrack,
                            isPlaying = playbackState.isPlaying,
                            progress = if (playbackState.durationMs > 0) {
                                playbackState.positionMs.toFloat() / playbackState.durationMs
                            } else {
                                0f
                            },
                            onOpen = { openPlayer() },
                            onToggle = viewModel::togglePlayPause,
                            onNext = viewModel::next,
                            onLike = { viewModel.toggleFavorite(currentTrack) },
                            modifier = Modifier.widthIn(max = 820.dp)
                        )
                    }
                }

                if (hasBottomBar) {
                    AynaBottomBar(
                        current = activeRoute.orEmpty(),
                        onSelect = navigateTab
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    start = 18.dp,
                    end = 18.dp,
                    bottom = when {
                        fullPlayer -> 12.dp
                        hasBottomBar -> 138.dp
                        hasMiniPlayer -> 92.dp
                        else -> 12.dp
                    }
                )
        )
    }

    val activeSheet = playerSheet
    if (activeSheet != null) {
        ModalBottomSheet(
            onDismissRequest = { playerSheet = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            when (activeSheet) {
                PlayerSheet.Sleep -> SleepTimerSheet(
                    endsAt = playbackState.sleepEndsAt,
                    onSelect = { minutes ->
                        viewModel.setSleepTimer(minutes)
                        playerSheet = null
                    }
                )

                PlayerSheet.Speed -> SpeedSheet(
                    currentSpeed = playbackState.speed,
                    onSelect = { speed ->
                        viewModel.setPlaybackSpeed(speed)
                        playerSheet = null
                    }
                )

                PlayerSheet.Queue -> QueueSheet(
                    queue = playbackState.queue,
                    currentTrackId = playbackState.currentTrack?.id,
                    isPlaying = playbackState.isPlaying,
                    onSelect = { index ->
                        viewModel.playTracks(playbackState.queue, index)
                        playerSheet = null
                    },
                    onRemove = viewModel::removeQueueItem
                )

                PlayerSheet.Lyrics -> LyricsSheet(
                    state = lyricsState,
                    positionMs = playbackState.positionMs,
                    onRetry = {
                        viewModel.clearLyrics()
                        viewModel.loadLyrics()
                    },
                    onSeekToLine = viewModel::seekTo
                )

                PlayerSheet.Equalizer -> EqualizerSheet(
                    state = equalizerState,
                    onEnabledChange = viewModel::setEqualizerEnabled,
                    onBandChange = viewModel::setEqualizerBand,
                    onPresetSelected = viewModel::applyEqualizerPreset,
                    onBassChanged = viewModel::setBassBoost,
                    onReset = viewModel::resetEqualizer
                )

                is PlayerSheet.Actions -> {
                    val latest = activeSheet.track.withUserState(userStates[activeSheet.track.id])
                    TrackActionSheet(
                        track = latest,
                        playlistId = activeSheet.playlistId,
                        onDismiss = { playerSheet = null },
                        onToggleFavorite = { viewModel.toggleFavorite(latest) },
                        onPlayNext = {
                            viewModel.playNext(latest)
                            playerSheet = null
                        },
                        onAddToQueue = {
                            viewModel.addToQueue(latest)
                            playerSheet = null
                        },
                        onDownload = {
                            if (latest.isDownloaded) viewModel.removeDownload(latest)
                            else viewModel.downloadTrack(latest)
                            playerSheet = null
                        },
                        onOpenAddToPlaylist = {
                            playerSheet = PlayerSheet.AddToPlaylist(latest)
                        },
                        onRemoveFromPlaylist = activeSheet.playlistId?.let { listId ->
                            {
                                viewModel.removeFromPlaylist(listId, latest)
                                playerSheet = null
                            }
                        },
                        onOpenUrl = { url ->
                            if (!openExternal(context, url)) {
                                scope.launch { snackbarHostState.showSnackbar("No app can open that link") }
                            }
                            playerSheet = null
                        },
                        onShare = {
                            if (!shareTrack(context, latest)) {
                                scope.launch { snackbarHostState.showSnackbar("Sharing isn't available") }
                            }
                            playerSheet = null
                        }
                    )
                }

                is PlayerSheet.AddToPlaylist -> {
                    val latest = activeSheet.track.withUserState(userStates[activeSheet.track.id])
                    AddToPlaylistSheet(
                        track = latest,
                        playlists = playlists,
                        onSelect = { listId ->
                            viewModel.addToPlaylist(listId, latest)
                            playerSheet = null
                        },
                        onCreate = { name ->
                            val track = latest
                            playerSheet = null
                            viewModel.createPlaylist(name) { listId ->
                                viewModel.addToPlaylist(listId, track)
                            }
                        }
                    )
                }
            }
        }
    }
}
}

private fun openExternal(context: Context, url: String): Boolean {
    return runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }.isSuccess
}

private fun shareTrack(context: Context, track: Track): Boolean {
    val link = MusicCatalogRepository.deepLinks(track).firstOrNull()?.second
    val text = buildString {
        append("Listening to “${track.title}” by ${track.artist} on AYNAMusic")
        if (!link.isNullOrBlank()) append("\n$link")
    }
    return runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${track.title} · ${track.artist}")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(send, "Share track").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }.isSuccess
}
