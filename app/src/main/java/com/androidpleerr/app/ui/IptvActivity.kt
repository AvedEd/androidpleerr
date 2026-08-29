package com.androidpleerr.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.androidpleerr.app.R
import com.androidpleerr.app.data.IptvChannel
import com.androidpleerr.app.data.IptvPlaylist
import com.androidpleerr.app.databinding.ActivityIptvBinding
import com.androidpleerr.app.iptv.IptvPlaylistParser
import com.androidpleerr.app.prefs.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads every configured M3U playlist (see Settings — one per line, supports several
 * sources merged together) and lets the user browse/play channels.
 *
 * Supports: search, category chips (from each channel's group-title, falling back to
 * the playlist's own name when the M3U doesn't tag groups), favorites, and an offline
 * cache per playlist so channels still show up with no network connection.
 */
class IptvActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIptvBinding
    private lateinit var prefs: AppPrefs
    private lateinit var adapter: IptvChannelAdapter
    private var allChannels: List<IptvChannel> = emptyList()
    private var showFavoritesOnly = false
    private var currentQuery: String = ""
    private var currentCategory: String? = null // null = "Все"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIptvBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPrefs(this)

        adapter = IptvChannelAdapter(
            onClick = { channel -> playChannel(channel) },
            onToggleFavorite = { channel ->
                prefs.toggleFavoriteChannel(channel.url)
                applyFilters()
            },
            isFavorite = { channel -> prefs.isFavoriteChannel(channel.url) }
        )
        binding.channelList.layoutManager = LinearLayoutManager(this)
        binding.channelList.adapter = adapter

        binding.searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s?.toString().orEmpty()
                applyFilters()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.favoritesToggle.setOnCheckedChangeListener { _, checked ->
            showFavoritesOnly = checked
            applyFilters()
        }

        binding.swipeRefresh.setOnRefreshListener { loadPlaylists() }
        loadPlaylists()
    }

    override fun onResume() {
        super.onResume()
        applyFilters()
    }

    private fun loadPlaylists() {
        val playlists = prefs.iptvPlaylists()
        if (playlists.isEmpty()) {
            allChannels = emptyList()
            rebuildCategoryChips()
            applyFilters()
            binding.swipeRefresh.isRefreshing = false
            return
        }

        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val merged = mutableListOf<IptvChannel>()
            var anyNetworkFailed = false

            for (playlist in playlists) {
                val text = fetchOrCache(playlist)
                if (text == null) {
                    anyNetworkFailed = true
                    continue
                }
                val parsed = withContext(Dispatchers.IO) { IptvPlaylistParser.parsePlaylist(text) }
                    .map { channel ->
                        // Channels without an explicit group-title fall back to the
                        // playlist's own name, so category chips stay meaningful even
                        // for playlists that don't tag groups.
                        if (channel.group.isNullOrBlank()) channel.copy(group = playlist.name) else channel
                    }
                merged.addAll(parsed)
            }

            allChannels = merged
            rebuildCategoryChips()
            applyFilters()
            binding.offlineBanner.visibility = if (anyNetworkFailed) android.view.View.VISIBLE else android.view.View.GONE
            if (anyNetworkFailed && merged.isEmpty()) {
                Toast.makeText(this@IptvActivity, "Нет сети и нет сохранённых плейлистов", Toast.LENGTH_LONG).show()
            } else if (anyNetworkFailed) {
                Toast.makeText(this@IptvActivity, "Часть плейлистов загружена из офлайн-кэша", Toast.LENGTH_SHORT).show()
            }
            binding.swipeRefresh.isRefreshing = false
        }
    }

    /** Fetches a playlist's raw text, caching on success; falls back to the cache on failure. */
    private suspend fun fetchOrCache(playlist: IptvPlaylist): String? {
        val fresh = try {
            withContext(Dispatchers.IO) { IptvPlaylistParser.fetchRaw(playlist.url) }
        } catch (e: Exception) {
            null
        }
        if (fresh != null) {
            prefs.cachePlaylist(playlist.url, fresh)
            return fresh
        }
        return prefs.cachedPlaylist(playlist.url)
    }

    private fun rebuildCategoryChips() {
        val categories = allChannels.mapNotNull { it.group }.distinct().sorted()
        binding.categoryChips.removeAllViews()
        binding.categoryChips.addView(makeChip("Все", null))
        for (category in categories) {
            binding.categoryChips.addView(makeChip(category, category))
        }
        // If the previously selected category no longer exists (e.g. after a reload), reset it.
        if (currentCategory != null && currentCategory !in categories) {
            currentCategory = null
        }
    }

    private fun makeChip(label: String, value: String?): TextView {
        val chip = TextView(this)
        chip.text = label
        chip.textSize = 12f
        chip.setPadding(28, 14, 28, 14)
        chip.gravity = Gravity.CENTER
        val margin = (8 * resources.displayMetrics.density).toInt()
        val params = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.marginEnd = margin
        chip.layoutParams = params
        chip.setBackgroundResource(R.drawable.bg_episode_item)
        chip.isSelected = currentCategory == value
        chip.setTextColor(
            ContextCompat.getColor(this, if (chip.isSelected) R.color.bg_dark else R.color.text_primary)
        )
        chip.setOnClickListener {
            currentCategory = value
            rebuildCategoryChips()
            applyFilters()
        }
        return chip
    }

    private fun applyFilters() {
        var list = allChannels
        if (showFavoritesOnly) {
            list = list.filter { prefs.isFavoriteChannel(it.url) }
        }
        if (currentCategory != null) {
            list = list.filter { it.group == currentCategory }
        }
        if (currentQuery.isNotBlank()) {
            list = list.filter { it.name.contains(currentQuery, ignoreCase = true) }
        }
        adapter.submit(list)
        binding.emptyState.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.emptyState.text = when {
            allChannels.isEmpty() -> "Плейлист пуст. Добавь ссылку на M3U в настройках."
            showFavoritesOnly -> "Нет избранных каналов.\nНажми на звёздочку у канала, чтобы добавить."
            else -> "Ничего не найдено."
        }
    }

    private fun playChannel(channel: IptvChannel) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_DIRECT_URL, channel.url)
            putExtra(PlayerActivity.EXTRA_TITLE, channel.name)
        }
        startActivity(intent)
    }
}
