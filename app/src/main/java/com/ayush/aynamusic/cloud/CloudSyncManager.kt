package com.ayush.aynamusic.cloud

import android.content.Context
import com.ayush.aynamusic.data.library.LibraryRepository
import com.ayush.aynamusic.data.local.PlaylistEntity
import com.ayush.aynamusic.data.model.Track
import com.ayush.aynamusic.data.model.TrackJson
import com.ayush.aynamusic.data.model.TrackSource
import com.ayush.aynamusic.data.model.optionalString
import com.ayush.aynamusic.data.prefs.SecretStore
import com.ayush.aynamusic.data.prefs.SettingsRepository
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class CloudSyncManager(
    context: Context,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val secrets: SecretStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private data class CloudPlaylist(
        val id: String,
        val name: String,
        val description: String?,
        val createdAt: Long,
        val updatedAt: Long,
        val tracks: List<Triple<Track, Int, Long>>
    )

    private data class RemoteSnapshot(
        val favorites: List<Track>,
        val playlists: List<CloudPlaylist>,
        val recent: List<Pair<Track, Long>>
    )

    suspend fun sync(): String = withContext(Dispatchers.IO) {
        val config = settings.current()
        if (!config.cloudConfigured) throw IOException("Add a WebDAV backup URL first")
        validateUrl(config.cloudUrl)
        val password = secrets.get(SecretStore.KEY_CLOUD_PASSWORD)
        val remoteRaw = download(config.cloudUrl, config.cloudUser, password)
        val remote = remoteRaw?.let(::parseRemote)
        val now = System.currentTimeMillis()

        val localFavorites = library.favorites.first()
            .filter { it.source != TrackSource.LOCAL }
        val localPlaylists = library.getAllPlaylists().map { p ->
            CloudPlaylist(
                id = p.id,
                name = p.name,
                description = p.description,
                createdAt = p.createdAt,
                updatedAt = p.updatedAt,
                tracks = library.playlistEntries(p.id)
                    .filter { it.first.source != TrackSource.LOCAL }
            )
        }
        val localRecents = library.recent.first()
            .filter { it.source != TrackSource.LOCAL }
            .mapIndexed { index, track -> track to (now - index * 1_000L) }

        val favoriteMap = linkedMapOf<String, Track>()
        localFavorites.forEach { favoriteMap[it.id] = it }
        remote?.favorites?.forEach { favoriteMap.putIfAbsent(it.id, it) }

        val playlistMap = linkedMapOf<String, CloudPlaylist>()
        localPlaylists.forEach { playlistMap[it.id] = it }
        remote?.playlists?.forEach { remotePlaylist ->
            val local = playlistMap[remotePlaylist.id]
            if (local == null || (remotePlaylist.updatedAt > local.updatedAt && remotePlaylist.tracks.size >= local.tracks.size)) {
                playlistMap[remotePlaylist.id] = remotePlaylist
                library.importPlaylistFromCloud(
                    PlaylistEntity(
                        id = remotePlaylist.id,
                        name = remotePlaylist.name,
                        description = remotePlaylist.description,
                        createdAt = remotePlaylist.createdAt,
                        updatedAt = remotePlaylist.updatedAt
                    ),
                    remotePlaylist.tracks
                )
            }
        }

        val remoteFavoritesToImport = favoriteMap.values.filter { remoteTrack ->
            localFavorites.none { it.id == remoteTrack.id }
        }
        if (remoteFavoritesToImport.isNotEmpty()) {
            library.importFavoritesFromCloud(remoteFavoritesToImport)
        }

        val recentMap = linkedMapOf<String, Pair<Track, Long>>()
        localRecents.forEach { (track, time) -> recentMap[track.id] = track to time }
        remote?.recent?.forEach { (track, time) ->
            val current = recentMap[track.id]
            if (current == null || time > current.second) recentMap[track.id] = track to time
        }
        if (remote != null && remote.recent.isNotEmpty()) {
            library.importRecentFromCloud(remote.recent)
        }

        val merged = JSONObject().apply {
            put("schema", 1)
            put("app", "AYNAMusic")
            put("updatedAt", now)
            put("favorites", JSONArray().apply {
                favoriteMap.values.forEach { put(TrackJson.toJson(it)) }
            })
            put("playlists", JSONArray().apply {
                playlistMap.values.forEach { playlist ->
                    put(JSONObject().apply {
                        put("id", playlist.id)
                        put("name", playlist.name)
                        put("description", playlist.description ?: JSONObject.NULL)
                        put("createdAt", playlist.createdAt)
                        put("updatedAt", playlist.updatedAt)
                        put("tracks", JSONArray().apply {
                            playlist.tracks.sortedBy { it.second }.forEach { (track, position, addedAt) ->
                                put(JSONObject().apply {
                                    put("track", TrackJson.toJson(track))
                                    put("position", position)
                                    put("addedAt", addedAt)
                                })
                            }
                        })
                    })
                }
            })
            put("recent", JSONArray().apply {
                recentMap.values.sortedByDescending { it.second }.take(100).forEach { (track, playedAt) ->
                    put(JSONObject().apply {
                        put("track", TrackJson.toJson(track))
                        put("playedAt", playedAt)
                    })
                }
            })
        }
        upload(config.cloudUrl, config.cloudUser, password, merged.toString())
        val playlistCount = playlistMap.size
        val favoriteCount = favoriteMap.size
        if (remote == null) {
            "Created cloud backup • $favoriteCount liked • $playlistCount playlists"
        } else {
            "Synced • $favoriteCount liked • $playlistCount playlists"
        }
    }

    private fun validateUrl(url: String) {
        val parsed = url.trim().toHttpUrlOrNull()
            ?: throw IOException("That doesn't look like a valid backup URL")
        if (parsed.scheme != "https") {
            throw IOException("Cloud sync requires an HTTPS WebDAV URL")
        }
    }

    private fun download(url: String, user: String, password: String?): String? {
        val builder = Request.Builder().url(url).get()
        addAuth(builder, user, password)
        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 404) return null
            if (response.code == 401 || response.code == 403) {
                throw IOException("HTTP ${response.code}")
            }
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} while reading backup")
            return response.body?.string()?.takeIf { it.isNotBlank() }
        }
    }

    private fun upload(url: String, user: String, password: String?, json: String) {
        val builder = Request.Builder()
            .url(url)
            .put(json.toRequestBody(JSON_MEDIA_TYPE))
        addAuth(builder, user, password)
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} while saving backup")
        }
    }

    private fun addAuth(builder: Request.Builder, user: String, password: String?) {
        if (user.isNotBlank()) {
            builder.header("Authorization", Credentials.basic(user, password.orEmpty()))
        }
        builder.header("User-Agent", "AYNAMusic/1.0 cloud-sync")
    }

    private fun parseRemote(raw: String): RemoteSnapshot {
        val root = runCatching { JSONObject(raw) }.getOrElse {
            throw IOException("The remote backup isn't valid AYNAMusic data")
        }
        val favorites = parseTracks(root.optJSONArray("favorites"))
        val playlists = buildList {
            val array = root.optJSONArray("playlists") ?: return@buildList
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optionalString("id") ?: continue
                val name = item.optionalString("name") ?: "Playlist"
                val tracks = buildList {
                    val trackArray = item.optJSONArray("tracks") ?: return@buildList
                    for (j in 0 until trackArray.length()) {
                        val entry = trackArray.optJSONObject(j) ?: continue
                        val trackJson = entry.optJSONObject("track") ?: continue
                        val track = runCatching { TrackJson.fromJson(trackJson) }.getOrNull() ?: continue
                        add(
                            Triple(
                                track,
                                entry.optInt("position", j),
                                entry.optLong("addedAt", 0L)
                            )
                        )
                    }
                }
                add(
                    CloudPlaylist(
                        id = id,
                        name = name,
                        description = item.optionalString("description"),
                        createdAt = item.optLong("createdAt", 0L),
                        updatedAt = item.optLong("updatedAt", 0L),
                        tracks = tracks
                    )
                )
            }
        }
        val recent = buildList {
            val array = root.optJSONArray("recent") ?: return@buildList
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val trackJson = item.optJSONObject("track") ?: continue
                val track = runCatching { TrackJson.fromJson(trackJson) }.getOrNull() ?: continue
                add(track to item.optLong("playedAt", 0L))
            }
        }
        return RemoteSnapshot(favorites, playlists, recent)
    }

    private fun parseTracks(array: JSONArray?): List<Track> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val track = runCatching { TrackJson.fromJson(item) }.getOrNull() ?: continue
                if (track.source != TrackSource.LOCAL) add(track)
            }
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
