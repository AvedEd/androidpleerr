package com.androidpleerr.app.prefs

import android.content.Context
import com.androidpleerr.app.data.ContinueWatchingItem
import com.androidpleerr.app.data.IptvPlaylist
import com.google.gson.Gson

/** Small SharedPreferences wrapper holding every user-configurable setting. */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("androidpleerr_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    var serverHost: String
        get() = prefs.getString("server_host", "127.0.0.1:8090") ?: "127.0.0.1:8090"
        set(value) = prefs.edit().putString("server_host", value).apply()

    var serverScheme: String
        get() = prefs.getString("server_scheme", "http") ?: "http"
        set(value) = prefs.edit().putString("server_scheme", value).apply()

    // Preferred language codes (e.g. "rus", "ru", "eng") used to auto-select a matching
    // audio/subtitle track when a new file starts playing, instead of always falling
    // back to whatever track the file lists first. Empty = no auto-selection.
    var preferredAudioLanguage: String
        get() = prefs.getString("pref_audio_lang", "") ?: ""
        set(value) = prefs.edit().putString("pref_audio_lang", value).apply()

    var preferredSubtitleLanguage: String
        get() = prefs.getString("pref_subtitle_lang", "") ?: ""
        set(value) = prefs.edit().putString("pref_subtitle_lang", value).apply()

    var seekStepSeconds: Int
        get() = prefs.getInt("seek_step", 10)
        set(value) = prefs.edit().putInt("seek_step", value).apply()

    var playbackSpeed: Float
        get() = prefs.getFloat("playback_speed", 1.0f)
        set(value) = prefs.edit().putFloat("playback_speed", value).apply()

    var resizeMode: Int
        get() = prefs.getInt("resize_mode", 0)
        set(value) = prefs.edit().putInt("resize_mode", value).apply()

    var showStatsOverlay: Boolean
        get() = prefs.getBoolean("show_stats_overlay", true)
        set(value) = prefs.edit().putBoolean("show_stats_overlay", value).apply()

    var autoNextEpisode: Boolean
        get() = prefs.getBoolean("auto_next", true)
        set(value) = prefs.edit().putBoolean("auto_next", value).apply()

    var resumePlayback: Boolean
        get() = prefs.getBoolean("resume_playback", true)
        set(value) = prefs.edit().putBoolean("resume_playback", value).apply()

    var subtitlesEnabled: Boolean
        get() = prefs.getBoolean("subtitles_enabled", true)
        set(value) = prefs.edit().putBoolean("subtitles_enabled", value).apply()

    var tunnelingEnabled: Boolean
        get() = prefs.getBoolean("tunneling", false)
        set(value) = prefs.edit().putBoolean("tunneling", value).apply()

    fun savePosition(fileKey: String, positionMs: Long) {
        if (positionMs > 5000) {
            prefs.edit().putLong("pos_$fileKey", positionMs).apply()
        }
    }

    fun loadPosition(fileKey: String): Long = prefs.getLong("pos_$fileKey", 0L)

    // ---- IPTV favorites (stored as a set of channel URLs, which are stable per-channel) ----

    fun isFavoriteChannel(url: String): Boolean =
        prefs.getStringSet("iptv_favorites", emptySet())?.contains(url) == true

    fun toggleFavoriteChannel(url: String) {
        val current = HashSet(prefs.getStringSet("iptv_favorites", emptySet()).orEmpty())
        if (current.contains(url)) current.remove(url) else current.add(url)
        prefs.edit().putStringSet("iptv_favorites", current).apply()
    }

    fun favoriteChannelUrls(): Set<String> =
        prefs.getStringSet("iptv_favorites", emptySet()).orEmpty()

    // ---- IPTV playlists: one or more M3U sources, stored as raw lines --------------------
    // Each line is either "URL" or "Name|URL". Kept as plain text (not JSON) so it's easy
    // to edit by hand in a plain multi-line text field in Settings.

    /** Raw, editable text — one playlist per line — shown directly in the Settings field. */
    fun iptvPlaylistsRawText(): String {
        val raw = prefs.getString("iptv_playlists_raw", null)
        if (raw != null) return raw
        // Migrate the old single-URL preference from earlier versions, if present.
        val legacy = prefs.getString("iptv_playlist_url", "") ?: ""
        return legacy
    }

    fun saveIptvPlaylistsRawText(raw: String) {
        prefs.edit().putString("iptv_playlists_raw", raw).apply()
    }

    fun iptvPlaylists(): List<IptvPlaylist> {
        val raw = iptvPlaylistsRawText()
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapIndexed { index, line ->
                val parts = line.split("|", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank()) {
                    IptvPlaylist(name = parts[0].trim(), url = parts[1].trim())
                } else {
                    IptvPlaylist(name = "Плейлист ${index + 1}", url = parts[0].trim())
                }
            }
            .filter { it.url.isNotBlank() }
            .toList()
    }

    // ---- Offline playlist cache: raw M3U text saved per playlist URL after every fetch ----

    private fun cacheKey(playlistUrl: String) = "iptv_cache_${playlistUrl.hashCode()}"

    fun cachePlaylist(playlistUrl: String, rawM3uText: String) {
        prefs.edit().putString(cacheKey(playlistUrl), rawM3uText).apply()
    }

    fun cachedPlaylist(playlistUrl: String): String? = prefs.getString(cacheKey(playlistUrl), null)

    // ---- Continue watching: torrent files the user started but hasn't finished -----------

    fun saveContinueWatching(item: ContinueWatchingItem) {
        val key = "cw_${item.hash}_${item.fileId}"
        prefs.edit().putString(key, gson.toJson(item)).apply()
    }

    fun removeContinueWatching(hash: String, fileId: Int) {
        prefs.edit().remove("cw_${hash}_$fileId").apply()
    }

    fun listContinueWatching(): List<ContinueWatchingItem> {
        return prefs.all.entries
            .filter { it.key.startsWith("cw_") && it.value is String }
            .mapNotNull { entry ->
                try {
                    gson.fromJson(entry.value as String, ContinueWatchingItem::class.java)
                } catch (e: Exception) {
                    null
                }
            }
            .sortedByDescending { it.updatedAt }
    }
}
