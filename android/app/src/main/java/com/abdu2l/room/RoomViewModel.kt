package com.abdu2l.room

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal data class RoomUiState(
    val tab: RoomTab = RoomTab.OVERVIEW,
    val range: TimeRange = TimeRange.WEEK,
    val theme: String = "grey",
    val username: String = "",
    val apiKey: String = "",
    val stats: RoomStats? = null,
    val history: List<PlayEntry>? = null,
    val historyActive: Boolean = false,
    val genres: List<Pair<String, Int>> = emptyList(),
    val loading: Boolean = false,
    val loadingGenres: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val notes: Map<String, TrackNote> = emptyMap()
) {
    val activeHistory: Boolean get() = historyActive && history != null
    val activeEntries: List<PlayEntry> get() = if (activeHistory) history.orEmpty() else stats?.recent.orEmpty()
    val activeTracks: List<ListeningTrack> get() = if (activeHistory) rankTracks(history.orEmpty()) else stats?.tracks.orEmpty()
    val activeArtists: List<ListeningArtist> get() = if (activeHistory) rankArtists(history.orEmpty()) else stats?.artists.orEmpty()

    private fun rankTracks(rows: List<PlayEntry>): List<ListeningTrack> = rows.groupBy {
        it.track.lowercase(Locale.ROOT) + "\u0000" + it.artist.lowercase(Locale.ROOT)
    }.map { (_, entries) -> ListeningTrack(entries.first().track, entries.first().artist, entries.size, durationMs = entries.sumOf { it.durationMs }) }
        .sortedByDescending { it.plays }

    private fun rankArtists(rows: List<PlayEntry>): List<ListeningArtist> = rows.groupBy { it.artist.lowercase(Locale.ROOT) }
        .map { (_, entries) -> ListeningArtist(entries.first().artist, entries.size) }
        .sortedByDescending { it.plays }
}

