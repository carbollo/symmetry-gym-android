package com.aesthetic.gym.ui.ads

import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Ads SDK state, plus a small cache of the last native ad so that returning to Home does not
 * request a brand-new ad every single time. A fresh request per visit over-serves the network
 * for no benefit; reusing a recent ad keeps a cap on frequency.
 *
 * Owning the ad here (not in the composable) also fixes the lifetime: the cache destroys the
 * previous ad when it is replaced, so nothing leaks.
 */
object Ads {

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    /** How long a loaded native ad is reused before a new one is requested. */
    const val CACHE_TTL_MS = 5 * 60 * 1000L

    private var cachedAd: NativeAd? = null
    private var cachedAt: Long = 0L

    /** Whether the ads SDK started up (building an AdLoader before that blocks on a lock). */
    fun initialize(context: Context) {
        runCatching {
            MobileAds.initialize(context.applicationContext) { _ready.value = true }
        }.onFailure {
            // Without ads the app still works: never let this take the app down.
            _ready.value = false
        }
    }

    /** A cached native ad if it is still fresh, or null (caller should then load a new one). */
    fun cachedOrNull(nowMs: Long): NativeAd? =
        cachedAd?.takeIf { nowMs - cachedAt < CACHE_TTL_MS }

    /** Stores a freshly loaded ad, destroying whatever was cached before. */
    fun cache(ad: NativeAd, nowMs: Long) {
        if (cachedAd !== ad) cachedAd?.destroy()
        cachedAd = ad
        cachedAt = nowMs
    }
}
