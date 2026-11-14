package com.nlevidev.customtabsrouter

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent

object CustomTabHelper {

    private const val CHROME_PACKAGE = "com.android.chrome"

    val BLOCKED_HOSTS = setOf(
        "reddit.com",
        "www.reddit.com",
        "old.reddit.com",
        "twitter.com",
        "x.com",
        "facebook.com",
        "instagram.com",
        "tiktok.com",
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "youtu.be",
        "ycombinator.com",
        "news.google.com"
    )

    private val SEARCH_ENGINE_HOSTS = setOf(
        "google.com",
        "bing.com",
        "duckduckgo.com",
        "search.yahoo.com",
        "brave.com",
        "ecosia.org",
        "startpage.com"
    )

    fun isBlockedHost(rawText: String): Boolean {
        val cleaned = rawText.trim().lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
        val host = cleaned.substringBefore('/').substringBefore(':').substringBefore('?')
        if (host.isEmpty()) return false
        return BLOCKED_HOSTS.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
    }

    fun isSearchUrl(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path?.lowercase() ?: ""
        val isSearchHost = SEARCH_ENGINE_HOSTS.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
        if (!isSearchHost) return false

        // Block search queries on search engines (e.g. google.com/search?q=...),
        // while still allowing non-search Google sub-paths like accounts.google.com or maps.
        if (path == "/search" || path.startsWith("/search/") || uri.getQueryParameter("q") != null) {
            // Allow google.com/url?q=... redirect wrappers only if the target URL itself isn't blocked
            if (path == "/url") {
                val target = uri.getQueryParameter("q") ?: uri.getQueryParameter("url") ?: return true
                val targetUri = Uri.parse(target)
                return isBlocked(targetUri)
            }
            return true
        }
        return false
    }

    fun isBlocked(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        if (BLOCKED_HOSTS.any { domain -> host == domain || host.endsWith(".$domain") }) {
            return true
        }
        // Also check if a redirect wrapper (e.g. google.com/url?q=https://youtube.com/...) points to a blocked host
        for (param in listOf("q", "url", "u")) {
            val target = uri.getQueryParameter(param)
            if (!target.isNullOrEmpty() && (target.startsWith("http://") || target.startsWith("https://"))) {
                val targetHost = Uri.parse(target).host?.lowercase()
                if (targetHost != null && BLOCKED_HOSTS.any { domain ->
                        targetHost == domain || targetHost.endsWith(".$domain")
                    }) {
                    return true
                }
            }
        }
        return false
    }

    fun openUrl(context: Context, uri: Uri) {
        if (isBlocked(uri)) {
            Toast.makeText(context, "Blocked domain", Toast.LENGTH_SHORT).show()
            return
        }

        val customTabsIntent = CustomTabsIntent.Builder()
            .setUrlBarHidingEnabled(false)
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setOpenInBrowserButtonState(CustomTabsIntent.OPEN_IN_BROWSER_STATE_OFF)
            .build()

        // Explicit package targeting is critical to avoid infinite intent loops
        // when this app is set as the system default browser.
        customTabsIntent.intent.setPackage(CHROME_PACKAGE)

        try {
            customTabsIntent.launchUrl(context, uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open link in Chrome Custom Tab", Toast.LENGTH_SHORT).show()
        }
    }
}
