package com.nlevidev.customtabsrouter

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent

object CustomTabHelper {

    private const val CHROME_PACKAGE = "com.android.chrome"

    private val BLOCKED_HOSTS = setOf(
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

    fun isBlocked(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return BLOCKED_HOSTS.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
    }

    fun openUrl(context: Context, uri: Uri) {
        if (isBlocked(uri)) {
            Toast.makeText(context, "Blocked domain", Toast.LENGTH_SHORT).show()
            return
        }

        val customTabsIntent = CustomTabsIntent.Builder()
            .setUrlBarHidingEnabled(true)
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
