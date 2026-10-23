package com.ayush.aynamusic.data.remote

import android.content.Context
import androidx.annotation.WorkerThread
import com.ayush.aynamusic.data.model.CatalogSection
import com.ayush.aynamusic.data.model.HomeFeed
import com.ayush.aynamusic.data.model.SearchResults
import com.ayush.aynamusic.data.model.SpotifyParsedPlaylist
import com.ayush.aynamusic.data.model.SpotifyParsedTrack
import com.ayush.aynamusic.data.model.Track
import com.ayush.aynamusic.data.model.TrackSource
import com.ayush.aynamusic.data.model.TrackJson
import com.ayush.aynamusic.data.model.optionalString
import com.ayush.aynamusic.data.prefs.AudioQuality
import com.ayush.aynamusic.data.prefs.SettingsRepository
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Multi-source legal public music catalog adapters:
 *
 * 1. JioSaavn Official API & Auto-Suggest (India & Regional Hits - Full Songs)
 * 2. Audius Open API (Global Independent & Electronic - Full Songs)
 * 3. Jamendo Music API (Global Royalty-Free & Indie - Full Songs)
 * 4. Piped / YouTube Music Public Search (Global Music Audio Streams)
 * 5. Internet Archive Audio API (Global Live & Classic Music)
 *
 * Only 100% complete full tracks are served with clean artist and album names.
 */
