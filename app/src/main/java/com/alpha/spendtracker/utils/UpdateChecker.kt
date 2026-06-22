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

    /** Details of an available update. [version] is the normalized release version (no leading "v"). */
    data class UpdateInfo(val version: String, val downloadUrl: String)

    suspend fun checkForUpdates(currentVersion: String): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val latestVersion = normalizeVersion(json.getString("tag_name"))

                    if (isNewerVersion(currentVersion, latestVersion)) {
                        // Try to find a direct APK link in assets
                        val assets = json.optJSONArray("assets")
                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val name = asset.getString("name")
                                if (name.endsWith(".apk")) {
                                    return@withContext UpdateInfo(latestVersion, asset.getString("browser_download_url"))
                                }
                            }
                        }
                        // Fallback to the release page
                        return@withContext UpdateInfo(latestVersion, json.getString("html_url"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates: ${e.message}")
            }
            null
        }
    }

    /** Strips a leading "v"/"V" and any non version characters so "v1.0.5" and "1.0.5" compare equal. */
    private fun normalizeVersion(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V").trim()

    /**
     * Compares dotted version strings component-wise, padding the shorter one with zeros so that
     * "1.0.5" and "1.0.5.0" are treated as EQUAL (not "newer"). Returns true only when [latest] is
     * strictly greater than [current].
     */
    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").map { it.trim().toIntOrNull() ?: 0 }
        val latestParts = latest.split(".").map { it.trim().toIntOrNull() ?: 0 }
        val size = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until size) {
            val c = currentParts.getOrElse(i) { 0 }
            val l = latestParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    fun openUpdateUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
}
