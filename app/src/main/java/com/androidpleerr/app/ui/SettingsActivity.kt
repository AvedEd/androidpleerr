package com.androidpleerr.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.androidpleerr.app.BuildConfig
import com.androidpleerr.app.data.TorrServerClient
import com.androidpleerr.app.databinding.ActivitySettingsBinding
import com.androidpleerr.app.prefs.AppPrefs
import com.androidpleerr.app.update.UpdateManager
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: AppPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPrefs(this)

        binding.serverHostInput.setText(prefs.serverHost)
        binding.iptvUrlInput.setText(prefs.iptvPlaylistUrl)
        binding.audioLangInput.setText(prefs.preferredAudioLanguage)
        binding.subtitleLangInput.setText(prefs.preferredSubtitleLanguage)
        binding.resumeSwitch.isChecked = prefs.resumePlayback
        binding.statsOverlaySwitch.isChecked = prefs.showStatsOverlay
        binding.autoNextSwitch.isChecked = prefs.autoNextEpisode
        binding.versionText.text = "androidpleerr v${BuildConfig.VERSION_NAME}"

        binding.saveButton.setOnClickListener {
            prefs.serverHost = binding.serverHostInput.text?.toString()?.trim().orEmpty().ifBlank { "127.0.0.1:8090" }
            prefs.iptvPlaylistUrl = binding.iptvUrlInput.text?.toString()?.trim().orEmpty()
            prefs.preferredAudioLanguage = binding.audioLangInput.text?.toString()?.trim().orEmpty()
            prefs.preferredSubtitleLanguage = binding.subtitleLangInput.text?.toString()?.trim().orEmpty()
            prefs.resumePlayback = binding.resumeSwitch.isChecked
            prefs.showStatsOverlay = binding.statsOverlaySwitch.isChecked
            prefs.autoNextEpisode = binding.autoNextSwitch.isChecked
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.testConnectionButton.setOnClickListener { testConnection() }
        binding.checkUpdateButton.setOnClickListener { checkUpdate() }
    }

    private fun testConnection() {
        val host = binding.serverHostInput.text?.toString()?.trim().orEmpty()
        if (host.isBlank()) return
        lifecycleScope.launch {
            binding.testConnectionButton.isEnabled = false
            val ok = TorrServerClient(host, prefs.serverScheme).ping()
            binding.testConnectionButton.isEnabled = true
            Toast.makeText(
                this@SettingsActivity,
                if (ok) "TorrServer доступен ✓" else "Не удалось подключиться",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun checkUpdate() {
        val repo = BuildConfig.UPDATE_REPO
        if (repo.isBlank()) {
            Toast.makeText(this, "UPDATE_REPO не задан в build.gradle.kts", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            val updater = UpdateManager(this@SettingsActivity, repo)
            val release = updater.checkForUpdate(BuildConfig.VERSION_NAME)
            if (release == null) {
                Toast.makeText(this@SettingsActivity, "Установлена последняя версия", Toast.LENGTH_SHORT).show()
                return@launch
            }
            Toast.makeText(this@SettingsActivity, "Скачиваю ${release.tag_name}...", Toast.LENGTH_SHORT).show()
            val file = updater.downloadApk(release)
            if (file != null) {
                updater.promptInstall(file)
            } else {
                Toast.makeText(this@SettingsActivity, "Не удалось скачать обновление", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
