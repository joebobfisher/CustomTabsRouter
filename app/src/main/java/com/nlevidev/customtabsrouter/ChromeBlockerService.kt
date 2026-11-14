package com.nlevidev.customtabsrouter

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
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
            "com.android.chrome:id/location_bar_status_text"
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastToastTime = 0L
    private var currentBannerView: TextView? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != CHROME_PACKAGE) return

        val className = event.className?.toString() ?: ""

        // 1. Direct check on the Activity class name for TYPE_WINDOW_STATE_CHANGED
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (className in BLOCKED_ACTIVITY_CLASSES) {
                blockAndGoHome("Closed: Standalone Chrome is disabled")
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
                    blockAndGoHome("Closed: Standalone Chrome is disabled")
                    return
                }
            }

            // 2b. Check if a Custom Tab has navigated to a blocked domain (e.g. via Google Search results or redirects)
            for (viewId in URL_BAR_VIEW_IDS) {
                val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        val text = node.text?.toString() ?: continue
                        val matchedDomain = CustomTabHelper.getBlockedDomain(text)
                        if (matchedDomain != null) {
                            blockAndGoHome("Blocked domain: $matchedDomain")
                            return
                        }
                    }
                }
            }
        }
    }

    private fun blockAndGoHome(message: String) {
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)
        val now = System.currentTimeMillis()
        if (now - lastToastTime > 1500) {
            lastToastTime = now
            showReasonNotification(message)
        }
    }

    private fun showReasonNotification(message: String) {
        mainHandler.post {
            // 1. Show an Accessibility Overlay pill (always visible on Android 12+ even from background services)
            try {
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                currentBannerView?.let {
                    try {
                        wm.removeView(it)
                    } catch (_: Exception) {}
                    currentBannerView = null
                }

                val density = resources.displayMetrics.density
                val banner = TextView(this).apply {
                    text = message
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    gravity = Gravity.CENTER
                    val hPad = (20 * density).toInt()
                    val vPad = (12 * density).toInt()
                    setPadding(hPad, vPad, hPad, vPad)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 24 * density
                        setColor(Color.parseColor("#E6222222"))
                    }
                    elevation = 8 * density
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = (96 * density).toInt()
                }

                wm.addView(banner, params)
                currentBannerView = banner

                mainHandler.postDelayed({
                    if (currentBannerView === banner) {
                        try {
                            wm.removeView(banner)
                        } catch (_: Exception) {}
                        currentBannerView = null
                    }
                }, 3000L)
            } catch (_: Exception) {
                // Fallback to standard Toast if overlay fails
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onInterrupt() {
        // No-op
    }
}
