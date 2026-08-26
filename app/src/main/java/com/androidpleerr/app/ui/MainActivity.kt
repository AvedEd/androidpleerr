package com.androidpleerr.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.androidpleerr.app.BuildConfig
import com.androidpleerr.app.data.TorrServerClient
import com.androidpleerr.app.databinding.ActivityMainBinding
import com.androidpleerr.app.prefs.AppPrefs
import com.androidpleerr.app.update.UpdateManager
import com.androidpleerr.app.util.TorrServerUrlUtils
import kotlinx.coroutines.launch

/** Home screen: connect to TorrServer, add a magnet/torrent link, browse active torrents. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPrefs
    private lateinit var client: TorrServerClient
    private lateinit var adapter: TorrentListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPrefs(this)
        client = TorrServerClient(prefs.serverHost, prefs.serverScheme)

        adapter = TorrentListAdapter(
            onClick = { torrent -> openPlayer(torrent) },
            onRemove = { torrent -> removeTorrent(torrent) }
        )
        binding.torrentList.layoutManager = LinearLayoutManager(this)
        binding.torrentList.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { refreshList() }

        binding.addButton.setOnClickListener {
            val link = binding.linkInput.text?.toString()?.trim().orEmpty()
            if (link.isEmpty()) {
                Toast.makeText(this, "Вставь magnet-ссылку или .torrent URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!TorrServerUrlUtils.isTorrentUrl(link)) {
                Toast.makeText(this, "Похоже, это не magnet и не ссылка на .torrent", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            addTorrent(link)
        }

        binding.iptvButton.setOnClickListener {
            startActivity(Intent(this, IptvActivity::class.java))
        }

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        checkForUpdates()
    }

    override fun onResume() {
        super.onResume()
        // Settings may have changed the server address while we were away.
        client = TorrServerClient(prefs.serverHost, prefs.serverScheme)
        refreshList()
    }

    private fun addTorrent(link: String) {
        lifecycleScope.launch {
            binding.swipeRefresh.isRefreshing = true
            val info = client.addTorrent(link)
            binding.swipeRefresh.isRefreshing = false
            if (info == null) {
                Toast.makeText(this@MainActivity, "Не удалось добавить торрент. Проверь адрес TorrServer в настройках.", Toast.LENGTH_LONG).show()
            } else {
                binding.linkInput.setText("")
                refreshList()
            }
        }
    }

    private fun removeTorrent(torrent: com.androidpleerr.app.data.TorrentInfo) {
        val hash = torrent.hash ?: return
        lifecycleScope.launch {
            client.removeTorrent(hash)
            refreshList()
        }
    }

    private fun refreshList() {
        lifecycleScope.launch {
            binding.swipeRefresh.isRefreshing = true
            val list = client.listTorrents()
            adapter.submit(list)
            binding.emptyState.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun openPlayer(torrent: com.androidpleerr.app.data.TorrentInfo) {
        val hash = torrent.hash ?: return
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_HASH, hash)
            putExtra(PlayerActivity.EXTRA_TITLE, torrent.title ?: torrent.name)
        }
        startActivity(intent)
    }

    private fun checkForUpdates() {
        val repo = BuildConfig.UPDATE_REPO
        if (repo.isBlank()) return
        lifecycleScope.launch {
            val updater = UpdateManager(this@MainActivity, repo)
            val release = updater.checkForUpdate(BuildConfig.VERSION_NAME) ?: return@launch
            Toast.makeText(this@MainActivity, "Доступно обновление: ${release.tag_name}", Toast.LENGTH_LONG).show()
        }
    }
}
