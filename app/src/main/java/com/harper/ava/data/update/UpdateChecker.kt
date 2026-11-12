package com.harper.ava.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import com.harper.ava.core.util.UPDATE_RELEASES_API_URL
import com.harper.ava.core.util.isNewerVersion
import com.harper.ava.core.util.parseJsonStringField
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of one check against the GitHub releases API. */
sealed interface UpdateCheckResult {
    /** A newer release exists: [tag] is the raw GitHub tag (e.g. `v0.7.2`). */
    data class Available(val tag: String, val versionName: String, val htmlUrl: String) :
        UpdateCheckResult

    /** The installed version is the latest (or newer than) the release. */
    data class UpToDate(val currentVersion: String) : UpdateCheckResult

    /** Offline, timeout, non-200 response, or an unparsable payload. Silent by design. */
    data object Error : UpdateCheckResult
}

/**
 * One-shot GitHub release check (0.7.2).
 *
 * Plain `HttpURLConnection` GET to `api.github.com/repos/amirlwf/ava/
 * releases/latest` — no user data is sent, only the User-Agent header.
 * Never throws: every failure (offline, timeout, HTTP error, bad JSON)
 * comes back as [UpdateCheckResult.Error] and the caller stays silent.
 * Never runs on the calling thread: [check] switches to Dispatchers.IO
 * itself, so the caller never blocks startup or the UI.
 */
class UpdateChecker(private val context: Context) {

    /** True when the device currently has a validated internet connection. */
    fun isOnline(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * The installed version, from PackageManager — the same source the
     * About screen shows. There is no BuildConfig in the manual build
     * (aapt2 + kotlinc, no AGP), so PackageManager is the one version
     * source of truth; never a hard-coded string.
     */
    fun currentVersionName(): String {
        return runCatching {
            val packageManager = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName
        }.getOrNull() ?: ""
    }

    /**
     * Fetches the latest release and compares its tag with the installed
     * version. Returns [UpdateCheckResult.Error] when offline, on any
     * network/HTTP/parse failure, or when the installed version cannot
     * be read — the caller treats all of those as "say nothing".
     */
    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val current = currentVersionName()
        if (current.isBlank()) return@withContext UpdateCheckResult.Error
        val body = runCatching { fetchLatestReleaseJson() }.getOrNull()
            ?: return@withContext UpdateCheckResult.Error
        val tag = parseJsonStringField(body, "tag_name")
            ?: return@withContext UpdateCheckResult.Error
        val htmlUrl = parseJsonStringField(body, "html_url").orEmpty()
        if (isNewerVersion(tag, current)) {
            UpdateCheckResult.Available(tag, tag.trim().removePrefix("v").removePrefix("V"), htmlUrl)
        } else {
            UpdateCheckResult.UpToDate(current)
        }
    }

    /** One GET, 10 s connect/read timeouts, `Accept: application/vnd.github+json`. */
    private fun fetchLatestReleaseJson(): String {
        val connection = (URL(UPDATE_RELEASES_API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Ava/${currentVersionName()} (Android)")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return ""
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** Opens the release page in the browser (Download button). */
        fun openReleasePage(context: Context, htmlUrl: String) {
            val url = htmlUrl.ifBlank { com.harper.ava.core.util.UPDATE_RELEASE_PAGE_URL }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }
}