internal class RoomViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("room-settings", Application.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        RoomUiState(
            theme = preferences.getString("theme", "grey") ?: "grey",
            username = preferences.getString("lastfm_username", "") ?: "",
            apiKey = BuildConfig.LASTFM_API_KEY.ifBlank { preferences.getString("lastfm_api_key", "") ?: "" },
            notes = readNotes()
        )
    )
    val state: StateFlow<RoomUiState> = _state

    init {
        val initial = _state.value
        if (initial.username.isNotBlank() && initial.apiKey.isNotBlank()) {
            loadStats(initial.username, initial.apiKey)
        }
    }

    fun clearNotice() {
        _state.update { it.copy(error = null, message = null) }
    }

    fun setTab(tab: RoomTab) {
        _state.update { it.copy(tab = tab, error = null, message = null) }
        if (tab == RoomTab.GALAXY && _state.value.stats != null && _state.value.genres.isEmpty()) loadGenres()
    }

    fun setRange(range: TimeRange) {
        if (_state.value.range == range) return
        _state.update { it.copy(range = range, error = null) }
        if (_state.value.stats != null && !_state.value.activeHistory) loadStats(_state.value.username, _state.value.apiKey)
    }

    fun setTheme(theme: String) {
        val allowed = setOf("grey", "pink", "green", "red")
        val safeTheme = theme.takeIf { it in allowed } ?: "grey"
        preferences.edit().putString("theme", safeTheme).apply()
        _state.update { it.copy(theme = safeTheme) }
    }

    fun connect(username: String, suppliedApiKey: String) {
        val cleanUsername = username.trim()
        val cleanKey = suppliedApiKey.trim().ifBlank { BuildConfig.LASTFM_API_KEY }
        if (cleanUsername.isBlank()) {
            _state.update { it.copy(error = "Enter your Last.fm username first.", message = null) }
            return
        }
        if (cleanKey.isBlank()) {
            _state.update { it.copy(error = "Room needs a Last.fm API key to read your public listening stats. Add one in Settings.", message = null) }
            return
        }
        preferences.edit().putString("lastfm_username", cleanUsername)
            .putString("lastfm_api_key", if (BuildConfig.LASTFM_API_KEY.isBlank()) cleanKey else "")
            .apply()
        _state.update { it.copy(username = cleanUsername, apiKey = cleanKey, historyActive = false, message = null) }
        loadStats(cleanUsername, cleanKey)
    }

    fun refresh() {
        val current = _state.value
        if (current.activeHistory) {
            _state.update { it.copy(message = "Your imported history is ready on this device.", error = null) }
        } else if (current.username.isNotBlank() && current.apiKey.isNotBlank()) {
            loadStats(current.username, current.apiKey)
        } else {
            _state.update { it.copy(error = "Add your Last.fm username and API key in Settings to refresh.") }
        }
    }

    private fun loadStats(username: String, apiKey: String) {
        _state.update { it.copy(loading = true, error = null, message = null, genres = emptyList()) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { LastFmApi.load(username, apiKey, _state.value.range) }
                _state.update { it.copy(stats = result, historyActive = false, loading = false, message = "Loaded ${result.recent.size} recent scrobbles.") }
            } catch (exception: Exception) {
                _state.update { it.copy(loading = false, error = exception.message ?: "Could not load Last.fm data. Check your connection and try again.") }
            }
        }
    }

    fun loadGenres() {
        val current = _state.value
        val stats = current.stats ?: return
        if (current.loadingGenres) return
        _state.update { it.copy(loadingGenres = true, error = null) }
        viewModelScope.launch {
            val tags = withContext(Dispatchers.IO) { LastFmApi.loadTags(current.username, current.apiKey, stats.artists) }
            _state.update { it.copy(genres = tags, loadingGenres = false) }
        }
    }

    fun importHistory(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        _state.update { it.copy(loading = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                val entries = withContext(Dispatchers.IO) { SpotifyHistoryImporter.read(getApplication<Application>().contentResolver, uris) }
                if (entries.isEmpty()) throw IllegalStateException("No Spotify listening rows found. Choose JSON files from your Spotify account data export.")
                _state.update {
                    it.copy(history = entries, historyActive = true, range = TimeRange.ALL, loading = false,
                        message = "Imported ${entries.size} plays. The file stays on this device.", error = null, tab = RoomTab.OVERVIEW)
                }
            } catch (exception: Exception) {
                _state.update { it.copy(loading = false, error = exception.message ?: "Could not read the selected Spotify JSON files.") }
            }
        }
    }

    fun clearHistory() {
        _state.update { it.copy(history = null, historyActive = false, range = TimeRange.WEEK, message = "Imported history cleared.") }
        if (_state.value.stats != null) loadStats(_state.value.username, _state.value.apiKey)
    }

    fun forgetLastFm() {
        preferences.edit().remove("lastfm_username").remove("lastfm_api_key").apply()
        _state.update { it.copy(username = "", apiKey = BuildConfig.LASTFM_API_KEY, stats = null, genres = emptyList(), error = null, message = "Last.fm profile disconnected.") }
    }

    fun noteFor(track: ListeningTrack): TrackNote = _state.value.notes[noteKey(track.artist, track.name)] ?: TrackNote()

    fun saveNote(track: ListeningTrack, note: TrackNote) {
        val key = noteKey(track.artist, track.name)
        val updated = _state.value.notes.toMutableMap()
        if (note.rating == 0 && note.text.isBlank()) updated.remove(key) else updated[key] = note.copy(text = note.text.take(140))
        val json = JSONObject()
        updated.forEach { (noteKey, value) -> json.put(noteKey, JSONObject().put("rating", value.rating).put("text", value.text)) }
        preferences.edit().putString("track_notes", json.toString()).apply()
        _state.update { it.copy(notes = updated) }
    }

    private fun readNotes(): Map<String, TrackNote> = try {
        val json = JSONObject(preferences.getString("track_notes", "{}") ?: "{}")
        buildMap {
            json.keys().forEach { key ->
                val item = json.optJSONObject(key) ?: return@forEach
                put(key, TrackNote(item.optInt("rating"), item.optString("text")))
            }
        }
    } catch (_: Exception) {
        emptyMap()
    }

    private fun noteKey(artist: String, track: String) = "${artist.trim().lowercase(Locale.ROOT)} | ${track.trim().lowercase(Locale.ROOT)}"
}
