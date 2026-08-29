package com.androidpleerr.app.data

import com.google.gson.annotations.SerializedName

/**
 * Request body for TorrServer's single "/torrents" endpoint.
 * action can be: "add", "get", "list", "rem", "drop".
 */
data class TorrentActionRequest(
    val action: String,
    val link: String? = null,
    val hash: String? = null,
    val title: String? = null,
    val poster: String? = null,
    @SerializedName("save_to_db") val saveToDb: Boolean? = null
)

data class TorrentInfo(
    val hash: String? = null,
    val title: String? = null,
    val poster: String? = null,
    val name: String? = null,
    @SerializedName("file_stats") val fileStats: List<TorrentFileStat>? = null,
    val stat: TorrentStat? = null
)

data class TorrentFileStat(
    val id: Int,
    val path: String,
    val length: Long = 0L
)

data class TorrentStat(
    @SerializedName("download_speed") val downloadSpeed: Long? = null,
    val peers: Int? = null,
    @SerializedName("torrent_size") val torrentSize: Long? = null,
    @SerializedName("preloaded_bytes") val preloadedBytes: Long? = null
)

/** A single channel parsed out of an M3U / IPTV playlist. */
data class IptvChannel(
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String? = null
)

/** One configured IPTV source. Users can add several; channels from all of them are merged. */
data class IptvPlaylist(
    val name: String,
    val url: String
)

/** A torrent file the user started watching but didn't finish — shown as "Continue watching". */
data class ContinueWatchingItem(
    val hash: String,
    val fileId: Int,
    val filePath: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)
