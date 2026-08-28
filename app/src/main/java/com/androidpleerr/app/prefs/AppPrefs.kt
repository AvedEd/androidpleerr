package com.androidpleerr.app.prefs

import android.content.Context

/** Small SharedPreferences wrapper holding every user-configurable setting. */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("androidpleerr_prefs", Context.MODE_PRIVATE)

    var serverHost: String
        get() = prefs.getString("server_host", "127.0.0.1:8090") ?: "127.0.0.1:8090"
        set(value) = prefs.edit().putString("server_host", value).apply()

    var serverScheme: String
        get() = prefs.getString("server_scheme", "http") ?: "http"
        set(value) = prefs.edit().putString("server_scheme", value).apply()

    var iptvPlaylistUrl: String
        get() = prefs.getString("iptv_playlist_url", "") ?: ""
        set(value) = prefs.edit().putString("iptv_playlist_url", value).apply()

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

    // ---- Offline playlist cache: raw M3U text saved after every successful fetch ----

    fun cachePlaylist(rawM3uText: String) {
        prefs.edit().putString("iptv_playlist_cache", rawM3uText).apply()
    }

    fun cachedPlaylist(): String? = prefs.getString("iptv_playlist_cache", null)
}
