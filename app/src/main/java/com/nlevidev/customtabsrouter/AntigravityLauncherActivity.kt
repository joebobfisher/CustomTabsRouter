package com.nlevidev.customtabsrouter

import android.app.Activity
import android.net.Uri
import android.os.Bundle

class AntigravityLauncherActivity : Activity() {

    companion object {
        // Configurable Antigravity endpoint/session URL
        private const val ANTIGRAVITY_URL = "https://antigravity.google.com/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CustomTabHelper.openUrl(this, Uri.parse(ANTIGRAVITY_URL))
        finish()
    }
}