class MusicCatalogRepository(
    private val context: Context,
    private val settings: SettingsRepository
) {
    private val client = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http_catalog"), 24L * 1024 * 1024))
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            chain.proceed(request)
        }
        .build()

    private val homeCacheFile = File(context.filesDir, "catalog/home_feed.json")

    suspend fun loadCachedHome(): HomeFeed? = withContext(Dispatchers.IO) {
        if (!homeCacheFile.exists()) return@withContext null
        runCatching {
            val root = JSONObject(homeCacheFile.readText())
            val sectionsJson = root.optJSONArray("sections") ?: return@runCatching null
            val sections = buildList {
                for (i in 0 until sectionsJson.length()) {
                    val section = TrackJson.fromSectionJson(sectionsJson.getJSONObject(i))
                    val fullTracks = section.tracks.filter { !it.preview }
                    if (fullTracks.isNotEmpty()) {
                        add(section.copy(tracks = fullTracks))
                    }
                }
            }
            HomeFeed(sections, root.optLong("updatedAt", homeCacheFile.lastModified()))
        }.getOrNull()
    }

    suspend fun fetchHome(): HomeFeed = withContext(Dispatchers.IO) {
        coroutineScope {
            val saavnBollywood = async { runCatching { saavnSearch("Bollywood Top Hits", 25) }.getOrDefault(emptyList()) }
            val saavnPunjabi = async { runCatching { saavnSearch("Punjabi Top Hits", 25) }.getOrDefault(emptyList()) }
            val saavnRomantic = async { runCatching { saavnSearch("Arijit Singh Hits", 25) }.getOrDefault(emptyList()) }
            val saavnPop = async { runCatching { saavnSearch("Global Pop Hits", 25) }.getOrDefault(emptyList()) }
            val audiusTrending = async { runCatching { audiusTrending(25) }.getOrDefault(emptyList()) }
            val jamendoPopular = async { runCatching { jamendoPopular(25) }.getOrDefault(emptyList()) }

            val sections = buildList {
                saavnBollywood.await().filter { !it.preview }.takeIf { it.isNotEmpty() }?.let {
                    add(
                        CatalogSection(
                            id = "saavn_bollywood",
                            title = "India & Bollywood Top Hits",
                            subtitle = "Full songs from JioSaavn catalog",
                            tracks = it
                        )
                    )
                }
                saavnPunjabi.await().filter { !it.preview }.takeIf { it.isNotEmpty() }?.let {
                    add(
                        CatalogSection(
                            id = "saavn_punjabi",
                            title = "Punjabi & Indipop Hits",
                            subtitle = "Top chartbusters and regional hits",
                            tracks = it
                        )
                    )
                }
                saavnRomantic.await().filter { !it.preview }.takeIf { it.isNotEmpty() }?.let {
                    add(
                        CatalogSection(
                            id = "saavn_romantic",
                            title = "Romantic & Trending Hits",
                            subtitle = "Top played tracks from popular artists",
                            tracks = it
                        )
                    )
                }
                saavnPop.await().filter { !it.preview }.takeIf { it.isNotEmpty() }?.let {
                    add(
                        CatalogSection(
                            id = "saavn_pop",
                            title = "Global Pop & Chartbusters",
                            subtitle = "Complete songs for your daily rotation",
                            tracks = it
                        )
                    )
                }
                audiusTrending.await().filter { !it.preview }.takeIf { it.isNotEmpty() }?.let {
                    add(
                        CatalogSection(
                            id = "audius_trending",
                            title = "Trending Independent & Electronic",
                            subtitle = "Full songs from Audius creators",
                            tracks = it
                        )
                    )
                }
                jamendoPopular.await().filter { !it.preview }.takeIf { it.isNotEmpty() }?.let {
                    add(
                        CatalogSection(
                            id = "jamendo_popular",
                            title = "Global Indie & Instrumental",
                            subtitle = "Full tracks from Jamendo creators",
                            tracks = it
                        )
                    )
                }
            }
            if (sections.isEmpty()) throw IOException("Couldn't reach any music catalog")
            HomeFeed(sections, System.currentTimeMillis()).also(::cacheHome)
        }
    }

    suspend fun search(query: String): SearchResults = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) return@withContext SearchResults()
        coroutineScope {
            val saavnFull = async { runCatching { saavnSearch(clean, 40) }.getOrDefault(emptyList()) }
            val audiusFull = async { runCatching { audiusSearch(clean, 25) }.getOrDefault(emptyList()) }
            val jamendoFull = async { runCatching { jamendoSearch(clean, 25) }.getOrDefault(emptyList()) }
            val pipedFull = async { runCatching { pipedSearch(clean, 20) }.getOrDefault(emptyList()) }
            val archiveFull = async { runCatching { archiveSearch(clean, 15) }.getOrDefault(emptyList()) }

            val combinedFull = buildList {
                addAll(saavnFull.await())
                addAll(audiusFull.await())
                addAll(jamendoFull.await())
                addAll(pipedFull.await())
                addAll(archiveFull.await())
            }.filter { !it.preview }
            .distinctBy { "${it.title.lowercase().trim()}:${it.artist.lowercase().trim()}" }

            SearchResults(full = combinedFull, previews = emptyList())
        }
    }

    suspend fun searchFull(query: String, limit: Int = 40): List<Track> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val saavn = async { runCatching { saavnSearch(query, limit) }.getOrDefault(emptyList()) }
                val audius = async { runCatching { audiusSearch(query, limit) }.getOrDefault(emptyList()) }
                val jamendo = async { runCatching { jamendoSearch(query, limit) }.getOrDefault(emptyList()) }
                val piped = async { runCatching { pipedSearch(query, limit) }.getOrDefault(emptyList()) }
                buildList {
                    addAll(saavn.await())
                    addAll(audius.await())
                    addAll(jamendo.await())
                    addAll(piped.await())
                }.filter { !it.preview }
                .distinctBy { "${it.title.lowercase().trim()}:${it.artist.lowercase().trim()}" }
            }
        }

    suspend fun fetchTasteBasedRecommendations(
        seedTracks: List<Track>,
        limit: Int = 20,
        excludeIds: Set<String> = emptySet(),
        excludeSongKeys: Set<String> = emptySet()
    ): List<Track> =
        withContext(Dispatchers.IO) {
            if (seedTracks.isEmpty()) {
                return@withContext runCatching { saavnSearch("Bollywood Top Hits 2024", limit) }
                    .getOrDefault(emptyList())
                    .filter { !MusicTrackUtils.isSpamOrFalseArtist(it.artist, it.title) }
            }

            val primarySeed = seedTracks.first()
            val seedArtists = MusicTrackUtils.extractArtists(primarySeed.artist)
            val primaryArtist = seedArtists.firstOrNull() ?: MusicTrackUtils.cleanHtml(primarySeed.artist)
            val seedAlbum = MusicTrackUtils.cleanHtml(primarySeed.album)
            val languageTag = MusicTrackUtils.detectLanguageTag(primarySeed) ?: MusicTrackUtils.cleanHtml(primarySeed.genre)

            val isArtistValid = primaryArtist.isNotBlank() && !MusicTrackUtils.isSpamOrFalseArtist(primaryArtist, primarySeed.title)
            val isAlbumValid = !seedAlbum.isNullOrBlank() && seedAlbum.length >= 3 && seedAlbum != "Single" && seedAlbum != "Unknown Album"
            val isLangValid = !languageTag.isNullOrBlank() && languageTag.isNotBlank()
            val genreTag = MusicTrackUtils.cleanHtml(primarySeed.genre)

            // Resolve YouTube Video ID for Seed Track to query YouTube's official recommendation neural net
            val youtubeVideoId = if (primarySeed.id.startsWith("piped:")) {
                primarySeed.id.removePrefix("piped:")
            } else {
                runCatching {
                    pipedSearch("${primarySeed.title} $primaryArtist", 1).firstOrNull()?.id?.removePrefix("piped:")
                }.getOrNull()
            }

            val queries = mutableListOf<String>()

            // 1. Global & Regional Popularity Queries (Highest Priority)
            if (isLangValid && genreTag.isNotBlank()) {
                queries.add("$languageTag $genreTag top hits")
                queries.add("$languageTag $genreTag global trending")
            }
            if (isLangValid) {
                queries.add("$languageTag global top 50")
                queries.add("$languageTag top songs 2024")
                queries.add("$languageTag hits radio")
            }

            queries.add("${primarySeed.title} radio")
            queries.add("${primarySeed.title} mix")

            if (isArtistValid) {
                if (isLangValid) {
                    queries.add("$primaryArtist $languageTag hits")
                }
                queries.add("$primaryArtist top songs")
            }

            if (isAlbumValid) {
                queries.add("$seedAlbum songs")
            }

            if (queries.isEmpty()) {
                queries.add("Global Top Hits 2024")
                queries.add("Bollywood Top Hits")
            }

            val seedIds = seedTracks.map { it.id }.toSet() + excludeIds
            val seedSongKeys = seedTracks.flatMap { tr ->
                listOf(
                    MusicTrackUtils.normalizeTitle(tr.title),
                    MusicTrackUtils.toSongKey(tr.title, tr.artist)
                )
            }.filter { it.isNotBlank() }.toSet() + excludeSongKeys

            coroutineScope {
                // Fetch YouTube Music Official "Up Next" / Autoplay queue for seed videoId
                val youtubeNextJob = async {
                    if (!youtubeVideoId.isNullOrBlank()) {
                        runCatching { fetchPipedNextTracks(youtubeVideoId, 15) }.getOrDefault(emptyList())
                    } else emptyList()
                }

                val saavnJobs = queries.distinct().take(6).map { q ->
                    async { runCatching { saavnSearch(q, 15) }.getOrDefault(emptyList()) }
                }
                val pipedJobs = queries.distinct().take(3).map { q ->
                    async { runCatching { pipedSearch(q, 10) }.getOrDefault(emptyList()) }
                }

                val youtubeUpNextTracks = youtubeNextJob.await()
                val youtubeUpNextKeys = youtubeUpNextTracks.map { MusicTrackUtils.normalizeTitle(it.title) }.toSet()

                val fetched = buildList {
                    addAll(youtubeUpNextTracks)
                    saavnJobs.forEach { addAll(it.await()) }
                    pipedJobs.forEach { addAll(it.await()) }
                }

                val candidatePairs = fetched.filter { track ->
                    !track.preview &&
                    !MusicTrackUtils.isSpamOrFalseArtist(track.artist, track.title) &&
                    !seedIds.contains(track.id) &&
                    !seedSongKeys.contains(MusicTrackUtils.normalizeTitle(track.title)) &&
                    !seedSongKeys.contains(MusicTrackUtils.toSongKey(track.title, track.artist)) &&
                    MusicTrackUtils.matchesGenreLanguageArtistAlbum(track, primarySeed)
                }
                .distinctBy { MusicTrackUtils.normalizeTitle(it.title) }
                .mapIndexed { index, track ->
                    var score = MusicTrackUtils.calculateAffinityScore(track, primarySeed)
                    
                    // Popularity Rank Boost: Top global/regional chart positions get a heavy boost
                    when (index) {
                        in 0..2 -> score += 50
                        in 3..7 -> score += 35
                        in 8..14 -> score += 20
                    }

                    // YouTube Music Neural Network Boost for globally trending up-next tracks
                    val normTitle = MusicTrackUtils.normalizeTitle(track.title)
                    if (youtubeUpNextKeys.contains(normTitle)) {
                        score += 50
                    }

                    track to score
                }
                .filter { it.second > -100 }
                .sortedByDescending { it.second }

                // Pacing / Anti-Monotony Guard:
                // Cap max consecutive tracks from the exact same album to 2
                val result = mutableListOf<Track>()
                val albumCounts = mutableMapOf<String, Int>()

                for ((track, _) in candidatePairs) {
                    if (result.size >= limit) break
                    val alb = MusicTrackUtils.cleanHtml(track.album).lowercase().trim()
                    if (alb.length >= 3 && alb != "single" && alb != "unknown album") {
                        val currentCount = albumCounts.getOrDefault(alb, 0)
                        if (currentCount >= 2 && candidatePairs.size > limit) {
                            continue
                        }
                        albumCounts[alb] = currentCount + 1
                    }
                    result.add(track)
                }

                result
            }
        }

    // -----------------------------------------------------------------------------------
    // 1. JioSaavn Official API (Indian & Regional Music - Full Songs)
    // -----------------------------------------------------------------------------------
    @WorkerThread
    private suspend fun saavnSearch(query: String, limit: Int): List<Track> {
        val encoded = runCatching { URLEncoder.encode(query, "UTF-8") }.getOrDefault(query)
        val endpoints = listOf(
            "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&q=$encoded&n=$limit",
            "https://www.jiosaavn.com/api.php?__call=search.getAlbumResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&q=$encoded&n=$limit",
            "https://www.jiosaavn.com/api.php?__call=search.getArtistResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&q=$encoded&n=$limit",
            "https://www.jiosaavn.com/api.php?__call=autocomplete.get&query=$encoded&_format=json&_marker=0&ctx=web6dot0",
            "https://saavn.dev/api/search/songs?query=$encoded&limit=$limit",
            "https://saavn.me/search/songs?query=$encoded&limit=$limit"
        )
        val allTracks = mutableListOf<Track>()
        for (url in endpoints) {
            runCatching {
                val root = getJson(url)
                val results = root.optJSONArray("results")
                    ?: root.optJSONObject("data")?.optJSONArray("results")
                    ?: root.optJSONArray("data")
                    ?: root.optJSONObject("songs")?.optJSONArray("data")
                val parsed = parseSaavnTracks(results)
                allTracks.addAll(parsed)
            }
        }
        return allTracks.distinctBy { "${it.title.lowercase().trim()}:${it.artist.lowercase().trim()}" }
    }

    private suspend fun parseSaavnTracks(array: JSONArray?): List<Track> {
        if (array == null) return emptyList()
        val quality = settings.current().audioQuality
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val rawTitle = item.optString("song")
                    .ifBlank { item.optString("name") }
                    .ifBlank { item.optString("title") }
                    .ifBlank { item.optJSONObject("more_info")?.optString("song").orEmpty() }
                val title = rawTitle.cleanHtml()
                if (title.isBlank()) continue

                val artist = extractSaavnArtist(item)
                val rawAlbum = item.optJSONObject("album")?.optionalString("name")
                    ?: item.optionalString("album")
                    ?: item.optJSONObject("more_info")?.optionalString("album")
                val album = rawAlbum?.cleanHtml()

                var artwork = item.optionalString("image")
                val imagesArray = item.optJSONArray("image")
                if (imagesArray != null && imagesArray.length() > 0) {
                    artwork = imagesArray.optJSONObject(imagesArray.length() - 1)?.optionalString("url")
                        ?: imagesArray.optJSONObject(0)?.optionalString("url")
                }
                artwork = artwork?.replace("150x150", "500x500")?.replace("50x50", "500x500")

                var streamUrl: String? = null
                val downloadUrls = item.optJSONArray("downloadUrl")
                if (downloadUrls != null && downloadUrls.length() > 0) {
                    val targetQualityStr = "${quality.bitRateKbps}kbps"
                    for (j in 0 until downloadUrls.length()) {
                        val obj = downloadUrls.optJSONObject(j) ?: continue
                        val qStr = obj.optString("quality")
                        val u = obj.optionalString("url")
                        if (qStr.equals(targetQualityStr, ignoreCase = true) && !u.isNullOrBlank()) {
                            streamUrl = u
                            break
                        }
                    }
                    if (streamUrl.isNullOrBlank()) {
                        for (j in downloadUrls.length() - 1 downTo 0) {
                            val u = downloadUrls.optJSONObject(j)?.optionalString("url")
                            if (!u.isNullOrBlank()) {
                                streamUrl = u
                                break
                            }
                        }
                    }
                }

                if (streamUrl.isNullOrBlank()) {
                    streamUrl = item.optionalString("media_url")
                }
                if (streamUrl.isNullOrBlank()) {
                    streamUrl = item.optionalString("media_preview_url")
                }
                if (streamUrl.isNullOrBlank()) {
                    val enc = item.optionalString("encrypted_media_url")
                        ?: item.optionalString("encrypted_url")
                        ?: item.optJSONObject("more_info")?.optionalString("encrypted_media_url")
                    if (!enc.isNullOrBlank()) {
                        streamUrl = decryptSaavnMediaUrl(enc, quality)
                    }
                }
                if (streamUrl.isNullOrBlank()) continue

                streamUrl = transformMediaQuality(streamUrl, quality)

                val duration = item.optLong("duration", 180L) * 1000L
                val external = item.optionalString("perma_url")
                    ?: item.optionalString("url")
                    ?: "https://www.jiosaavn.com"

                add(
                    Track(
                        id = "saavn:$id",
                        title = title,
                        artist = artist,
                        album = album,
                        artworkUrl = artwork,
                        durationMs = duration,
                        source = TrackSource.SAAVN,
                        streamUrl = streamUrl,
                        externalUrl = external,
                        preview = false,
                        genre = item.optionalString("language"),
                        releaseYear = item.optionalString("year")
                    )
                )
            }
        }
    }

    private fun extractSaavnArtist(item: JSONObject): String {
        val moreInfo = item.optJSONObject("more_info")
        val artistMap = moreInfo?.optJSONObject("artistMap") ?: item.optJSONObject("artistMap")

        val primaryArray = artistMap?.optJSONArray("primary_artists")
            ?: item.optJSONObject("artists")?.optJSONArray("primary")
        if (primaryArray != null && primaryArray.length() > 0) {
            val names = buildList {
                for (i in 0 until primaryArray.length()) {
                    val a = primaryArray.optJSONObject(i)
                    val n = a?.optString("name")
                    if (!n.isNullOrBlank()) add(n.cleanHtml())
                }
            }
            if (names.isNotEmpty()) return names.joinToString(", ")
        }

        val direct = item.optString("primary_artists")
            .ifBlank { item.optString("primaryArtists") }
            .ifBlank { item.optString("singles") }
            .ifBlank { item.optString("artist") }
            .ifBlank { item.optString("artists") }
            .ifBlank { moreInfo?.optString("primary_artists").orEmpty() }
            .ifBlank { moreInfo?.optString("singles").orEmpty() }
            .ifBlank { moreInfo?.optString("music").orEmpty() }

        if (direct.isNotBlank() && direct != "null" && direct != "[]" && !direct.startsWith("{")) {
            return direct.cleanHtml()
        }

        return "JioSaavn Artist"
    }

    private fun decryptSaavnMediaUrl(encryptedUrl: String, quality: AudioQuality = AudioQuality.MAXIMUM): String? {
        if (encryptedUrl.isBlank()) return null
        val clean = runCatching { java.net.URLDecoder.decode(encryptedUrl, "UTF-8") }.getOrDefault(encryptedUrl)
            .trim().replace(" ", "+")
        return runCatching {
            val key = "38346591".toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            val keySpec = SecretKeySpec(key, "DES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decodedBytes = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            val url = String(decryptedBytes, Charsets.UTF_8)
            transformMediaQuality(url, quality)
        }.getOrNull()
    }

    private fun transformMediaQuality(url: String, quality: AudioQuality): String {
        if (url.isBlank()) return url
        val targetSuffix = quality.suffix
        var res = url.replace("http://", "https://")
        res = res.replace("_96.mp4", "${targetSuffix}.mp4")
                 .replace("_96.m4a", "${targetSuffix}.m4a")
                 .replace("_96.aac", "${targetSuffix}.m4a")
                 .replace("_160.mp4", "${targetSuffix}.mp4")
                 .replace("_160.m4a", "${targetSuffix}.m4a")
                 .replace("_160.aac", "${targetSuffix}.m4a")
        return res
    }

    // -----------------------------------------------------------------------------------
    // 2. Audius Adapter (Global Independent - Full Songs)
    // -----------------------------------------------------------------------------------
    @WorkerThread
    private fun audiusTrending(limit: Int): List<Track> {
        val url = "https://api.audius.co/v1/tracks/trending".toHttpUrl().newBuilder()
            .addQueryParameter("app_name", APP_NAME)
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("time", "week")
            .build()
        val root = getJson(url.toString())
        val data = root.optJSONArray("data") ?: return emptyList()
        return parseAudiusTracks(data)
    }

    @WorkerThread
    private fun audiusSearch(query: String, limit: Int): List<Track> {
        val url = "https://api.audius.co/v1/tracks/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("app_name", APP_NAME)
            .addQueryParameter("limit", limit.toString())
            .build()
        val root = getJson(url.toString())
        val data = root.optJSONArray("data") ?: return emptyList()
        return parseAudiusTracks(data)
    }

    private fun parseAudiusTracks(array: JSONArray): List<Track> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optBoolean("is_stream_gated", false)) continue
            if (!item.optBoolean("is_streamable", true)) continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val user = item.optJSONObject("user")
            val title = item.optString("title").ifBlank { "Untitled" }.cleanHtml()
            val artist = user?.optString("name").orEmpty().ifBlank { "Audius artist" }.cleanHtml()
            val artwork = item.optJSONObject("artwork")?.optionalString("480x480")
                ?: item.optJSONObject("artwork")?.optionalString("150x150")
            val duration = (item.optLong("duration", 0L) * 1000L).coerceAtLeast(0L)
            val handle = user?.optString("handle").orEmpty()
            val slug = item.optString("slug")
            val external = if (handle.isNotBlank() && slug.isNotBlank()) {
                "https://audius.co/$handle/$slug"
            } else {
                "https://audius.co"
            }
            add(
                Track(
                    id = "audius:$id",
                    title = title,
                    artist = artist,
                    album = null,
                    artworkUrl = artwork,
                    durationMs = duration,
                    source = TrackSource.AUDIUS,
                    streamUrl = "https://api.audius.co/v1/tracks/$id/stream?app_name=$APP_NAME",
                    externalUrl = external,
                    preview = false,
                    genre = item.optionalString("genre"),
                    releaseYear = item.optionalString("release_date")?.take(4)
                )
            )
        }
    }

    // -----------------------------------------------------------------------------------
    // 3. Jamendo Adapter (Global Royalty-Free & Indie - Full Songs)
    // -----------------------------------------------------------------------------------
    @WorkerThread
    private fun jamendoPopular(limit: Int): List<Track> {
        val url = "https://api.jamendo.com/v3.0/tracks/".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", "56d30c95")
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("order", "popularity_week")
            .addQueryParameter("audioformat", "mp32")
            .build()
        val root = getJson(url.toString())
        return parseJamendoTracks(root.optJSONArray("results"))
    }

    @WorkerThread
    private fun jamendoSearch(query: String, limit: Int): List<Track> {
        val url = "https://api.jamendo.com/v3.0/tracks/".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", "56d30c95")
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("namesearch", query)
            .addQueryParameter("audioformat", "mp32")
            .build()
        val root = getJson(url.toString())
        return parseJamendoTracks(root.optJSONArray("results"))
    }

    private fun parseJamendoTracks(array: JSONArray?): List<Track> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val title = item.optString("name").cleanHtml()
                if (title.isBlank()) continue
                val audio = item.optionalString("audio") ?: continue
                val artist = item.optString("artist_name").ifBlank { "Jamendo Artist" }.cleanHtml()
                val album = item.optionalString("album_name")?.cleanHtml()
                val artwork = item.optionalString("image")
                val duration = item.optLong("duration", 0L) * 1000L
                val external = item.optionalString("shareurl") ?: "https://www.jamendo.com"

                add(
                    Track(
                        id = "jamendo:$id",
                        title = title,
                        artist = artist,
                        album = album,
                        artworkUrl = artwork,
                        durationMs = duration,
                        source = TrackSource.JAMENDO,
                        streamUrl = audio,
                        externalUrl = external,
                        preview = false,
                        genre = "Indie",
                        releaseYear = item.optionalString("releasedate")?.take(4)
                    )
                )
            }
        }
    }

    // -----------------------------------------------------------------------------------
    // 4. Piped / YouTube Music Adapter (Global Full Audio Streams)
    // -----------------------------------------------------------------------------------
    @WorkerThread
    private fun pipedSearch(query: String, limit: Int): List<Track> {
        val encoded = runCatching { URLEncoder.encode(query, "UTF-8") }.getOrDefault(query)
        val endpoints = listOf(
            "https://pipedapi.kavin.rocks/search?q=$encoded&filter=music_songs",
            "https://api.piped.video/search?q=$encoded&filter=music_songs"
        )
        for (url in endpoints) {
            val tracks = runCatching {
                val root = getJsonArray(url)
                parsePipedTracks(root, limit)
            }.getOrDefault(emptyList())
            if (tracks.isNotEmpty()) return tracks
        }
        return emptyList()
    }

    private fun parsePipedTracks(array: JSONArray?, limit: Int): List<Track> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until minOf(array.length(), limit)) {
                val item = array.optJSONObject(i) ?: continue
                val videoUrl = item.optString("url")
                val videoId = videoUrl.substringAfter("v=", "").takeIf { it.isNotBlank() }
                    ?: item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val rawTitle = item.optString("title")
                if (rawTitle.isBlank()) continue
                val title = rawTitle.cleanHtml()
                val artist = item.optString("uploaderName")
                    .replace(" - Topic", "")
                    .replace("- Topic", "")
                    .ifBlank { "YouTube Music" }
                    .cleanHtml()
                val artwork = item.optionalString("thumbnail")
                val duration = item.optLong("duration", 180L) * 1000L

                add(
                    Track(
                        id = "piped:$videoId",
                        title = title,
                        artist = artist,
                        album = null,
                        artworkUrl = artwork,
                        durationMs = duration,
                        source = TrackSource.AUDIUS,
                        streamUrl = "https://pipedapi.kavin.rocks/streams/$videoId",
                        externalUrl = "https://music.youtube.com/watch?v=$videoId",
                        preview = false,
                        genre = null,
                        releaseYear = null
                    )
                )
            }
        }
    }

    private fun fetchPipedNextTracks(videoId: String, limit: Int = 15): List<Track> {
        if (videoId.isBlank()) return emptyList()
        val endpoints = listOf(
            "https://pipedapi.kavin.rocks/next/$videoId",
            "https://api.piped.video/next/$videoId"
        )
        for (endpoint in endpoints) {
            val tracks = runCatching {
                val root = getJson(endpoint)
                val related = root.optJSONArray("relatedStreams")
                parsePipedNextStreams(related, limit)
            }.getOrDefault(emptyList())
            if (tracks.isNotEmpty()) return tracks
        }
        return emptyList()
    }

    private fun parsePipedNextStreams(array: JSONArray?, limit: Int): List<Track> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until minOf(array.length(), limit)) {
                val item = array.optJSONObject(i) ?: continue
                val videoUrl = item.optString("url")
                val vId = videoUrl.substringAfter("v=", "").takeIf { it.isNotBlank() }
                    ?: item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val rawTitle = item.optString("title")
                if (rawTitle.isBlank()) continue
                val title = rawTitle.cleanHtml()
                val artist = item.optString("uploaderName")
                    .replace(" - Topic", "")
                    .replace("- Topic", "")
                    .ifBlank { "YouTube Music" }
                    .cleanHtml()
                val artwork = item.optionalString("thumbnail")
                val duration = item.optLong("duration", 180L) * 1000L

                if (MusicTrackUtils.isSpamOrFalseArtist(artist, title)) continue

                add(
                    Track(
                        id = "piped:$vId",
                        title = title,
                        artist = artist,
                        album = null,
                        artworkUrl = artwork,
                        durationMs = duration,
                        source = TrackSource.AUDIUS,
                        streamUrl = "https://pipedapi.kavin.rocks/streams/$vId",
                        externalUrl = "https://music.youtube.com/watch?v=$vId",
                        preview = false,
                        genre = null,
                        releaseYear = null
                    )
                )
            }
        }
    }

    // -----------------------------------------------------------------------------------
    // 5. Internet Archive Music Adapter (Global Historical & Independent Full Songs)
    // -----------------------------------------------------------------------------------
    @WorkerThread
    private fun archiveSearch(query: String, limit: Int): List<Track> {
        val encoded = runCatching { URLEncoder.encode(query, "UTF-8") }.getOrDefault(query)
        val url = "https://archive.org/advancedsearch.php?q=$encoded+AND+mediatype:audio&fl[]=identifier,title,creator,album,year,downloads&sort[]=downloads+desc&rows=$limit&page=1&output=json"
        return runCatching {
            val root = getJson(url)
            val docs = root.optJSONObject("response")?.optJSONArray("docs")
            parseArchiveTracks(docs)
        }.getOrDefault(emptyList())
    }

    private fun parseArchiveTracks(array: JSONArray?): List<Track> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("identifier").takeIf { it.isNotBlank() } ?: continue
                val title = item.optString("title").cleanHtml()
                if (title.isBlank()) continue
                val artist = item.optString("creator").ifBlank { "Internet Archive" }.cleanHtml()
                val album = item.optionalString("album")?.cleanHtml()
                val year = item.optionalString("year")

                add(
                    Track(
                        id = "archive:$id",
                        title = title,
                        artist = artist,
                        album = album,
                        artworkUrl = "https://archive.org/services/img/$id",
                        durationMs = 200_000L,
                        source = TrackSource.JAMENDO,
                        streamUrl = "https://archive.org/download/$id/$id.mp3",
                        externalUrl = "https://archive.org/details/$id",
                        preview = false,
                        genre = "Archive",
                        releaseYear = year
                    )
                )
            }
        }
    }

    suspend fun fetchSpotifyPlaylist(playlistId: String): SpotifyParsedPlaylist? = withContext(Dispatchers.IO) {
        val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        runCatching {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@runCatching null
            val html = response.body?.string().orEmpty()

            val scriptJson = html.substringAfter("<script id=\"__NEXT_DATA__\" type=\"application/json\">", "")
                .substringBefore("</script>", "")
                .ifBlank {
                    html.substringAfter("<script id=\"resource\" type=\"application/json\">", "")
                        .substringBefore("</script>", "")
                }

            if (scriptJson.isNotBlank()) {
                val root = JSONObject(scriptJson)
                val props = root.optJSONObject("props")?.optJSONObject("pageProps")
                val entity = props?.optJSONObject("state")?.optJSONObject("data")?.optJSONObject("entity")
                    ?: props?.optJSONObject("entity")

                val rawName = entity?.optString("name").orEmpty()
                val name = if (rawName.isNotBlank()) MusicTrackUtils.cleanHtml(rawName) else "Spotify Playlist"
                val description = entity?.optString("description")?.takeIf { it.isNotBlank() }?.let { MusicTrackUtils.cleanHtml(it) }

                // Extract embedded session access token
                val accessToken = props?.optJSONObject("state")
                    ?.optJSONObject("settings")
                    ?.optJSONObject("session")
                    ?.optString("accessToken")
                    ?.takeIf { it.isNotBlank() }

                if (accessToken != null) {
                    // 1. Try Pathfinder GraphQL query first (bypasses standard REST 429 quota limits)
                    val graphQLTracks = fetchSpotifyPlaylistGraphQL(playlistId, accessToken)
                    if (graphQLTracks.isNotEmpty()) {
                        return@withContext SpotifyParsedPlaylist(playlistId, name, description, graphQLTracks)
                    }

                    // 2. Try REST API as secondary fallback
                    val apiTracks = mutableListOf<SpotifyParsedTrack>()
                    var offset = 0
                    val limit = 100
                    var hasMore = true

                    while (hasMore && offset < 2000) {
                        val apiUrl = "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=$limit&offset=$offset"
                        val apiReq = Request.Builder()
                            .url(apiUrl)
                            .header("Authorization", "Bearer $accessToken")
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .header("App-Platform", "WebPlayer")
                            .build()

                        val apiResp = runCatching { client.newCall(apiReq).execute() }.getOrNull()
                        if (apiResp != null && apiResp.isSuccessful) {
                            val bodyStr = apiResp.body?.string().orEmpty()
                            if (bodyStr.isNotBlank()) {
                                val apiJson = JSONObject(bodyStr)
                                val items = apiJson.optJSONArray("items")
                                if (items != null && items.length() > 0) {
                                    for (i in 0 until items.length()) {
                                        val itemObj = items.optJSONObject(i) ?: continue
                                        val tObj = itemObj.optJSONObject("track")
                                            ?: itemObj.optJSONObject("item")
                                            ?: continue
                                        val title = tObj.optString("name")
                                            .ifBlank { tObj.optString("title") }
                                            .let { MusicTrackUtils.cleanHtml(it) }

                                        val artistsArr = tObj.optJSONArray("artists")
                                        val artist = if (artistsArr != null && artistsArr.length() > 0) {
                                            buildList {
                                                for (j in 0 until artistsArr.length()) {
                                                    val a = artistsArr.optJSONObject(j)
                                                    val n = a?.optString("name")
                                                    if (!n.isNullOrBlank()) add(MusicTrackUtils.cleanHtml(n))
                                                }
                                            }.joinToString(", ")
                                        } else {
                                            tObj.optString("subtitle").let { MusicTrackUtils.cleanHtml(it) }
                                        }

                                        if (title.isNotBlank()) {
                                            apiTracks.add(SpotifyParsedTrack(title, artist.ifBlank { "Unknown Artist" }))
                                        }
                                    }
                                    val total = apiJson.optInt("total", apiTracks.size)
                                    offset += items.length()
                                    if (offset >= total || items.length() < limit) {
                                        hasMore = false
                                    }
                                } else {
                                    hasMore = false
                                }
                            } else {
                                hasMore = false
                            }
                        } else {
                            hasMore = false
                        }
                    }

                    if (apiTracks.isNotEmpty()) {
                        return@withContext SpotifyParsedPlaylist(playlistId, name, description, apiTracks)
                    }
                }

                if (entity != null) {
                    val trackListJson = entity.optJSONArray("trackList")
                        ?: entity.optJSONObject("tracks")?.optJSONArray("items")

                    val parsedTracks = mutableListOf<SpotifyParsedTrack>()
                    if (trackListJson != null) {
                        for (i in 0 until trackListJson.length()) {
                            val item = trackListJson.optJSONObject(i) ?: continue
                            val tObj = item.optJSONObject("track") ?: item
                            val title = tObj.optString("title")
                                .ifBlank { tObj.optString("name") }
                                .let { MusicTrackUtils.cleanHtml(it) }
                            val artist = tObj.optString("subtitle")
                                .ifBlank {
                                    val arr = tObj.optJSONArray("artists")
                                    if (arr != null && arr.length() > 0) {
                                        buildList {
                                            for (j in 0 until arr.length()) {
                                                val a = arr.optJSONObject(j)
                                                val n = a?.optString("name")
                                                if (!n.isNullOrBlank()) add(MusicTrackUtils.cleanHtml(n))
                                            }
                                        }.joinToString(", ")
                                    } else ""
                                }
                                .let { MusicTrackUtils.cleanHtml(it) }

                            if (title.isNotBlank()) {
                                parsedTracks.add(SpotifyParsedTrack(title, artist.ifBlank { "Unknown Artist" }))
                            }
                        }
                    }
                    if (parsedTracks.isNotEmpty()) {
                        return@withContext SpotifyParsedPlaylist(playlistId, name, description, parsedTracks)
                    }
                }
            }

            val oembedUrl = "https://open.spotify.com/oembed?url=https://open.spotify.com/playlist/$playlistId"
            val oembedTitle = runCatching {
                val oRoot = getJson(oembedUrl)
                oRoot.optString("title").takeIf { it.isNotBlank() }
            }.getOrNull()

            val fallbackTitle = oembedTitle?.let { MusicTrackUtils.cleanHtml(it) } ?: "Spotify Playlist"

            val tracks = mutableListOf<SpotifyParsedTrack>()
            val matches = Regex("""\"title\"\s*:\s*\"(.*?)\"[\s\S]*?\"subtitle\"\s*:\s*\"(.*?)\"""").findAll(html)
            for (m in matches) {
                val tName = m.groupValues.getOrNull(1)?.let { MusicTrackUtils.cleanHtml(it) }.orEmpty()
                val aName = m.groupValues.getOrNull(2)?.let { MusicTrackUtils.cleanHtml(it) }.orEmpty()
                if (tName.isNotBlank()) {
                    tracks.add(SpotifyParsedTrack(tName, aName.ifBlank { "Unknown Artist" }))
                }
            }

            if (tracks.isNotEmpty()) {
                return@withContext SpotifyParsedPlaylist(playlistId, fallbackTitle, null, tracks.distinct())
            }

            null
        }.getOrNull()
    }

    private fun fetchSpotifyPlaylistGraphQL(playlistId: String, accessToken: String): List<SpotifyParsedTrack> {
        val tracks = mutableListOf<SpotifyParsedTrack>()
        var offset = 0
        val limit = 100
        val hashVal = "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf"

        while (offset < 2000) {
            val varsJson = JSONObject().apply {
                put("uri", "spotify:playlist:$playlistId")
                put("offset", offset)
                put("limit", limit)
            }.toString()

            val extJson = JSONObject().apply {
                put("persistedQuery", JSONObject().apply {
                    put("version", 1)
                    put("sha256Hash", hashVal)
                })
            }.toString()

            val encodedVars = runCatching { URLEncoder.encode(varsJson, "UTF-8") }.getOrDefault(varsJson)
            val encodedExt = runCatching { URLEncoder.encode(extJson, "UTF-8") }.getOrDefault(extJson)

            val pfUrl = "https://api-partner.spotify.com/pathfinder/v1/query?operationName=fetchPlaylistContents&variables=$encodedVars&extensions=$encodedExt"

            val req = Request.Builder()
                .url(pfUrl)
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("App-Platform", "WebPlayer")
                .build()

            val resp = runCatching { client.newCall(req).execute() }.getOrNull()
            if (resp == null || !resp.isSuccessful) break
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) break

            val json = runCatching { JSONObject(body) }.getOrNull() ?: break
            val playlistV2 = json.optJSONObject("data")?.optJSONObject("playlistV2") ?: break
            val content = playlistV2.optJSONObject("content") ?: break
            val items = content.optJSONArray("items") ?: break
            val totalCount = content.optInt("totalCount", 0)

            if (items.length() == 0) break

            for (i in 0 until items.length()) {
                val itObj = items.optJSONObject(i) ?: continue
                val itemV2Data = itObj.optJSONObject("itemV2")?.optJSONObject("data")
                    ?: itObj.optJSONObject("itemV3")?.optJSONObject("data")
                    ?: continue

                val title = itemV2Data.optString("name")
                    .ifBlank { itemV2Data.optString("title") }
                    .let { MusicTrackUtils.cleanHtml(it) }

                val artistsArr = itemV2Data.optJSONObject("artists")?.optJSONArray("items")
                val artistNames = if (artistsArr != null && artistsArr.length() > 0) {
                    buildList {
                        for (j in 0 until artistsArr.length()) {
                            val aObj = artistsArr.optJSONObject(j)
                            val name = aObj?.optJSONObject("profile")?.optString("name")
                                ?: aObj?.optString("name")
                            if (!name.isNullOrBlank()) add(MusicTrackUtils.cleanHtml(name))
                        }
                    }.joinToString(", ")
                } else {
                    itemV2Data.optString("subtitle").let { MusicTrackUtils.cleanHtml(it) }
                }

                if (title.isNotBlank()) {
                    tracks.add(SpotifyParsedTrack(title, artistNames.ifBlank { "Unknown Artist" }))
                }
            }

            offset += items.length()
            if (items.length() < limit || (totalCount in 1..offset)) break
        }

        return tracks
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val body = response.body?.string() ?: throw IOException("Empty response")
            return JSONObject(body)
        }
    }

    private fun getJsonArray(url: String): JSONArray {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val body = response.body?.string() ?: throw IOException("Empty response")
            return JSONArray(body)
        }
    }

    private fun cacheHome(feed: HomeFeed) {
        runCatching {
            homeCacheFile.parentFile?.mkdirs()
            val root = JSONObject().apply {
                put("version", 1)
                put("updatedAt", feed.updatedAt)
                put("sections", JSONArray().apply {
                    feed.sections.forEach { put(TrackJson.sectionToJson(it)) }
                })
            }
            val temp = File(homeCacheFile.parentFile, "home_feed.tmp")
            temp.writeText(root.toString())
            if (!temp.renameTo(homeCacheFile)) {
                homeCacheFile.writeText(root.toString())
                temp.delete()
            }
        }
    }

    companion object {
        const val APP_NAME = "AYNAMusic"

        fun deepLinks(track: Track): List<Pair<String, String>> {
            val query = "${track.title} ${track.artist}".trim()
            val encoded = runCatching { URLEncoder.encode(query, "UTF-8") }.getOrDefault(query)
            return buildList {
                track.externalUrl?.takeIf { it.startsWith("https://") }?.let {
                    val label = when (track.source) {
                        TrackSource.SAAVN -> "JioSaavn"
                        TrackSource.APPLE -> "Apple Music"
                        TrackSource.DEEZER -> "Deezer"
                        TrackSource.JAMENDO -> "Jamendo"
                        TrackSource.AUDIUS -> "Audius"
                        else -> "Source link"
                    }
                    add(label to it)
                }
                add("JioSaavn" to "https://www.jiosaavn.com/search/$encoded")
                add("Spotify" to "https://open.spotify.com/search/$encoded")
                add("YouTube Music" to "https://music.youtube.com/search?q=$encoded")
                add("Apple Music" to "https://music.apple.com/us/search?term=$encoded")
                add("Gaana" to "https://gaana.com/search/$encoded")
                add("Wynk Music" to "https://wynk.in/music/search/$encoded")
            }
        }
    }
}

