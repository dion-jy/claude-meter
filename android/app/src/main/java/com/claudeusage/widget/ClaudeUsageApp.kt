package com.claudeusage.widget

import android.app.Application
import com.google.android.gms.ads.MobileAds

class ClaudeUsageApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler(this).install()
        MobileAds.initialize(this)
    }
}
