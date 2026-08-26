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

/** Loads an M3U playlist (from Settings) and lets the user browse/play channels. */
class IptvActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIptvBinding
    private lateinit var prefs: AppPrefs
    private lateinit var adapter: IptvChannelAdapter
    private var allChannels: List<IptvChannel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIptvBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPrefs(this)

        adapter = IptvChannelAdapter { channel -> playChannel(channel) }
        binding.channelList.layoutManager = LinearLayoutManager(this)
        binding.channelList.adapter = adapter

        binding.searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.swipeRefresh.setOnRefreshListener { loadPlaylist() }
        loadPlaylist()
    }

    private fun loadPlaylist() {
        val url = prefs.iptvPlaylistUrl
        if (url.isBlank()) {
            Toast.makeText(this, "Укажи ссылку на M3U-плейлист в настройках", Toast.LENGTH_LONG).show()
            binding.swipeRefresh.isRefreshing = false
            return
        }
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val channels = try {
                withContext(Dispatchers.IO) { IptvPlaylistParser.fetchAndParse(url) }
            } catch (e: Exception) {
                emptyList()
            }
            allChannels = channels
            adapter.submit(channels)
            binding.emptyState.visibility = if (channels.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun filter(query: String) {
        val filtered = if (query.isBlank()) allChannels
        else allChannels.filter { it.name.contains(query, ignoreCase = true) }
        adapter.submit(filtered)
    }

    private fun playChannel(channel: IptvChannel) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_DIRECT_URL, channel.url)
            putExtra(PlayerActivity.EXTRA_TITLE, channel.name)
        }
        startActivity(intent)
    }
}