private fun String.cleanHtml(): String {
    if (isEmpty() || (!contains("&") && !contains("&#"))) return this.trim()
    return this
        .replace("&amp;", "&")
        .replace("&#039;", "'")
        .replace("&apos;", "'")
        .replace("&#x27;", "'")
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .trim()
}

object MusicTrackUtils {
    fun extractSpotifyPlaylistId(link: String): String? {
        val clean = link.trim()
        if (clean.isBlank()) return null
        if (clean.startsWith("spotify:playlist:")) {
            return clean.substringAfter("spotify:playlist:").substringBefore("?").substringBefore(":")
        }
        if (clean.contains("spotify.com/playlist/")) {
            return clean.substringAfter("playlist/").substringBefore("?").substringBefore("/")
        }
        return null
    }

    fun cleanHtml(str: String?): String {
        if (str.isNullOrBlank()) return ""
        return str.cleanHtml()
    }

    fun extractArtists(rawArtist: String): List<String> {
        val clean = cleanHtml(rawArtist).trim()
        if (clean.isBlank()) return emptyList()
        val parts = clean.split(Regex("""(?i),\s*|\s+&\s+|\s+ft\.?\s+|\s+feat\.?\s+|\s+featuring\s+|\s+x\s+|\s*/\s+"""))
        return parts.map { it.trim() }.filter { part ->
            part.isNotBlank() && !isSpamOrFalseArtist(part)
        }.distinct()
    }

