package com.androidpleerr.app.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** TorrServer (https://github.com/YouROK/TorrServe) exposes a single JSON-RPC-ish endpoint. */
interface TorrServerApi {
    @POST("torrents")
    suspend fun torrentInfo(@Body body: TorrentActionRequest): Response<TorrentInfo>

    @POST("torrents")
    suspend fun torrentsList(@Body body: TorrentActionRequest): Response<List<TorrentInfo>>

    @POST("torrents")
    suspend fun removeTorrent(@Body body: TorrentActionRequest): Response<Unit>
}
