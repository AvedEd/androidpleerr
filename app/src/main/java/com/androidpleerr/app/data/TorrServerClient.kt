package com.androidpleerr.app.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around a running TorrServer instance.
 * host example: "127.0.0.1:8090", scheme: "http" or "https".
 */
class TorrServerClient(private val host: String, private val scheme: String = "http") {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val api: TorrServerApi by lazy {
        Retrofit.Builder()
            .baseUrl("$scheme://$host/")
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TorrServerApi::class.java)
    }

    /** Adds (or fetches, if already added) a torrent by magnet link / .torrent URL. */
    suspend fun addTorrent(link: String, title: String? = null, poster: String? = null): TorrentInfo? {
        val resp = api.torrentInfo(TorrentActionRequest(action = "add", link = link, title = title, poster = poster, saveToDb = true))
        return if (resp.isSuccessful) resp.body() else null
    }

    suspend fun getTorrent(hash: String): TorrentInfo? {
        val resp = api.torrentInfo(TorrentActionRequest(action = "get", hash = hash))
        return if (resp.isSuccessful) resp.body() else null
    }

    suspend fun listTorrents(): List<TorrentInfo> {
        val resp = api.torrentsList(TorrentActionRequest(action = "list"))
        return if (resp.isSuccessful) resp.body().orEmpty() else emptyList()
    }

    suspend fun removeTorrent(hash: String): Boolean {
        val resp = api.removeTorrent(TorrentActionRequest(action = "rem", hash = hash))
        return resp.isSuccessful
    }

    /** Quick reachability check used by the "test connection" button in Settings. */
    suspend fun ping(): Boolean = try {
        api.torrentsList(TorrentActionRequest(action = "list")).isSuccessful
    } catch (e: Exception) {
        false
    }

    /** Builds the direct HTTP stream URL TorrServer serves a given file at. */
    fun streamUrl(hash: String, fileIndex: Int): String =
        "$scheme://$host/stream/index.mp4?link=$hash&index=$fileIndex&play"
}
