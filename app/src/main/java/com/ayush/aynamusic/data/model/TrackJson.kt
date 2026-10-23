package com.ayush.aynamusic.data.model

import org.json.JSONArray
import org.json.JSONObject

object TrackJson {
    fun toJson(track: Track): JSONObject = JSONObject().apply {
        put("id", track.id)
        put("title", track.title)
        put("artist", track.artist)
        put("album", track.album ?: JSONObject.NULL)
        put("artworkUrl", track.artworkUrl ?: JSONObject.NULL)
        put("durationMs", track.durationMs)
        put("source", track.source.name)
        put("streamUrl", track.streamUrl)
        put("externalUrl", track.externalUrl ?: JSONObject.NULL)
        put("preview", track.preview)
        put("genre", track.genre ?: JSONObject.NULL)
        put("releaseYear", track.releaseYear ?: JSONObject.NULL)
        put("isFavorite", track.isFavorite)
        put("downloadState", track.downloadState.name)
        put("localFilePath", track.localFilePath ?: JSONObject.NULL)
    }

    fun fromJson(json: JSONObject): Track = Track(
        id = json.getString("id"),
        title = json.optString("title", "Unknown title"),
        artist = json.optString("artist", "Unknown artist"),
        album = json.optionalString("album"),
        artworkUrl = json.optionalString("artworkUrl"),
        durationMs = json.optLong("durationMs", 0L),
        source = runCatching { TrackSource.valueOf(json.optString("source", "AUDIUS")) }
            .getOrDefault(TrackSource.AUDIUS),
        streamUrl = json.optString("streamUrl"),
        externalUrl = json.optionalString("externalUrl"),
        preview = json.optBoolean("preview", false),
        genre = json.optionalString("genre"),
        releaseYear = json.optionalString("releaseYear"),
        isFavorite = json.optBoolean("isFavorite", false),
        downloadState = runCatching { DownloadState.valueOf(json.optString("downloadState", "NONE")) }
            .getOrDefault(DownloadState.NONE),
        localFilePath = json.optionalString("localFilePath")
    )

    fun listToJson(tracks: List<Track>): JSONArray = JSONArray().apply {
        tracks.forEach { put(toJson(it)) }
    }

    fun listFromJson(array: JSONArray?): List<Track> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                runCatching { TrackJson.fromJson(array.getJSONObject(i)) }.getOrNull()?.let(::add)
            }
        }
    }

    fun sectionToJson(section: CatalogSection): JSONObject = JSONObject().apply {
        put("id", section.id)
        put("title", section.title)
        put("subtitle", section.subtitle)
        put("tracks", listToJson(section.tracks))
    }

    fun fromSectionJson(json: JSONObject): CatalogSection = CatalogSection(
        id = json.getString("id"),
        title = json.getString("title"),
        subtitle = json.optString("subtitle"),
        tracks = listFromJson(json.optJSONArray("tracks"))
    )
}

internal fun JSONObject.optionalString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() && it != "null" }
}
