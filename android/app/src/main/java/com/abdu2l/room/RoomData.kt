package com.abdu2l.room

import android.content.ContentResolver
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Locale

internal enum class TimeRange(val label: String, val lastFmPeriod: String, val seconds: Long?) {
    DAY("24 hours", "7day", 24 * 60 * 60L),
    WEEK("7 days", "7day", 7 * 24 * 60 * 60L),
    MONTH("1 month", "1month", 30 * 24 * 60 * 60L),
    YEAR("1 year", "12month", 365 * 24 * 60 * 60L),
    ALL("All time", "overall", null)
}

internal enum class RoomTab(val title: String) {
    OVERVIEW("Room"), TRACKS("Tracks"), ARTISTS("Artists"), GALAXY("Galaxy"), ACTIVITY("Activity")
}

internal data class ListeningTrack(
    val name: String,
    val artist: String,
    val plays: Int,
    val image: String = "",
    val url: String = "",
    val timestamp: Long? = null,
    val durationMs: Long = 0
)

internal data class ListeningArtist(
    val name: String,
    val plays: Int,
    val image: String = "",
    val tags: List<String> = emptyList()
)

internal data class PlayEntry(
    val track: String,
    val artist: String,
    val timestamp: Long,
    val durationMs: Long,
    val album: String = ""
)

internal data class RoomStats(
    val username: String,
    val totalScrobbles: Int,
    val tracks: List<ListeningTrack>,
    val artists: List<ListeningArtist>,
    val recent: List<PlayEntry>,
    val truncated: Boolean
)

internal data class TrackNote(val rating: Int = 0, val text: String = "")

internal object LastFmApi {
    fun load(username: String, apiKey: String, range: TimeRange): RoomStats {
        val nowSeconds = System.currentTimeMillis() / 1000
        val params = mutableMapOf("limit" to "200")
        range.seconds?.let { params["from"] = (nowSeconds - it).toString() }
        if (range == TimeRange.ALL) params["from"] = "1"
        params["to"] = nowSeconds.toString()
        val recentJson = request("user.getrecenttracks", username, apiKey, params)
        val topTracksJson = request("user.gettoptracks", username, apiKey, mapOf("period" to range.lastFmPeriod, "limit" to "50"))
        val topArtistsJson = request("user.gettopartists", username, apiKey, mapOf("period" to range.lastFmPeriod, "limit" to "50"))

        val recentObject = recentJson.optJSONObject("recenttracks") ?: JSONObject()
        val recentArray = asArray(recentObject.opt("track"))
        val recent = buildList {
            for (i in 0 until recentArray.length()) {
                val item = recentArray.optJSONObject(i) ?: continue
                val date = item.optJSONObject("date") ?: continue
                val timestamp = date.optString("uts").toLongOrNull()?.times(1000) ?: continue
                val artist = item.opt("artist").asTextOrNested("#text", "name")
                val track = item.optString("name").trim()
                if (track.isNotEmpty() && artist.isNotEmpty()) {
                    add(PlayEntry(track, artist, timestamp, 0, item.opt("album").asTextOrNested("#text")))
                }
            }
        }.distinctBy { "${it.timestamp}:${it.track.lowercase()}:${it.artist.lowercase()}" }.sortedByDescending { it.timestamp }

        val topTrackArray = asArray(topTracksJson.optJSONObject("toptracks")?.opt("track"))
        val topTracks = buildList {
            for (i in 0 until topTrackArray.length()) {
                val item = topTrackArray.optJSONObject(i) ?: continue
                val name = item.optString("name").trim()
                val artist = item.opt("artist").asTextOrNested("name", "#text")
                if (name.isNotEmpty() && artist.isNotEmpty()) {
                    add(ListeningTrack(name, artist, item.optString("playcount").toIntOrNull() ?: 0, image(item), item.optString("url")))
                }
            }
        }

        val topArtistArray = asArray(topArtistsJson.optJSONObject("topartists")?.opt("artist"))
        val topArtists = buildList {
            for (i in 0 until topArtistArray.length()) {
                val item = topArtistArray.optJSONObject(i) ?: continue
                val name = item.optString("name").trim()
                if (name.isNotEmpty()) add(ListeningArtist(name, item.optString("playcount").toIntOrNull() ?: 0, image(item)))
            }
        }
        val total = recentObject.optJSONObject("@attr")?.optString("total")?.toIntOrNull() ?: recent.size
        val dayRankings = recent.groupingBy { it.track.lowercase(Locale.ROOT) + "\u0000" + it.artist.lowercase(Locale.ROOT) }
            .eachCount().entries.sortedByDescending { it.value }.map { (key, count) ->
                val row = recent.first { it.track.lowercase(Locale.ROOT) + "\u0000" + it.artist.lowercase(Locale.ROOT) == key }
                ListeningTrack(row.track, row.artist, count)
            }
        val dayArtists = recent.groupingBy { it.artist.lowercase(Locale.ROOT) }.eachCount().entries.sortedByDescending { it.value }.map { (key, count) ->
            val row = recent.first { it.artist.lowercase(Locale.ROOT) == key }
            ListeningArtist(row.artist, count)
        }
        return RoomStats(
            username,
            total,
            if (range == TimeRange.DAY) dayRankings else topTracks,
            if (range == TimeRange.DAY) dayArtists else topArtists,
            recent,
            total > recent.size
        )
    }

