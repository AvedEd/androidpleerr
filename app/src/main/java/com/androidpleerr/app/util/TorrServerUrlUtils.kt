package com.androidpleerr.app.util

import android.net.Uri

/** Helpers for pulling info back out of a TorrServer stream URL / magnet link. */
object TorrServerUrlUtils {

    fun isMagnet(link: String): Boolean = link.startsWith("magnet:")

    fun isTorrentUrl(link: String): Boolean =
        isMagnet(link) || link.endsWith(".torrent", ignoreCase = true) || link.startsWith("http")

    fun hostOf(streamUrl: String): String? {
        val uri = Uri.parse(streamUrl)
        val host = uri.host ?: return null
        return if (uri.port == -1) host else "$host:${uri.port}"
    }

    fun fileNameOf(streamUrl: String): String = Uri.parse(streamUrl).lastPathSegment ?: "video"
}
