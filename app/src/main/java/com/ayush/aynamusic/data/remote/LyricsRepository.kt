package com.ayush.aynamusic.data.remote

import android.content.Context
import com.ayush.aynamusic.data.local.LyricsDao
import com.ayush.aynamusic.data.local.LyricsEntity
import com.ayush.aynamusic.data.model.Lyrics
import com.ayush.aynamusic.data.model.Track
import com.ayush.aynamusic.util.parseLrc
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * LRCLIB is a free, public lyrics database with time-synced lyrics.
 * Results are cached locally so a downloaded song's lyrics work offline after first viewing.
 */
class LyricsRepository(
    context: Context,
    private val dao: LyricsDao
) {
    private val client = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http_lyrics"), 4L * 1024 * 1024))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "AYNAMusic/1.0 (lyrics lookup)")
                    .build()
            )
        }
        .build()

    suspend fun lyricsFor(track: Track, allowNetwork: Boolean = true): Lyrics? =
        withContext(Dispatchers.IO) {
            val cached = dao.get(track.id)
            if (cached != null) return@withContext cached.toLyrics()
            if (!allowNetwork) return@withContext null

            val title = cleanTitle(track.title)
            val artist = track.artist.substringBefore(',').substringBefore(" & ").trim()
            val duration = (track.durationMs / 1000L).coerceAtLeast(1L)
            val result = runCatching {
                lookupExact(title, artist, track.album, duration)
                    ?: lookupSearch(title, artist)
            }.getOrNull()

            if (result == null) {
                dao.upsert(
                    LyricsEntity(
                        trackId = track.id,
                        syncedLyrics = null,
                        plainLyrics = null,
                        instrumental = false,
                        fetchedAt = System.currentTimeMillis()
                    )
                )
                return@withContext null
            }
            dao.upsert(result.copy(trackId = track.id))
            result.toLyrics()
        }

    private fun lookupExact(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Long
    ): LyricsEntity? {
        val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .addQueryParameter("duration", durationSeconds.toString())
            .apply { album?.takeIf { it.isNotBlank() }?.let { addQueryParameter("album_name", it) } }
            .build()
        val response = newCall(url.toString()) ?: return null
        return parseLyricsJson(response)
    }

    private fun lookupSearch(title: String, artist: String): LyricsEntity? {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .build()
        val body = newCall(url.toString()) ?: return null
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return null
        val candidates = buildList {
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let(::add)
            }
        }
        val withSynced = candidates.firstOrNull {
            !it.optString("syncedLyrics").isNullOrBlank() && !it.optBoolean("instrumental")
        }
        val chosen = withSynced
            ?: candidates.firstOrNull { !it.optString("plainLyrics").isNullOrBlank() }
            ?: return null
        return parseLyricsObject(chosen)
    }

    private fun newCall(url: String): String? {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("Lyrics HTTP ${response.code}")
            return response.body?.string()
        }
    }

    private fun parseLyricsJson(raw: String): LyricsEntity? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return parseLyricsObject(json)
    }

    private fun parseLyricsObject(json: JSONObject): LyricsEntity? {
        val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        val instrumental = json.optBoolean("instrumental", false)
        if (synced == null && plain == null && !instrumental) return null
        return LyricsEntity(
            trackId = "",
            syncedLyrics = synced,
            plainLyrics = plain,
            instrumental = instrumental,
            fetchedAt = System.currentTimeMillis()
        )
    }

    private fun cleanTitle(raw: String): String = raw
        .replace(Regex("\\([^)]*(feat|ft|with|remix|version)[^)]*\\)", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\[[^]]*]"), "")
        .substringBefore(" - From ")
        .substringBefore(" (From ")
        .trim()

    private fun LyricsEntity.toLyrics(): Lyrics = Lyrics(
        synced = syncedLyrics?.let(::parseLrc).orEmpty(),
        plain = plainLyrics,
        instrumental = instrumental
    )
}