    fun loadTags(username: String, apiKey: String, artists: List<ListeningArtist>): List<Pair<String, Int>> {
        val weights = mutableMapOf<String, Int>()
        artists.take(14).forEach { artist ->
            try {
                val payload = request("artist.getinfo", "", apiKey, mapOf("artist" to artist.name, "autocorrect" to "1"))
                val tags = payload.optJSONObject("artist")?.optJSONObject("tags")?.opt("tag")
                asArray(tags).let { list ->
                    for (i in 0 until minOf(list.length(), 5)) {
                        val name = list.optJSONObject(i)?.optString("name")?.trim()?.lowercase(Locale.ROOT).orEmpty()
                        if (name.isNotEmpty() && name !in JUNK_TAGS) weights[name] = (weights[name] ?: 0) + artist.plays.coerceAtLeast(1)
                    }
                }
            } catch (_: Exception) {
                // A missing tag should not prevent other genre results from loading.
            }
        }
        return weights.entries.sortedByDescending { it.value }.take(16).map { it.key to it.value }
    }

    private fun request(method: String, username: String, apiKey: String, extra: Map<String, String>): JSONObject {
        val query = linkedMapOf(
            "method" to method,
            "api_key" to apiKey,
            "format" to "json"
        ).apply {
            if (username.isNotBlank()) put("user", username)
            putAll(extra)
        }.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        val connection = (URL("https://ws.audioscrobbler.com/2.0/?$query").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 18_000
            setRequestProperty("User-Agent", "RoomAndroid/1.0 (https://abdu2l.github.io/Room)")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = try { JSONObject(body) } catch (_: Exception) {
                throw IllegalStateException("Last.fm returned an unreadable response. Check your connection and API key.")
            }
            if (json.has("error")) {
                val message = json.optString("message", "Last.fm could not load listening data.")
                throw IllegalStateException(when (json.optInt("error")) {
                    6 -> "Last.fm username not found. Check the spelling and try again."
                    10 -> "That Last.fm API key is not valid. Check the key in Settings."
                    29 -> "Last.fm is rate-limiting requests. Wait a moment, then retry."
                    else -> message
                })
            }
            if (code !in 200..299) throw IllegalStateException("Last.fm request failed ($code). Try again.")
            return json
        } finally {
            connection.disconnect()
        }
    }

    private fun image(item: JSONObject): String {
        val images = item.optJSONArray("image") ?: return ""
        for (size in listOf("extralarge", "large", "medium", "small")) {
            for (i in 0 until images.length()) {
                val entry = images.optJSONObject(i) ?: continue
                val value = entry.optString("#text")
                if (entry.optString("size") == size && value.startsWith("https://") && "2a96cbd8b46e442fc41c2b86b821562f" !in value) return value
            }
        }
        return ""
    }

    private fun asArray(value: Any?): JSONArray = when (value) {
        is JSONArray -> value
        is JSONObject -> JSONArray().put(value)
        else -> JSONArray()
    }

    private fun Any?.asTextOrNested(vararg keys: String): String = when (this) {
        is JSONObject -> keys.firstNotNullOfOrNull { key -> optString(key).takeIf { it.isNotBlank() } }.orEmpty()
        is String -> this
        else -> ""
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    private val JUNK_TAGS = setOf("seen live", "favorites", "favourites", "favorite", "favourite", "love", "loved", "awesome", "amazing", "best", "good", "great", "underrated", "guilty pleasure", "guilty pleasures")
}

internal object SpotifyHistoryImporter {
    fun read(resolver: ContentResolver, uris: List<Uri>): List<PlayEntry> {
        val all = ArrayList<PlayEntry>()
        uris.forEach { uri ->
            val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("Could not open one of the selected JSON files.")
            val parsed = org.json.JSONTokener(text).nextValue()
            val rows = when (parsed) {
                is JSONArray -> parsed
                is JSONObject -> parsed.optJSONArray("data") ?: parsed.optJSONArray("items") ?: JSONArray()
                else -> JSONArray()
            }
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val track = first(row, "master_metadata_track_name", "trackName", "track_name", "track")
                val artist = first(row, "master_metadata_album_artist_name", "artistName", "artist_name", "artist")
                val timestamp = parseTimestamp(first(row, "ts", "endTime", "played_at", "timestamp"))
                if (track.isBlank() || artist.isBlank() || timestamp <= 0L) continue
                val duration = (row.opt("ms_played") as? Number)?.toLong()
                    ?: (row.opt("msPlayed") as? Number)?.toLong()
                    ?: (row.opt("duration_ms") as? Number)?.toLong()
                    ?: 0L
                all.add(PlayEntry(track, artist, timestamp, duration.coerceAtLeast(0), first(row, "master_metadata_album_album_name", "albumName")))
            }
        }
        return all.distinctBy { "${it.timestamp}:${it.track.lowercase(Locale.ROOT)}:${it.artist.lowercase(Locale.ROOT)}" }.sortedByDescending { it.timestamp }
    }

    private fun first(row: JSONObject, vararg fields: String): String = fields.firstNotNullOfOrNull { key ->
        row.optString(key).takeIf { it.isNotBlank() && it != "null" }
    }.orEmpty()

    private fun parseTimestamp(value: String): Long {
        value.toLongOrNull()?.let { return if (it < 10_000_000_000L) it * 1000 else it }
        return try { Instant.parse(value).toEpochMilli() } catch (_: DateTimeParseException) {
            try { OffsetDateTime.parse(value).toInstant().toEpochMilli() } catch (_: DateTimeParseException) { 0L }
        }
    }
}
