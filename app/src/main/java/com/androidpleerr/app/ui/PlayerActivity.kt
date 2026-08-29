package com.androidpleerr.app.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings as SystemSettings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.recyclerview.widget.LinearLayoutManager
import com.androidpleerr.app.data.TorrServerClient
import com.androidpleerr.app.data.TorrentFileStat
import com.androidpleerr.app.data.TorrentInfo
import com.androidpleerr.app.databinding.ActivityPlayerBinding
import com.androidpleerr.app.prefs.AppPrefs
import com.androidpleerr.app.util.Formatting
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Video playback screen. Two modes:
 *  - Torrent mode (EXTRA_HASH set): streams from TorrServer, shows a live speed/peers
 *    overlay and, for multi-file torrents, an episode picker.
 *  - Direct mode (EXTRA_DIRECT_URL set): plays an arbitrary URL, used by IPTV.
 *
 * Gestures (handled by [gestureOverlay], a transparent View on top of the player):
 *  - Double-tap left/right half  -> seek backward/forward, with a fading "±N сек" label.
 *  - Vertical swipe, left half   -> screen brightness.
 *  - Vertical swipe, right half  -> media volume.
 *  Plain taps are additionally forwarded to the underlying PlayerView so its native
 *  play/pause button and seek bar keep working as normal.
 *
 * Track selection: "🔊"/"💬" buttons in the bottom icon strip list every audio/text
 * track embedded in the file (label = file's own track name > language > "Дорожка N").
 * If a preferred language is set in Settings, it's auto-applied once per file.
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_HASH = "extra_hash"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DIRECT_URL = "extra_direct_url"
        const val EXTRA_RESUME_FILE_ID = "extra_resume_file_id"
        private const val SEEK_FEEDBACK_MS = 700L
    }

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var prefs: AppPrefs
    private var client: TorrServerClient? = null
    private var player: ExoPlayer? = null
    private var hash: String? = null
    private var currentFile: TorrentFileStat? = null
    private val statsHandler = Handler(Looper.getMainLooper())
    private var statsRunnable: Runnable? = null

    private var episodeFiles: List<TorrentFileStat> = emptyList()
    private var currentEpisodeIndex: Int = -1
    private var currentResumeKey: String? = null

    /** Reset per media item so the preferred-language override only applies once. */
    private var preferredLanguageApplied = false

    // --- Gesture state ---
    private lateinit var gestureDetector: GestureDetector
    private lateinit var audioManager: AudioManager
    private var dragStartY = 0f
    private var dragIsLeftSide = false
    private var dragStartVolume = 0
    private var dragStartBrightness = 0f
    private var isDragging = false
    private val dragThresholdPx = 24f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Don't let the screen dim/lock while a video is playing.
        binding.root.keepScreenOn = true
        prefs = AppPrefs(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        title = intent.getStringExtra(EXTRA_TITLE) ?: ""

        binding.audioTrackButton.setOnClickListener { showTrackPicker(C.TRACK_TYPE_AUDIO) }
        binding.subtitleTrackButton.setOnClickListener { showTrackPicker(C.TRACK_TYPE_TEXT) }

        setupGestures()

        val externalViewUri = if (intent.action == Intent.ACTION_VIEW) intent.data else null
        val directUrl = intent.getStringExtra(EXTRA_DIRECT_URL) ?: externalViewUri?.toString()
        if (directUrl != null) {
            // When launched by another app (Lampa, a browser, a file manager, ...) via
            // "open with", pick up whatever title it passed along — different apps use
            // different extra keys, so check the common ones.
            val externalTitle = intent.getStringExtra(EXTRA_TITLE)
                ?: intent.getStringExtra("title")
                ?: intent.getStringExtra(Intent.EXTRA_TITLE)
            if (externalTitle != null) title = externalTitle

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
            val resumeFileId = intent.getIntExtra(EXTRA_RESUME_FILE_ID, -1)
            val targetFile = if (resumeFileId != -1) episodeFiles.firstOrNull { it.id == resumeFileId } else null
            val firstVideo = targetFile ?: episodeFiles.firstOrNull()
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
        preferredLanguageApplied = false

        val selector = DefaultTrackSelector(this)
        val exoPlayer = ExoPlayer.Builder(this)
            .setTrackSelector(selector)
            .build()
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

            override fun onTracksChanged(tracks: Tracks) {
                if (!preferredLanguageApplied) {
                    preferredLanguageApplied = true
                    applyPreferredLanguages(tracks)
                }
            }
        })

        exoPlayer.prepare()
        player = exoPlayer
        currentResumeKey = resumeKey
    }

    /** Called when the current file finishes and "auto-next" is enabled in Settings. */
    private fun playNextEpisode() {
        val h = hash ?: return
        if (currentEpisodeIndex < 0 || episodeFiles.isEmpty()) return
        val nextIndex = currentEpisodeIndex + 1
        if (nextIndex >= episodeFiles.size) return
        playFile(h, episodeFiles[nextIndex])
    }

    // ---------------------------------------------------------------------------------
    // Preferred audio/subtitle language auto-selection
    // ---------------------------------------------------------------------------------

    private fun applyPreferredLanguages(tracks: Tracks) {
        val p = player ?: return
        val audioLang = prefs.preferredAudioLanguage.trim()
        val subLang = prefs.preferredSubtitleLanguage.trim()
        if (audioLang.isBlank() && subLang.isBlank()) return

        var builder = p.trackSelectionParameters.buildUpon()
        var changed = false

        if (audioLang.isNotBlank()) {
            findMatchingTrack(tracks, C.TRACK_TYPE_AUDIO, audioLang)?.let { option ->
                builder = builder
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.indexInGroup))
                changed = true
            }
        }
        if (subLang.isNotBlank()) {
            findMatchingTrack(tracks, C.TRACK_TYPE_TEXT, subLang)?.let { option ->
                builder = builder
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.indexInGroup))
                changed = true
            }
        }
        if (changed) p.trackSelectionParameters = builder.build()
    }

    private fun findMatchingTrack(tracks: Tracks, type: Int, langQuery: String): TrackOption? {
        for (group in tracks.groups.filter { it.type == type }) {
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val format = group.getTrackFormat(i)
                val lang = format.language ?: continue
                if (lang.contains(langQuery, ignoreCase = true) || langQuery.contains(lang, ignoreCase = true)) {
                    return TrackOption(group, i, labelFor(format, i))
                }
            }
        }
        return null
    }

    // ---------------------------------------------------------------------------------
    // Audio / subtitle track selection dialog
    // ---------------------------------------------------------------------------------

    private data class TrackOption(
        val group: Tracks.Group,
        val indexInGroup: Int,
        val label: String
    )

    /** Builds a human-friendly label for a track: file-embedded name > language > index. */
    private fun labelFor(format: Format, fallbackIndex: Int): String {
        format.label?.let { if (it.isNotBlank()) return it }
        format.language?.let { if (it.isNotBlank()) return it.uppercase() }
        return "Дорожка ${fallbackIndex + 1}"
    }

    private fun showTrackPicker(trackType: Int) {
        val p = player ?: return
        val groups = p.currentTracks.groups.filter { it.type == trackType }

        if (groups.isEmpty() || groups.all { it.length == 0 }) {
            android.widget.Toast.makeText(
                this,
                if (trackType == C.TRACK_TYPE_AUDIO) "В этом файле только одна звуковая дорожка"
                else "В этом файле нет встроенных субтитров",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        val options = mutableListOf<TrackOption>()
        var runningIndex = 0
        for (group in groups) {
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val format = group.getTrackFormat(i)
                options.add(TrackOption(group, i, labelFor(format, runningIndex)))
                runningIndex++
            }
        }

        val labels = mutableListOf<String>()
        if (trackType == C.TRACK_TYPE_TEXT) labels.add("Выключить субтитры")
        labels.addAll(options.map { opt -> if (isSelected(opt)) "✓ ${opt.label}" else opt.label })

        val title = if (trackType == C.TRACK_TYPE_AUDIO) "Озвучка" else "Субтитры"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels.toTypedArray()) { _, which ->
                val offset = if (trackType == C.TRACK_TYPE_TEXT) 1 else 0
                if (trackType == C.TRACK_TYPE_TEXT && which == 0) {
                    disableTrackType(C.TRACK_TYPE_TEXT)
                } else {
                    selectTrack(options[which - offset])
                }
            }
            .show()
    }

    private fun isSelected(option: TrackOption): Boolean {
        val p = player ?: return false
        return p.currentTracks.groups.any { g -> g == option.group && g.isTrackSelected(option.indexInGroup) }
    }

    private fun selectTrack(option: TrackOption) {
        val p = player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(option.group.type, false)
            .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.indexInGroup))
            .build()
    }

    private fun disableTrackType(trackType: Int) {
        val p = player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(trackType, true)
            .clearOverridesOfType(trackType)
            .build()
    }

    // ---------------------------------------------------------------------------------
    // Gestures: double-tap seek, swipe brightness/volume
    // ---------------------------------------------------------------------------------

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val isRight = e.x > binding.gestureOverlay.width / 2f
                val step = prefs.seekStepSeconds
                seekRelative(if (isRight) step else -step)
                showSeekFeedback(isRight, step)
                return true
            }
        })

        binding.gestureOverlay.setOnTouchListener { view, event ->
            gestureDetector.onTouchEvent(event)
            // Forward the raw event to the PlayerView underneath so its own
            // play/pause button, seek bar and tap-to-toggle-controls keep working.
            binding.playerView.dispatchTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartY = event.y
                    dragIsLeftSide = event.x < view.width / 2f
                    dragStartVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    dragStartBrightness = currentBrightness()
                    isDragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = dragStartY - event.y
                    if (abs(dy) > dragThresholdPx) {
                        isDragging = true
                        val fraction = dy / view.height.coerceAtLeast(1)
                        if (dragIsLeftSide) {
                            adjustBrightness(fraction)
                        } else {
                            adjustVolume(fraction)
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) hideAdjustFeedbackSoon()
                    isDragging = false
                }
            }
            true
        }
    }

    private fun seekRelative(deltaSeconds: Int) {
        val p = player ?: return
        val newPos = (p.currentPosition + deltaSeconds * 1000L).coerceIn(0, p.duration.coerceAtLeast(0))
        p.seekTo(newPos)
    }

    private fun showSeekFeedback(isRight: Boolean, step: Int) {
        val label = binding.let { if (isRight) it.seekFeedbackRight else it.seekFeedbackLeft }
        label.text = if (isRight) "+$step сек »" else "« -$step сек"
        label.animate().cancel()
        label.alpha = 1f
        label.animate().alpha(0f).setStartDelay(SEEK_FEEDBACK_MS).setDuration(250).start()
    }

    private fun currentBrightness(): Float {
        val lp = window.attributes
        if (lp.screenBrightness in 0f..1f) return lp.screenBrightness
        return try {
            SystemSettings.System.getInt(contentResolver, SystemSettings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (e: Exception) {
            0.5f
        }
    }

    private fun adjustBrightness(deltaFraction: Float) {
        val newValue = (dragStartBrightness + deltaFraction).coerceIn(0.02f, 1f)
        val lp = window.attributes
        lp.screenBrightness = newValue
        window.attributes = lp
        showAdjustFeedback("Яркость ${(newValue * 100).toInt()}%")
    }

    private fun adjustVolume(deltaFraction: Float) {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newVol = (dragStartVolume + deltaFraction * maxVol).toInt().coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        val percent = if (maxVol > 0) newVol * 100 / maxVol else 0
        showAdjustFeedback("Громкость $percent%")
    }

    private fun showAdjustFeedback(text: String) {
        binding.adjustFeedback.text = text
        binding.adjustFeedback.visibility = View.VISIBLE
    }

    private fun hideAdjustFeedbackSoon() {
        binding.adjustFeedback.postDelayed({ binding.adjustFeedback.visibility = View.GONE }, 400)
    }

    // ---------------------------------------------------------------------------------

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
            saveContinueWatchingIfApplicable(p)
            p.release()
        }
        player = null
    }

    /**
     * Records (or clears) the "continue watching" entry for the file that was just
     * playing — but only in torrent mode, and only while genuinely mid-way through it.
     * Barely-started or essentially-finished files are dropped instead of listed.
     */
    private fun saveContinueWatchingIfApplicable(p: ExoPlayer) {
        val h = hash ?: return
        val file = currentFile ?: return
        val duration = p.duration
        val position = p.currentPosition
        if (duration <= 0) return

        val ratio = position.toFloat() / duration.toFloat()
        if (position < 5000 || ratio > 0.95f) {
            prefs.removeContinueWatching(h, file.id)
            return
        }

        prefs.saveContinueWatching(
            com.androidpleerr.app.data.ContinueWatchingItem(
                hash = h,
                fileId = file.id,
                filePath = file.path,
                title = intent.getStringExtra(EXTRA_TITLE) ?: EpisodeListAdapter.displayName(file.path.substringAfterLast('/')),
                positionMs = position,
                durationMs = duration,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
        statsRunnable?.let { statsHandler.removeCallbacks(it) }
    }
}
