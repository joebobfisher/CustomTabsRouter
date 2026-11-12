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
                blockAndGoHome()
                return
            }
        }

        // 2. Fallback check on the active window hierarchy in case Chrome resumes an already-open tab
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return
        if (root.packageName?.toString() == CHROME_PACKAGE) {
            for (viewId in TABBED_CHROME_VIEW_IDS) {
                val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
                if (!nodes.isNullOrEmpty()) {
                    blockAndGoHome()
                    return
                }
            }
        }
    }

    private fun blockAndGoHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        val now = System.currentTimeMillis()
        if (now - lastToastTime > 1500) {
            lastToastTime = now
            Toast.makeText(this, "Standalone Chrome is disabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInterrupt() {
        // No-op
    }
}
