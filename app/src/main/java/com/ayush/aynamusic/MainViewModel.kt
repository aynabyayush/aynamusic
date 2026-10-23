package com.ayush.aynamusic

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ayush.aynamusic.cloud.CloudSyncScheduler
import com.ayush.aynamusic.data.local.toTrack
import com.ayush.aynamusic.data.model.DownloadState
import com.ayush.aynamusic.data.model.HomeFeed
import com.ayush.aynamusic.data.model.Lyrics
import com.ayush.aynamusic.data.model.PlaylistSummary
import com.ayush.aynamusic.data.model.SearchResults
import com.ayush.aynamusic.data.model.Track
import com.ayush.aynamusic.data.model.TrackJson
import com.ayush.aynamusic.data.model.TrackSource
import com.ayush.aynamusic.data.model.UserTrackState
import com.ayush.aynamusic.data.prefs.AppSettings
import com.ayush.aynamusic.data.prefs.AudioQuality
import com.ayush.aynamusic.data.prefs.SecretStore
import com.ayush.aynamusic.data.prefs.ThemeMode
import com.ayush.aynamusic.data.remote.MusicTrackUtils
import com.ayush.aynamusic.playback.EqualizerManager
import com.ayush.aynamusic.playback.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class HomeUiState(
    val feed: HomeFeed? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null
)

data class SearchUiState(
    val query: String = "",
    val local: List<Track> = emptyList(),
    val full: List<Track> = emptyList(),
    val previews: List<Track> = emptyList(),
    val searching: Boolean = false,
    val error: String? = null
) {
    val hasResults: Boolean get() = local.isNotEmpty() || full.isNotEmpty() || previews.isNotEmpty()
}

data class PlaybackUiState(
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val sleepEndsAt: Long? = null
)

