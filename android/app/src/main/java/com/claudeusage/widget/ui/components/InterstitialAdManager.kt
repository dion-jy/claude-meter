package com.claudeusage.widget.ui.components

import android.app.Activity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-8064366969131187/1028803027"

class InterstitialAdManager {

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    fun load(activity: Activity) {
        if (interstitialAd != null || isLoading) return
        isLoading = true

        InterstitialAd.load(
            activity,
            INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                }
            }
        )
    }

    fun showThen(activity: Activity, onComplete: () -> Unit) {
        val ad = interstitialAd
        if (ad == null) {
            onComplete()
            load(activity) // preload for next time
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                onComplete()
                load(activity) // preload for next time
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                interstitialAd = null
                onComplete()
                load(activity)
            }
        }

        ad.show(activity)
    }
}