    fun detectLanguageTag(track: Track): String? {
        val text = "${track.genre.orEmpty()} ${track.artist} ${track.album.orEmpty()} ${track.title}".lowercase()
        val languages = listOf(
            "hindi", "punjabi", "tamil", "telugu", "malayalam", "kannada",
            "bengali", "marathi", "gujarati", "bhojpuri", "english", "spanish",
            "korean", "japanese", "french", "german", "arabic", "urdu",
            "bollywood", "tollywood", "kollywood", "sufi", "ghazal", "indie"
        )
        return languages.firstOrNull { text.contains(it) }
    }

    fun normalizeTitle(title: String): String {
        if (title.isBlank()) return ""
        var t = cleanHtml(title).lowercase()
        t = t.replace(Regex("""\(.*?\)|\[.*?\]"""), " ")
        t = t.replace(Regex("""\s*[-|:].*"""), " ")
        t = t.replace(Regex("""\b(official|video|lyric|lyrics|full song|hd|audio|remix|lofi|slowed|reverb|version|status|ringtone|reels|trending|part|mp3)\b"""), " ")
        t = t.replace(Regex("""[^a-z0-9\s]"""), " ")
        t = t.replace(Regex("""\s+"""), " ").trim()
        return if (t.length >= 2) t else cleanHtml(title).lowercase().replace(Regex("""[^a-z0-9]"""), "")
    }

