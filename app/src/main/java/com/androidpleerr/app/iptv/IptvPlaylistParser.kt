package com.androidpleerr.app.iptv

import com.androidpleerr.app.data.IptvChannel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

/** Parses standard M3U / M3U8 IPTV playlists into a flat channel list. */
object IptvPlaylistParser {

    private val EXTINF_NAME = Regex(",(.*)$")
    private val TVG_LOGO = Regex("tvg-logo=\"([^\"]*)\"")
    private val GROUP_TITLE = Regex("group-title=\"([^\"]*)\"")

    fun parsePlaylist(text: String): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String? = null

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    pendingName = EXTINF_NAME.find(line)?.groupValues?.get(1)?.trim()
                    pendingLogo = TVG_LOGO.find(line)?.groupValues?.get(1)
                    pendingGroup = GROUP_TITLE.find(line)?.groupValues?.get(1)
                }
                line.isBlank() || line.startsWith("#") -> { /* skip other directives */ }
                else -> {
                    channels.add(
                        IptvChannel(
                            name = pendingName ?: line,
                            url = line,
                            logo = pendingLogo,
                            group = pendingGroup
                        )
                    )
                    pendingName = null
                    pendingLogo = null
                    pendingGroup = null
                }
            }
        }
        return channels
    }

    /** Downloads and parses a playlist. Must be called off the main thread. */
    fun fetchAndParse(url: String): List<IptvChannel> {
        val connection = URL(url).openConnection()
        connection.connectTimeout = 8000
        connection.readTimeout = 15000
        BufferedReader(InputStreamReader(connection.getInputStream())).use { reader ->
            val text = reader.readText()
            return parsePlaylist(text)
        }
    }
}
