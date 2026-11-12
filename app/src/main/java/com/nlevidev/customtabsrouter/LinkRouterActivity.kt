package com.nlevidev.customtabsrouter

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class LinkRouterActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
        finish()
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") {
            CustomTabHelper.openUrl(this, uri)
        }
    }
}