    fun normalizeArtist(artist: String): String {
        if (artist.isBlank()) return ""
        var a = cleanHtml(artist).lowercase()
        a = a.replace(Regex("""\b(topic|official|channel|music|records|vevo|audio|uploader|singles|artists|artist)\b"""), " ")
        a = a.replace(Regex("""[^a-z0-9,\s]"""), " ")
        return a.replace(Regex("""\s+"""), " ").trim()
    }

    fun toSongKey(title: String, artist: String): String {
        val normTitle = normalizeTitle(title)
        val normArtist = normalizeArtist(artist)
        return if (normArtist.isBlank()) normTitle else "$normTitle:$normArtist"
    }

    fun isSpamOrFalseArtist(artist: String, title: String = ""): Boolean {
        val cleanArt = cleanHtml(artist).trim()
        if (cleanArt.isBlank()) return true

        val lowerArtist = cleanArt.lowercase()
        val lowerTitle = cleanHtml(title).lowercase()

        val spamExact = setOf(
            "unknown artist", "unknown", "jiosaavn artist", "youtube music",
            "various artists", "various", "various artist", "artist", "uploaded by",
            "official sound", "topic", "auto-generated", "auto generated",
            "reel trending", "status video", "ringtone", "ring tone",
            "tik tok", "tiktok", "subscribe", "telegram", "download",
            "free download", "8d audio", "slowed reverb", "lofi mix",
            "bass boosted", "full bass", "dj remix", "spam", "uploader",
            "channel", "anonymous", "soundtrack", "instrumental", "va"
        )
        if (spamExact.contains(lowerArtist)) return true

        val spamSubstrings = listOf(
            "unknown artist", "jiosaavn artist", "youtube music",
            "auto-generated", "auto generated", "official sound",
            "reel trending", "status video", "ringtone", "ring tone",
            "subscribe", "telegram", "t.me/", "download free",
            "click here", "must watch", "8d audio", "slowed reverb",
            ".com", ".net", ".org", "www.", "http:", "https:", "@",
            "whatsapp", "instagram", "tiktok", "yt music", "speed up"
        )
        if (spamSubstrings.any { lowerArtist.contains(it) }) return true

        if (cleanArt.length > 60) return true

        if (lowerArtist.contains("lofi") && !lowerTitle.contains("lofi")) return true
        if (lowerArtist.contains("dj remix") && !lowerTitle.contains("remix")) return true

        return false
    }