data class LyricsUiState(
    val trackId: String? = null,
    val loading: Boolean = false,
    val lyrics: Lyrics? = null,
    val error: String? = null,
    val loaded: Boolean = false
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as AynaApplication).container
    private val library = container.library

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val online: StateFlow<Boolean> = container.network.online
    val userStates: StateFlow<Map<String, UserTrackState>> = library.userStates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val favorites: StateFlow<List<Track>> = library.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val downloads: StateFlow<List<Track>> = library.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recent: StateFlow<List<Track>> = library.recent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val playlists: StateFlow<List<PlaylistSummary>> = library.playlistSummaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val equalizer = EqualizerManager.state

    private val _home = MutableStateFlow(HomeUiState())
    val home = _home.asStateFlow()

    private val _search = MutableStateFlow(SearchUiState())
    val search = _search.asStateFlow()

    private val _playback = MutableStateFlow(PlaybackUiState())
    val playback = _playback.asStateFlow()

    private val _lyrics = MutableStateFlow(LyricsUiState())
    val lyrics = _lyrics.asStateFlow()

    private val _localTracks = MutableStateFlow<List<Track>>(emptyList())
    val localTracks = _localTracks.asStateFlow()

    private val _localLoading = MutableStateFlow(false)
    val localLoading = _localLoading.asStateFlow()

    private val _importStates = MutableStateFlow<Map<String, com.ayush.aynamusic.data.model.PlaylistImportState>>(emptyMap())
    val importStates = _importStates.asStateFlow()

    private val _audioPermission = MutableStateFlow(container.localMusic.hasAudioPermission())
    val audioPermission = _audioPermission.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val toasts = _toasts.asSharedFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controllerReady = CompletableDeferred<MediaController>()
    private var homeJob: Job? = null
    private var searchJob: Job? = null
    private var tickerJob: Job? = null
    private var sleepJob: Job? = null
    private var lyricsJob: Job? = null
    private var uiVisible = false

    private val playedTrackIds = mutableSetOf<String>()
    private val playedSongKeys = mutableSetOf<String>()

    private fun recordPlayedTrack(track: Track) {
        playedTrackIds.add(track.id)
        val normTitle = MusicTrackUtils.normalizeTitle(track.title)
        if (normTitle.isNotBlank()) playedSongKeys.add(normTitle)
        val songKey = MusicTrackUtils.toSongKey(track.title, track.artist)
        if (songKey.isNotBlank()) playedSongKeys.add(songKey)
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncPlaybackState()
            mediaItem?.toTrack()?.let { track ->
                recordPlayedTrack(track)
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    viewModelScope.launch { library.recordPlay(track) }
                }
            }
            ensureTasteAutoplayQueue()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncPlaybackState()
            if (isPlaying) startTicker()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncPlaybackState()
            if (playbackState == Player.STATE_ENDED) {
                ensureTasteAutoplayQueue()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            syncPlaybackState()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            syncPlaybackState()
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            syncPlaybackState()
        }

        override fun onPlayerError(error: PlaybackException) {
            syncPlaybackState()
            _toasts.tryEmit(
                if (online.value) "Playback was interrupted. Try another track."
                else "You're offline. Downloaded tracks are still available."
            )
        }
    }

    init {
        connectToPlayer()
        observeConnectivity()
        loadHome(force = false)
        if (_audioPermission.value) scanLocalMusic()
        viewModelScope.launch { container.downloads.refreshDownloads() }
        viewModelScope.launch {
            container.settings.settings.collectLatest { current ->
                if (current.autoCloudSync && current.cloudConfigured) {
                    CloudSyncScheduler.ensurePeriodic(getApplication())
                }
            }
        }
    }

    private fun connectToPlayer() {
        val token = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java)
        )
        val future = MediaController.Builder(getApplication(), token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching {
                val c = future.get()
                if (controller != null) {
                    c.release()
                    return@runCatching
                }
                controller = c
                c.addListener(playerListener)
                if (c.mediaItemCount == 0) {
                    c.repeatMode = Player.REPEAT_MODE_ALL
                    c.shuffleModeEnabled = false
                }
                controllerReady.complete(c)
                syncPlaybackState()
                startTicker()
            }.onFailure {
                controllerReady.completeExceptionally(it)
                _toasts.tryEmit("Couldn't start the audio engine")
            }
        }, ContextCompat.getMainExecutor(getApplication()))
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            var previousOnline = online.value
            online.collect { isOnline ->
                if (isOnline && !previousOnline) {
                    if (_home.value.feed == null || isHomeStale()) loadHome(force = true)
                    val current = settings.value
                    if (current.autoCloudSync && current.cloudConfigured) {
                        CloudSyncScheduler.schedule(getApplication())
                    }
                }
                if (!isOnline) {
                    val playing = _playback.value
                    val track = playing.currentTrack
                    if (playing.isPlaying && track != null && !track.isLocal && !track.isDownloaded) {
                        controller?.pause()
                        _toasts.tryEmit("Connection lost. Playback paused.")
                    }
                }
                previousOnline = isOnline
                if (_search.value.query.length >= 2) runSearch(_search.value.query, debounce = false)
            }
        }
    }

    // ---------- Home ----------

    private fun isHomeStale(): Boolean {
        val updatedAt = _home.value.feed?.updatedAt ?: return true
        return System.currentTimeMillis() - updatedAt > 20 * 60_000L
    }

    fun loadHome(force: Boolean) {
        if (homeJob?.isActive == true) return
        homeJob = viewModelScope.launch {
            if (_home.value.feed == null) {
                container.catalog.loadCachedHome()?.let { cached ->
                    _home.update { it.copy(feed = cached, loading = false) }
                }
            }
            val isOnline = online.value
            if (!isOnline || settings.value.offlineOnly) {
                _home.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = if (it.feed == null) "You're offline. Download music to listen anywhere." else null
                    )
                }
                return@launch
            }
            if (!force && !isHomeStale() && _home.value.feed != null) {
                _home.update { it.copy(loading = false, refreshing = false) }
                return@launch
            }
            _home.update {
                it.copy(loading = it.feed == null, refreshing = it.feed != null, error = null)
            }
            runCatching { container.catalog.fetchHome() }
                .onSuccess { feed ->
                    _home.value = HomeUiState(feed = feed, loading = false, refreshing = false)
                }
                .onFailure { error ->
                    _home.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = if (it.feed == null) {
                                "Couldn't refresh the catalog. Check your connection."
                            } else null
                        )
                    }
                }
        }
    }

    fun refreshHome() = loadHome(force = true)

    // ---------- Search ----------

    fun setSearchQuery(query: String) {
        _search.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _search.update {
                it.copy(local = emptyList(), full = emptyList(), previews = emptyList(), searching = false, error = null)
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            runSearch(query, debounce = false)
        }
    }

    fun submitSearch(query: String) {
        if (query.trim().length < 2) return
        viewModelScope.launch { container.settings.addRecentSearch(query.trim()) }
        _search.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(query, debounce = false) }
    }

    private suspend fun runSearch(query: String, debounce: Boolean) {
        if (debounce) delay(350)
        val clean = query.trim()
        if (clean.length < 2) return
        _search.update {
            it.copy(
                query = query,
                local = emptyList(),
                searching = online.value && !settings.value.offlineOnly,
                error = null
            )
        }
        if (!online.value || settings.value.offlineOnly) {
            _search.update { it.copy(searching = false, error = "Offline mode active") }
            return
        }
        runCatching { container.catalog.search(clean) }
            .onSuccess { result ->
                if (_search.value.query == query) {
                    _search.update {
                        it.copy(
                            local = emptyList(),
                            full = result.full,
                            previews = result.previews,
                            searching = false,
                            error = if (result.isEmpty) "No matches. Try a different search." else null
                        )
                    }
                }
            }
            .onFailure {
                if (_search.value.query == query) {
                    _search.update {
                        it.copy(
                            searching = false,
                            error = "Search is unavailable right now."
                        )
                    }
                }
            }
    }

    fun addRecentSearch(query: String) {
        viewModelScope.launch { container.settings.addRecentSearch(query) }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { container.settings.clearRecentSearches() }
    }

    private fun searchLocal(query: String): List<Track> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val localIds = _localTracks.value.map { it.id }.toSet()
        val combined = _localTracks.value + downloads.value.filterNot { it.id in localIds }
        return combined.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.album?.contains(q, ignoreCase = true) == true
        }.take(40)
    }

    // ---------- On-device library ----------

    fun onAudioPermissionChanged(granted: Boolean) {
        _audioPermission.value = granted && container.localMusic.hasAudioPermission()
        if (_audioPermission.value) scanLocalMusic()
    }

    fun scanLocalMusic() {
        if (!container.localMusic.hasAudioPermission()) {
            _audioPermission.value = false
            return
        }
        _audioPermission.value = true
        if (_localLoading.value) return
        viewModelScope.launch {
            _localLoading.value = true
            val tracks = container.localMusic.loadTracks()
            _localTracks.value = tracks
            _localLoading.value = false
            container.settings.bumpLocalScanVersion()
          }
    }

    // ---------- Playback ----------

    private suspend fun awaitController(): MediaController? {
        controller?.let { return it }
        return runCatching {
            withTimeoutOrNull(6_000) { controllerReady.await() }
        }.getOrNull()
    }

    private fun MediaItem.toTrack(): Track? {
        val raw = mediaMetadata.extras?.getString(KEY_TRACK_JSON)
        if (!raw.isNullOrBlank()) {
            return runCatching { TrackJson.fromJson(org.json.JSONObject(raw)) }.getOrNull()
        }
        return Track(
            id = mediaId,
            title = mediaMetadata.title?.toString().orEmpty().ifBlank { "Unknown track" },
            artist = mediaMetadata.artist?.toString().orEmpty().ifBlank { "Unknown artist" },
            album = mediaMetadata.albumTitle?.toString(),
            artworkUrl = mediaMetadata.artworkUri?.toString(),
            durationMs = 0L,
            source = TrackSource.AUDIUS,
            streamUrl = ""
        )
    }

    private fun toMediaItem(track: Track): MediaItem {
        val extras = Bundle().apply { putString(KEY_TRACK_JSON, TrackJson.toJson(track).toString()) }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.artworkUrl?.let(android.net.Uri::parse))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.playableUri)
            .setMediaMetadata(metadata)
            .build()
    }

    private suspend fun resolveTracks(tracks: List<Track>): List<Track> {
        val fromDb = library.getTracks(tracks.map { it.id })
        val states = userStates.value
        return tracks.map { original ->
            val db = fromDb[original.id]
            val state = states[original.id]
            when {
                db != null -> original.copy(
                    isFavorite = db.isFavorite,
                    downloadState = db.downloadState,
                    localFilePath = db.localFilePath
                )
                state != null -> original.copy(
                    isFavorite = state.favorite,
                    downloadState = state.downloadState,
                    localFilePath = state.localFilePath
                )
                else -> original
            }
        }
    }

    fun playTrack(track: Track, queue: List<Track> = listOf(track)) {
        val index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playTracks(queue = queue.ifEmpty { listOf(track) }, startIndex = index)
    }

    fun playTracks(queue: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (queue.isEmpty()) return
        viewModelScope.launch {
            val c = awaitController() ?: return@launch
            val resolved = resolveTracks(queue)
            val offline = !online.value || settings.value.offlineOnly
            val chosen = resolved.getOrNull(startIndex) ?: resolved.first()
            if (offline && !chosen.isLocal && !chosen.isDownloaded) {
                _toasts.tryEmit("That track isn't saved offline. Download it first.")
                return@launch
            }
            val playable = if (offline) {
                resolved.filter { it.isLocal || it.isDownloaded }
            } else {
                resolved
            }
            if (playable.isEmpty()) {
                _toasts.tryEmit("Nothing here is available offline.")
                return@launch
            }
            val adjustedStart = playable.indexOfFirst { it.id == chosen.id }.let { if (it < 0) 0 else it }
            recordPlayedTrack(chosen)

            c.setMediaItems(playable.map(::toMediaItem), adjustedStart, 0L)
            c.shuffleModeEnabled = shuffle
            c.prepare()
            c.play()

            if (!offline) {
                ensureTasteAutoplayQueue()
            }
            if (offline && playable.size < resolved.size) {
                _toasts.tryEmit("Playing your downloaded tracks only.")
            }
        }
    }

    fun shufflePlay(tracks: List<Track>) {
        if (tracks.isEmpty()) {
            _toasts.tryEmit("There's nothing to shuffle yet.")
            return
        }
        playTracks(tracks.shuffled(), startIndex = 0, shuffle = false)
    }

    fun playNext(track: Track) {
        viewModelScope.launch {
            val c = awaitController() ?: return@launch
            if (!canPlayNow(track)) return@launch
            val resolved = resolveTracks(listOf(track)).first()
            val index = if (c.mediaItemCount == 0) 0 else (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
            if (c.mediaItemCount == 0) {
                c.setMediaItems(listOf(toMediaItem(resolved)))
                c.prepare()
                c.play()
            } else {
                c.addMediaItem(index, toMediaItem(resolved))
                _toasts.tryEmit("Playing next")
            }
        }
    }

    fun addToQueue(track: Track) {
        viewModelScope.launch {
            val c = awaitController() ?: return@launch
            if (!canPlayNow(track)) return@launch
            val resolved = resolveTracks(listOf(track)).first()
            if (c.mediaItemCount == 0) {
                c.setMediaItems(listOf(toMediaItem(resolved)))
                c.prepare()
            } else {
                c.addMediaItem(toMediaItem(resolved))
            }
            _toasts.tryEmit("Added to queue")
        }
    }

    private fun canPlayNow(track: Track): Boolean {
        if ((!online.value || settings.value.offlineOnly) && !track.isLocal && !track.isDownloaded) {
            _toasts.tryEmit("Download this track to play it offline.")
            return false
        }
        return true
    }

    fun removeQueueItem(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        if (c.mediaItemCount == 1) {
            c.stop()
            c.clearMediaItems()
        } else {
            c.removeMediaItem(index)
        }
        syncPlaybackState()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
            if (c.mediaItemCount > 0) c.play()
        }
    }

    fun next() {
        val c = controller ?: return
        if (c.hasNextMediaItem()) {
            c.seekToNextMediaItem()
        } else {
            ensureTasteAutoplayQueue()
        }
    }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3_000) c.seekTo(0) else if (c.hasPreviousMediaItem()) {
            c.seekToPreviousMediaItem()
        } else {
            c.seekTo(0)
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.let { c ->
            val duration = c.duration.takeIf { it > 0 } ?: _playback.value.currentTrack?.durationMs ?: 0L
            if (duration > 0) c.seekTo(positionMs.coerceIn(0L, duration))
        }
    }

    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        c.seekTo((c.currentPosition + deltaMs).coerceAtLeast(0L))
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun ensureTasteAutoplayQueue() {
        viewModelScope.launch {
            val c = controller ?: return@launch
            val currentIndex = c.currentMediaItemIndex
            val totalItems = c.mediaItemCount

            if (totalItems == 0 || currentIndex >= totalItems - 3 || c.playbackState == Player.STATE_ENDED) {
                val currentTrack = c.currentMediaItem?.toTrack() ?: _playback.value.currentTrack
                if (currentTrack == null) return@launch

                recordPlayedTrack(currentTrack)

                val offline = !online.value || settings.value.offlineOnly

                val existingQueueTracks = (0 until totalItems).mapNotNull { c.getMediaItemAt(it).toTrack() }
                val existingIds = existingQueueTracks.map { it.id }.toSet()
                val existingSongKeys = existingQueueTracks.flatMap { tr ->
                    listOf(
                        MusicTrackUtils.normalizeTitle(tr.title),
                        MusicTrackUtils.toSongKey(tr.title, tr.artist)
                    )
                }.filter { it.isNotBlank() }.toSet()

                val allExcludeIds = existingIds + playedTrackIds
                val allExcludeKeys = existingSongKeys + playedSongKeys

                // 1. Analyze User's Listening History (recent), Favorites, and Created Playlists for matching genre/language songs
                val userPlaylistTracks = runCatching { library.getAllUserPlaylistTracks() }.getOrDefault(emptyList())
                val userPersonalCollection = (recent.value + favorites.value + userPlaylistTracks).distinctBy { it.id }

                val matchingPersonalTracks = userPersonalCollection.filter { track ->
                    track.id != currentTrack.id &&
                    !allExcludeIds.contains(track.id) &&
                    !allExcludeKeys.contains(MusicTrackUtils.normalizeTitle(track.title)) &&
                    !allExcludeKeys.contains(MusicTrackUtils.toSongKey(track.title, track.artist)) &&
                    !MusicTrackUtils.isSpamOrFalseArtist(track.artist, track.title) &&
                    MusicTrackUtils.matchesGenreLanguageArtistAlbum(track, currentTrack) &&
                    (!offline || track.isLocal || track.isDownloaded)
                }
                .map { track ->
                    track to (MusicTrackUtils.calculateAffinityScore(track, currentTrack) + 20)
                }
                .filter { it.second > -100 }
                .sortedByDescending { it.second }
                .map { it.first }

                val candidates = if (offline) {
                    matchingPersonalTracks.take(20)
                } else {
                    val matchingPersonalIds = matchingPersonalTracks.map { it.id }.toSet()
                    val matchingPersonalKeys = matchingPersonalTracks.map { MusicTrackUtils.normalizeTitle(it.title) }.toSet()

                    val onlineRecommendations = runCatching {
                        container.catalog.fetchTasteBasedRecommendations(
                            seedTracks = listOf(currentTrack),
                            limit = 20,
                            excludeIds = allExcludeIds + matchingPersonalIds,
                            excludeSongKeys = allExcludeKeys + matchingPersonalKeys
                        )
                    }.getOrDefault(emptyList()).filter { track ->
                        track.id != currentTrack.id &&
                        !allExcludeIds.contains(track.id) &&
                        !allExcludeKeys.contains(MusicTrackUtils.normalizeTitle(track.title)) &&
                        !allExcludeKeys.contains(MusicTrackUtils.toSongKey(track.title, track.artist)) &&
                        !MusicTrackUtils.isSpamOrFalseArtist(track.artist, track.title) &&
                        MusicTrackUtils.matchesGenreLanguageArtistAlbum(track, currentTrack)
                    }

                    buildList {
                        var pIdx = 0
                        var oIdx = 0
                        val maxPersonal = minOf(matchingPersonalTracks.size, 6)
                        while ((pIdx < maxPersonal || oIdx < onlineRecommendations.size) && size < 20) {
                            if (pIdx < maxPersonal && (oIdx >= onlineRecommendations.size || size % 3 == 0)) {
                                add(matchingPersonalTracks[pIdx++])
                            } else if (oIdx < onlineRecommendations.size) {
                                add(onlineRecommendations[oIdx++])
                            } else {
                                break
                            }
                        }
                    }
                }

                if (candidates.isNotEmpty()) {
                    val newMediaItems = candidates.map(::toMediaItem)
                    val wasEnded = c.playbackState == Player.STATE_ENDED

                    if (totalItems == 0) {
                        c.setMediaItems(newMediaItems, 0, 0L)
                        c.prepare()
                        c.play()
                    } else {
                        c.addMediaItems(newMediaItems)
                        if (wasEnded) {
                            c.seekToNextMediaItem()
                            c.play()
                        }
                    }
                }
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed.coerceIn(0.5f, 2f))
    }

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _playback.update { it.copy(sleepEndsAt = null) }
            return
        }
        val end = System.currentTimeMillis() + minutes * 60_000L
        _playback.update { it.copy(sleepEndsAt = end) }
        sleepJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            _playback.update { it.copy(sleepEndsAt = null) }
            _toasts.tryEmit("Sleep timer ended")
        }
    }

    fun setUiVisible(visible: Boolean) {
        uiVisible = visible
        if (visible) {
            onAppForeground()
            startTicker()
        } else {
            tickerJob?.cancel()
            tickerJob = null
        }
    }

    fun onAppForeground() {
        viewModelScope.launch { container.downloads.refreshDownloads() }
        if (_audioPermission.value && _localTracks.value.isEmpty() && !_localLoading.value) {
            scanLocalMusic()
        }
    }

    private fun startTicker() {
        if (!uiVisible || tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (isActive) {
                syncPlaybackState()
                delay(if (controller?.isPlaying == true) 500L else 1_500L)
            }
        }
    }

    private fun syncPlaybackState() {
        val c = controller ?: return
        val queue = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).toTrack() }
        val current = c.currentMediaItem?.toTrack()
        _playback.update {
            it.copy(
                currentTrack = current,
                queue = queue,
                isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                positionMs = c.currentPosition.coerceAtLeast(0L),
                durationMs = c.duration.takeIf { duration -> duration > 0 }
                    ?: current?.durationMs ?: 0L,
                bufferedMs = c.bufferedPosition.coerceAtLeast(0L),
                shuffle = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
                speed = c.playbackParameters.speed
            )
        }
    }

    // ---------- Favorites, playlists, downloads ----------

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            val favorite = library.toggleFavorite(track)
            _toasts.tryEmit(if (favorite) "Added to Liked songs" else "Removed from Liked songs")
        }
    }

    fun downloadTrack(track: Track) {
        viewModelScope.launch {
            val result = container.downloads.enqueue(track, settings.value.wifiOnlyDownloads)
            if (result.isSuccess) {
                _toasts.tryEmit(if (track.preview) "Saving preview for offline listening" else "Download started")
                delay(300)
                container.downloads.refreshDownloads()
            } else {
                _toasts.tryEmit(result.exceptionOrNull()?.message ?: "Couldn't start the download")
            }
        }
    }

    fun removeDownload(track: Track) {
        viewModelScope.launch {
            val latest = library.getTrack(track.id) ?: track
            container.downloads.remove(latest)
            _toasts.tryEmit("Download removed")
        }
    }

    fun refreshDownloads() {
        viewModelScope.launch { container.downloads.refreshDownloads() }
    }

    fun createPlaylist(name: String, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            val id = library.createPlaylist(name)
            onCreated(id)
            _toasts.tryEmit("Playlist created")
        }
    }

    fun importSpotifyPlaylist(
        link: String,
        onProgress: (String) -> Unit = {},
        onComplete: (String?) -> Unit = {}
    ) {
        val playlistId = MusicTrackUtils.extractSpotifyPlaylistId(link)
        if (playlistId == null) {
            _toasts.tryEmit("Invalid Spotify playlist link.")
            onComplete(null)
            return
        }

        viewModelScope.launch {
            onProgress("Reading Spotify playlist...")
            val spotifyData = container.catalog.fetchSpotifyPlaylist(playlistId)
            if (spotifyData == null || spotifyData.tracks.isEmpty()) {
                _toasts.tryEmit("Unable to load Spotify playlist. Ensure it is set to Public.")
                onComplete(null)
                return@launch
            }

            onProgress("Creating playlist '${spotifyData.name}'...")
            val createdId = library.createPlaylist(spotifyData.name, spotifyData.description)
            val total = spotifyData.tracks.size

            onComplete(createdId)

            val initState = com.ayush.aynamusic.data.model.PlaylistImportState(
                playlistId = createdId,
                playlistName = spotifyData.name,
                current = 0,
                total = total,
                status = "Starting import..."
            )
            _importStates.update { it + (createdId to initState) }

            _toasts.tryEmit("Importing '${spotifyData.name}' ($total tracks) in background")

            var resolvedCount = 0
            val localTracks = _localTracks.value

            coroutineScope {
                spotifyData.tracks.chunked(6).forEachIndexed { chunkIndex, chunk ->
                    val jobs = chunk.mapIndexed { itemIndex, sTrack ->
                        val trackIndex = chunkIndex * 6 + itemIndex
                        async {
                            resolveSpotifyTrack(sTrack, createdId, trackIndex, localTracks)
                        }
                    }
                    jobs.forEach { job ->
                        val resolvedTrack = job.await()
                        library.addToPlaylist(createdId, resolvedTrack, scheduleSync = false)
                        resolvedCount++

                        _importStates.update { map ->
                            val prev = map[createdId]
                            if (prev != null) {
                                map + (createdId to prev.copy(current = resolvedCount, status = "Importing ($resolvedCount/$total)"))
                            } else map
                        }
                    }
                    onProgress("Resolving tracks ($resolvedCount/$total)...")
                }
            }

            _importStates.update { map ->
                val prev = map[createdId]
                if (prev != null) {
                    map + (createdId to prev.copy(current = resolvedCount, total = total, status = "Completed", isDone = true))
                } else map
            }

            CloudSyncScheduler.schedule(getApplication())

            _toasts.tryEmit("Imported ${spotifyData.name} ($resolvedCount tracks added)")

            delay(4000)
            _importStates.update { it - createdId }
        }
    }

    private suspend fun resolveSpotifyTrack(
        sTrack: com.ayush.aynamusic.data.model.SpotifyParsedTrack,
        playlistId: String,
        index: Int,
        localTracks: List<Track>
    ): Track {
        val title = sTrack.title.trim()
        val artist = sTrack.artist.trim()

        val localMatch = localTracks.firstOrNull { l ->
            l.title.equals(title, ignoreCase = true) &&
                (artist.isEmpty() || l.artist.contains(artist, ignoreCase = true) || artist.contains(l.artist, ignoreCase = true))
        }
        if (localMatch != null) return localMatch

        val cleanTitle = title
            .replace(Regex("(?i)\\s*\\(feat\\.[^)]+\\)|\\(with[^)]+\\)|\\(remastered[^)]*\\)|\\[explicit\\]|- remastered.*"), "")
            .trim()
        val cleanArtist = artist
            .replace(Regex("(?i)\\s*feat\\..*|,&.*"), "")
            .trim()

        val fullQuery = "$cleanTitle $cleanArtist".trim().ifBlank { title }
        var onlineResult = runCatching {
            container.catalog.searchFull(fullQuery, limit = 5).firstOrNull()
        }.getOrNull()

        if (onlineResult == null && cleanTitle.length > 2 && cleanTitle != fullQuery) {
            onlineResult = runCatching {
                container.catalog.searchFull(cleanTitle, limit = 5).firstOrNull()
            }.getOrNull()
        }

        if (onlineResult != null) return onlineResult

        val safeHash = Math.abs("${title}_${artist}".hashCode())
        val encodedQuery = runCatching { java.net.URLEncoder.encode("$title $artist", "UTF-8") }.getOrDefault("$title $artist")

        return Track(
            id = "spotify_${playlistId}_${index}_$safeHash",
            title = title,
            artist = if (artist.isNotBlank()) artist else "Unknown Artist",
            album = "Spotify Import",
            durationMs = 0L,
            source = TrackSource.SAAVN,
            streamUrl = "https://saavn.me/search?q=$encodedQuery",
            isFavorite = false,
            downloadState = DownloadState.NONE
        )
    }

    fun renamePlaylist(id: String, name: String) {
        viewModelScope.launch {
            library.renamePlaylist(id, name)
            _toasts.tryEmit("Playlist renamed")
        }
    }

    fun updatePlaylistCover(id: String, coverUrl: String?) {
        viewModelScope.launch {
            library.updatePlaylistCover(id, coverUrl)
            _toasts.tryEmit("Playlist cover updated")
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            library.deletePlaylist(id)
            _toasts.tryEmit("Playlist deleted")
        }
    }

    fun addToPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch {
            val added = library.addToPlaylist(playlistId, track)
            _toasts.tryEmit(if (added) "Added to playlist" else "Already in that playlist")
        }
    }

    fun removeFromPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch {
            library.removeFromPlaylist(playlistId, track.id)
            _toasts.tryEmit("Removed from playlist")
        }
    }

    fun playlistTracks(playlistId: String) = library.playlistTracks(playlistId)

    fun ensureTrack(track: Track) {
        viewModelScope.launch { library.ensureTrack(track) }
    }

    // ---------- Settings & cloud ----------

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { container.settings.setTheme(mode) }

    fun setAmoled(enabled: Boolean) = viewModelScope.launch { container.settings.setAmoled(enabled) }

    fun setDynamicColor(enabled: Boolean) =
        viewModelScope.launch { container.settings.setDynamicColor(enabled) }

    fun setOfflineOnly(enabled: Boolean) = viewModelScope.launch {
        container.settings.setOfflineOnly(enabled)
        if (enabled) {
            val current = _playback.value.currentTrack
            if (_playback.value.isPlaying && current != null && !current.isLocal && !current.isDownloaded) {
                controller?.pause()
            }
            _toasts.tryEmit(if (enabled) "Offline mode on" else "Offline mode off")
        }
    }

    fun setWifiOnlyDownloads(enabled: Boolean) =
        viewModelScope.launch { container.settings.setWifiOnlyDownloads(enabled) }

    fun setAudioQuality(quality: AudioQuality) = viewModelScope.launch {
        container.settings.setAudioQuality(quality)
        _toasts.tryEmit("Audio quality: ${quality.label}")
    }

    fun setAutoCloudSync(enabled: Boolean) = viewModelScope.launch {
        container.settings.setAutoCloudSync(enabled)
        if (enabled) {
            CloudSyncScheduler.ensurePeriodic(getApplication())
            CloudSyncScheduler.schedule(getApplication())
        } else {
            CloudSyncScheduler.cancelPeriodic(getApplication())
        }
    }

    fun setRegion(region: String) {
        viewModelScope.launch {
            container.settings.setRegion(region)
            loadHome(force = true)
        }
    }

    fun saveCloudConfig(url: String, user: String, password: String) {
        val clean = url.trim()
        if (clean.isNotBlank() && !clean.startsWith("https://")) {
            _toasts.tryEmit("Use an HTTPS WebDAV URL")
            return
        }
        viewModelScope.launch {
            container.settings.saveCloudConfig(clean, user)
            if (password.isNotBlank()) {
                container.secrets.put(SecretStore.KEY_CLOUD_PASSWORD, password)
            }
            if (clean.isNotBlank()) {
                CloudSyncScheduler.syncNow(getApplication())
                _toasts.tryEmit("Cloud backup configured • syncing")
            } else {
                _toasts.tryEmit("Cloud backup URL cleared")
            }
        }
    }

    fun disconnectCloud() {
        viewModelScope.launch {
            container.settings.clearCloudConfig()
            container.secrets.remove(SecretStore.KEY_CLOUD_PASSWORD)
            CloudSyncScheduler.cancelPeriodic(getApplication())
            _toasts.tryEmit("Cloud sync disconnected")
        }
    }

    fun syncNow() {
        if (!settings.value.cloudConfigured) {
            _toasts.tryEmit("Add a WebDAV backup URL first")
            return
        }
        if (!online.value) {
            _toasts.tryEmit("You're offline. Sync will run when you reconnect.")
            return
        }
        CloudSyncScheduler.syncNow(getApplication())
        _toasts.tryEmit("Syncing your library…")
    }

    // ---------- Lyrics ----------

    fun loadLyrics(allowNetwork: Boolean = true) {
        val track = _playback.value.currentTrack ?: return
        if (_lyrics.value.trackId == track.id && (_lyrics.value.loaded || _lyrics.value.loading)) return
        lyricsJob?.cancel()
        _lyrics.value = LyricsUiState(trackId = track.id, loading = true)
        lyricsJob = viewModelScope.launch {
            val result = container.lyrics.lyricsFor(
                track,
                allowNetwork = allowNetwork && online.value && !settings.value.offlineOnly
            )
            _lyrics.value = LyricsUiState(
                trackId = track.id,
                loading = false,
                lyrics = result,
                loaded = true,
                error = if (result == null) "No lyrics found for this track" else null
            )
        }
    }

    fun clearLyrics() {
        lyricsJob?.cancel()
        _lyrics.value = LyricsUiState()
    }

    // ---------- Equalizer ----------

    fun setEqualizerEnabled(enabled: Boolean) = EqualizerManager.setEnabled(enabled)
    fun setEqualizerBand(index: Int, levelMb: Int) = EqualizerManager.setBandLevel(index, levelMb)
    fun applyEqualizerPreset(preset: Int) = EqualizerManager.applyPreset(preset)
    fun setBassBoost(strength: Int) = EqualizerManager.setBassBoost(strength)
    fun resetEqualizer() = EqualizerManager.reset()

    fun trackLabel(track: Track): String = "${track.title} • ${track.artist}"

    override fun onCleared() {
        tickerJob?.cancel()
        sleepJob?.cancel()
        searchJob?.cancel()
        homeJob?.cancel()
        lyricsJob?.cancel()
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        super.onCleared()
    }

    companion object {
        private const val KEY_TRACK_JSON = "ayna_track"
    }
}
