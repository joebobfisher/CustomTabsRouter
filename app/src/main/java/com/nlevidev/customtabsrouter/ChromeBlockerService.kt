package com.nlevidev.customtabsrouter

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class ChromeBlockerService : AccessibilityService() {

    companion object {
        private const val CHROME_PACKAGE = "com.android.chrome"

        private val BLOCKED_ACTIVITY_CLASSES = setOf(
            "com.google.android.apps.chrome.Main",
            "org.chromium.chrome.browser.ChromeTabbedActivity",
            "org.chromium.chrome.browser.ChromeTabbedActivity2",
            "org.chromium.chrome.browser.incognito.IncognitoTabLauncher",
            "org.chromium.chrome.browser.searchwidget.SearchActivity"
        )

        // Full tabbed Chrome UI view IDs (omnibox/tab switcher) that do not exist in Custom Tabs or WebAPKs
        private val TABBED_CHROME_VIEW_IDS = listOf(
            "com.android.chrome:id/tab_switcher_button",
            "com.android.chrome:id/home_button",
            "com.android.chrome:id/search_box_text"
        )

        // Custom Tab & Chrome URL bar view IDs displaying the current domain/URL
        private val URL_BAR_VIEW_IDS = listOf(
            "com.android.chrome:id/url_bar",
            "com.android.chrome:id/location_bar_status_text",
            "com.android.chrome:id/title_bar"
        )
    }

    private var lastToastTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != CHROME_PACKAGE) return

        val className = event.className?.toString() ?: ""

        // 1. Direct check on the Activity class name for TYPE_WINDOW_STATE_CHANGED
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (className in BLOCKED_ACTIVITY_CLASSES) {
                blockAndGoHome("Standalone Chrome is disabled")
                return
            }
        }

        // 2. Inspect active window hierarchy for standalone Chrome OR blocked domains inside a Custom Tab
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return
        if (root.packageName?.toString() == CHROME_PACKAGE) {
            // 2a. Check if full tabbed Chrome UI is showing
            for (viewId in TABBED_CHROME_VIEW_IDS) {
                val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
                if (!nodes.isNullOrEmpty()) {
                    blockAndGoHome("Standalone Chrome is disabled")
                    return
                }
            }

            // 2b. Check if a Custom Tab has navigated to a blocked domain (e.g. via Google Search results or redirects)
            for (viewId in URL_BAR_VIEW_IDS) {
                val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        val text = node.text?.toString() ?: continue
                        if (CustomTabHelper.isBlockedHost(text)) {
                            blockAndGoHome("Blocked domain")
                            return
                        }
                    }
                }
            }
        }
    }

    private fun blockAndGoHome(message: String) {
        // First navigate back to close or pop the blocked page from the Custom Tab, then go Home
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)
        val now = System.currentTimeMillis()
        if (now - lastToastTime > 1500) {
            lastToastTime = now
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInterrupt() {
        // No-op
    }
}