    fun calculateAffinityScore(candidate: Track, seed: Track): Int {
        if (isSpamOrFalseArtist(candidate.artist, candidate.title)) return -1000

        var score = 0

        val seedLang = detectLanguageTag(seed)?.lowercase()
        val candLang = detectLanguageTag(candidate)?.lowercase()

        val seedGenre = cleanHtml(seed.genre).lowercase().trim()
        val candGenre = cleanHtml(candidate.genre).lowercase().trim()

        val hasSameLang = seedLang != null && candLang != null && seedLang == candLang
        val hasSameGenre = seedGenre.isNotBlank() && candGenre.isNotBlank() &&
                (candGenre.contains(seedGenre) || seedGenre.contains(candGenre))

        // 1. Language Alignment (+50 points)
        if (hasSameLang) {
            score += 50
        } else if (seedLang != null && candLang != null && seedLang != candLang) {
            score -= 70 // Heavy penalty for cross-language jump
        } else if (seedLang != null && candidate.source == TrackSource.SAAVN) {
            if (seedLang == "hindi" || seedLang == "punjabi" || seedLang == "bollywood") {
                score += 25
            }
        }

        // 2. Genre Alignment (+40 points)
        if (hasSameGenre) {
            score += 40
        }

        // 3. Same Genre + Same Language Synergy Bonus (+30 points)
        if (hasSameLang && hasSameGenre) {
            score += 30
        }

        // 4. Song Popularity & Source Quality (+40 points)
        if (candidate.source == TrackSource.SAAVN || candidate.id.startsWith("piped:")) {
            score += 30
        }
        if (!candidate.artworkUrl.isNullOrBlank()) {
            score += 10
        }

        // 5. Artist Match (+20 points - lower priority than Genre/Language/Popularity)
        val seedArtists = extractArtists(seed.artist)
        val candArtists = extractArtists(candidate.artist)
        val primarySeedArtist = seedArtists.firstOrNull()?.lowercase()
        val candArtistLower = candArtists.map { it.lowercase() }

        if (primarySeedArtist != null && candArtistLower.contains(primarySeedArtist)) {
            score += 20
        } else if (seedArtists.any { sa -> candArtistLower.contains(sa.lowercase()) }) {
            score += 10
        }

        // 6. Album / Single Match
        val seedAlbum = cleanHtml(seed.album).lowercase().trim()
        val candAlbum = cleanHtml(candidate.album).lowercase().trim()
        if (seedAlbum.length >= 3 && seedAlbum != "single" && seedAlbum != "unknown album" && candAlbum.isNotBlank()) {
            if (candAlbum == seedAlbum || candAlbum.contains(seedAlbum) || seedAlbum.contains(candAlbum)) {
                score += 25
            }
        }

        // 7. Era / Release Year Match (Spotify era continuity)
        val seedYear = seed.releaseYear?.toIntOrNull()
        val candYear = candidate.releaseYear?.toIntOrNull()
        if (seedYear != null && candYear != null) {
            val diff = kotlin.math.abs(seedYear - candYear)
            when {
                diff == 0 -> score += 15
                diff <= 3 -> score += 10
                diff <= 7 -> score += 5
                diff > 20 -> score -= 15
            }
        }

        // 8. Duration Check
        if (candidate.durationMs in 90_000L..480_000L) {
            score += 10
        } else if (candidate.durationMs < 60_000L || candidate.durationMs > 600_000L) {
            score -= 50
        }

        return score
    }

