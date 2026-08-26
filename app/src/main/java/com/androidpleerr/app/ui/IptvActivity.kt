package com.androidpleerr.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.androidpleerr.app.data.IptvChannel
import com.androidpleerr.app.databinding.ActivityIptvBinding
import com.androidpleerr.app.iptv.IptvPlaylistParser
import com.androidpleerr.app.prefs.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads an M3U playlist (from Settings) and lets the user browse/play channels.
 * Supports: search, favorites (starred channels, filterable), and an offline
 * cache of the last successfully loaded playlist so channels still show up
 * with no network connection.
 */
class IptvActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIptvBinding
    private lateinit var prefs: AppPrefs
    private lateinit var adapter: IptvChannelAdapter
    private var allChannels: List<IptvChannel> = emptyList()
    private var showFavoritesOnly = false
    private var currentQuery: String = ""

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

        binding.swipeRefresh.setOnRefreshListener { loadPlaylist(forceNetwork = true) }
        loadPlaylist(forceNetwork = false)
    }

    override fun onResume() {
        super.onResume()
        // Favorite state may have changed while in the player/back from it.
        applyFilters()
    }

    private fun loadPlaylist(forceNetwork: Boolean) {
        val url = prefs.iptvPlaylistUrl
        if (url.isBlank()) {
            // No URL configured — fall back to whatever is cached, if anything.
            loadFromCacheOrEmpty()
            return
        }

        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val rawText = try {
                withContext(Dispatchers.IO) { IptvPlaylistParser.fetchRaw(url) }
            } catch (e: Exception) {
                null
            }

            if (rawText != null) {
                prefs.cachePlaylist(rawText)
                allChannels = IptvPlaylistParser.parsePlaylist(rawText)
                applyFilters()
                binding.offlineBanner.visibility = android.view.View.GONE
            } else {
                // Network failed — try the offline cache instead of showing nothing.
                val hadCache = loadFromCacheOrEmpty()
                if (!hadCache) {
                    Toast.makeText(this@IptvActivity, "Нет сети и нет сохранённого плейлиста", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@IptvActivity, "Нет сети — показан сохранённый плейлист", Toast.LENGTH_SHORT).show()
                    binding.offlineBanner.visibility = android.view.View.VISIBLE
                }
            }
            binding.swipeRefresh.isRefreshing = false
        }
    }

    /** Returns true if a cached playlist existed and was loaded. */
    private fun loadFromCacheOrEmpty(): Boolean {
        val cached = prefs.cachedPlaylist()
        return if (cached != null) {
            allChannels = IptvPlaylistParser.parsePlaylist(cached)
            applyFilters()
            true
        } else {
            allChannels = emptyList()
            applyFilters()
            false
        }
    }

    private fun applyFilters() {
        var list = allChannels
        if (showFavoritesOnly) {
            list = list.filter { prefs.isFavoriteChannel(it.url) }
        }
        if (currentQuery.isNotBlank()) {
            list = list.filter { it.name.contains(currentQuery, ignoreCase = true) }
        }
        adapter.submit(list)
        binding.emptyState.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.emptyState.text = if (showFavoritesOnly && allChannels.isNotEmpty())
            "Нет избранных каналов.\nНажми на звёздочку у канала, чтобы добавить."
        else
            "Плейлист пуст. Укажи ссылку на M3U в настройках."
    }

    private fun playChannel(channel: IptvChannel) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_DIRECT_URL, channel.url)
            putExtra(PlayerActivity.EXTRA_TITLE, channel.name)
        }
        startActivity(intent)
    }
}
