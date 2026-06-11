package com.alpha.spendtracker.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Simple checker to see if a new version is available on GitHub Releases.
 */
class UpdateChecker(private val context: Context) {

    companion object {
        private const val GITHUB_REPO = "saikumar2882/spend-analyzer" // Replace with your repo
        private const val TAG = "UpdateChecker"
    }

    suspend fun checkForUpdates(currentVersion: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val latestVersion = json.getString("tag_name").removePrefix("v")
                    
                    if (isNewerVersion(currentVersion, latestVersion)) {
                        // Try to find a direct APK link in assets
                        val assets = json.optJSONArray("assets")
                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val name = asset.getString("name")
                                if (name.endsWith(".apk")) {
                                    return@withContext asset.getString("browser_download_url")
                                }
                            }
                        }
                        // Fallback to the release page
                        return@withContext json.getString("html_url")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates: ${e.message}")
            }
            null
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        
        for (i in 0 until minOf(currentParts.size, latestParts.size)) {
            if (latestParts[i] > currentParts[i]) return true
            if (latestParts[i] < currentParts[i]) return false
        }
        return latestParts.size > currentParts.size
    }

    fun openUpdateUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
}