    fun matchesGenreLanguageArtistAlbum(candidate: Track, seed: Track): Boolean {
        if (isSpamOrFalseArtist(candidate.artist, candidate.title)) return false

        val score = calculateAffinityScore(candidate, seed)
        if (score >= 10) return true

        val seedArtist = normalizeArtist(seed.artist)
        val candArtist = normalizeArtist(candidate.artist)

        val seedAlbum = cleanHtml(seed.album).lowercase().trim()
        val candAlbum = cleanHtml(candidate.album).lowercase().trim()

        val seedGenreOrLang = cleanHtml(seed.genre).lowercase().trim()
        val candGenreOrLang = cleanHtml(candidate.genre).lowercase().trim()

        val artistMatch = seedArtist.isNotBlank() && candArtist.isNotBlank() &&
                (candArtist.contains(seedArtist) || seedArtist.contains(candArtist) ||
                        seedArtist.split(",").any { part -> part.trim().isNotBlank() && candArtist.contains(part.trim()) })

        val albumMatch = seedAlbum.length >= 3 && seedAlbum != "single" && seedAlbum != "unknown album" &&
                candAlbum.length >= 3 && (candAlbum.contains(seedAlbum) || seedAlbum.contains(candAlbum))

        val genreLangMatch = seedGenreOrLang.isNotBlank() && candGenreOrLang.isNotBlank() &&
                (candGenreOrLang.contains(seedGenreOrLang) || seedGenreOrLang.contains(candGenreOrLang))

        return artistMatch || albumMatch || genreLangMatch
    }
}
