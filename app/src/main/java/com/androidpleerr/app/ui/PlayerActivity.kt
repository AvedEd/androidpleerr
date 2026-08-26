package com.androidpleerr.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import com.androidpleerr.app.data.TorrServerClient
import com.androidpleerr.app.data.TorrentFileStat
import com.androidpleerr.app.data.TorrentInfo
import com.androidpleerr.app.databinding.ActivityPlayerBinding
import com.androidpleerr.app.prefs.AppPrefs
import com.androidpleerr.app.util.Formatting
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Video playback screen. Two modes:
 *  - Torrent mode (EXTRA_HASH set): streams from TorrServer, shows a live speed/peers
 *    overlay and, for multi-file torrents, an episode picker (best-of APK2).
 *  - Direct mode (EXTRA_DIRECT_URL set): plays an arbitrary URL, used by IPTV (best-of APK1).
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_HASH = "extra_hash"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DIRECT_URL = "extra_direct_url"
    }

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var prefs: AppPrefs
    private var client: TorrServerClient? = null
    private var player: ExoPlayer? = null
    private var hash: String? = null
    private var currentFile: TorrentFileStat? = null
    private val statsHandler = Handler(Looper.getMainLooper())
    private var statsRunnable: Runnable? = null

    // Full ordered episode list for the current torrent + index of what's playing,
    // used to support real auto-next-episode behaviour.
    private var episodeFiles: List<TorrentFileStat> = emptyList()
    private var currentEpisodeIndex: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPrefs(this)

        title = intent.getStringExtra(EXTRA_TITLE) ?: ""

        val directUrl = intent.getStringExtra(EXTRA_DIRECT_URL)
        if (directUrl != null) {
            binding.statsOverlay.visibility = View.GONE
            binding.episodeList.visibility = View.GONE
            startPlayback(directUrl, resumeKey = directUrl)
            return
        }

        hash = intent.getStringExtra(EXTRA_HASH)
        client = TorrServerClient(prefs.serverHost, prefs.serverScheme)
        loadTorrentAndPlay()
    }

    private fun loadTorrentAndPlay() {
        val h = hash ?: return
        val c = client ?: return
        lifecycleScope.launch {
            val info = c.getTorrent(h)
            if (info == null) {
                binding.statusText.text = "Не удалось загрузить торрент"
                return@launch
            }
            setupEpisodes(info)
            val firstVideo = episodeFiles.firstOrNull()
            if (firstVideo != null) {
                playFile(h, firstVideo)
            } else {
                binding.statusText.text = "Видео файлы не найдены в торренте"
            }
            startStatsPolling(h)
        }
    }

    private fun setupEpisodes(info: TorrentInfo) {
        val videos = EpisodeListAdapter.videoFiles(info.fileStats.orEmpty())
        episodeFiles = videos
        if (videos.size <= 1) {
            binding.episodeList.visibility = View.GONE
            return
        }
        binding.episodeList.visibility = View.VISIBLE
        binding.episodeList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        refreshEpisodeAdapter()
    }

    private fun refreshEpisodeAdapter() {
        if (episodeFiles.size <= 1) return
        binding.episodeList.adapter = EpisodeListAdapter(episodeFiles, currentFile?.id ?: -1) { file ->
            hash?.let { playFile(it, file) }
        }
    }

    private fun playFile(hash: String, file: TorrentFileStat) {
        currentFile = file
        currentEpisodeIndex = episodeFiles.indexOfFirst { it.id == file.id }
        val c = client ?: return
        val url = c.streamUrl(hash, file.id)
        startPlayback(url, resumeKey = file.path)
        refreshEpisodeAdapter()
    }

    private fun startPlayback(url: String, resumeKey: String) {
        releasePlayer()
        val exoPlayer = ExoPlayer.Builder(this).build()
        binding.playerView.player = exoPlayer
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.playWhenReady = true

        if (prefs.resumePlayback) {
            val pos = prefs.loadPosition(resumeKey)
            if (pos > 0) exoPlayer.seekTo(pos)
        }
        exoPlayer.setPlaybackSpeed(prefs.playbackSpeed)

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                binding.statusText.text = "Ошибка воспроизведения: ${error.errorCodeName}"
                binding.statusText.visibility = View.VISIBLE
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    binding.statusText.visibility = View.GONE
                }
                if (playbackState == Player.STATE_ENDED && prefs.autoNextEpisode) {
                    playNextEpisode()
                }
            }
        })

        exoPlayer.prepare()
        player = exoPlayer
        currentResumeKey = resumeKey
    }

    private var currentResumeKey: String? = null

    /** Called when the current file finishes and "auto-next" is enabled in Settings. */
    private fun playNextEpisode() {
        val h = hash ?: return
        if (currentEpisodeIndex < 0 || episodeFiles.isEmpty()) return
        val nextIndex = currentEpisodeIndex + 1
        if (nextIndex >= episodeFiles.size) {
            // Was the last episode — nothing further to auto-play.
            return
        }
        val nextFile = episodeFiles[nextIndex]
        playFile(h, nextFile)
    }

    private fun startStatsPolling(hash: String) {
        if (!prefs.showStatsOverlay) {
            binding.statsOverlay.visibility = View.GONE
            return
        }
        binding.statsOverlay.visibility = View.VISIBLE
        val c = client ?: return
        statsRunnable = object : Runnable {
            override fun run() {
                lifecycleScope.launch {
                    val info = c.getTorrent(hash)
                    val stat = info?.stat
                    binding.statsText.text = "${Formatting.formatSpeed(stat?.downloadSpeed)} · ${stat?.peers ?: 0} peers"
                }
                statsHandler.postDelayed(this, 2000)
            }
        }
        statsHandler.post(statsRunnable!!)
    }

    private fun releasePlayer() {
        player?.let { p ->
            currentResumeKey?.let { key -> prefs.savePosition(key, p.currentPosition) }
            p.release()
        }
        player = null
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
        statsRunnable?.let { statsHandler.removeCallbacks(it) }
    }
}
