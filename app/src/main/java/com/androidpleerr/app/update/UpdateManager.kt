package com.androidpleerr.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class GithubAsset(val name: String, val browser_download_url: String)
data class GithubRelease(val tag_name: String, val name: String?, val assets: List<GithubAsset>)

/**
 * Checks GitHub Releases for a newer build of this app and installs it.
 * Point [repo] at "owner/name" of the GitHub repository that Actions publishes releases to.
 */
class UpdateManager(private val context: Context, private val repo: String) {

    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun checkForUpdate(currentVersionName: String): GithubRelease? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val release = gson.fromJson(body, GithubRelease::class.java)
                val latestTag = release.tag_name.removePrefix("v")
                return@withContext if (latestTag != currentVersionName) release else null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun downloadApk(release: GithubRelease): File? = withContext(Dispatchers.IO) {
        val asset = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@withContext null
        try {
            val request = Request.Builder().url(asset.browser_download_url).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val file = File(dir, asset.name)
                resp.body?.byteStream()?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                file
            }
        } catch (e: Exception) {
            null
        }
    }

    fun promptInstall(apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
