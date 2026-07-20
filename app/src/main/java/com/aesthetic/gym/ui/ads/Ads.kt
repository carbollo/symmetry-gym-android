package com.aesthetic.gym.ui.ads

import android.content.Context
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks whether the ads SDK has finished starting up.
 *
 * This matters more than it looks: building an AdLoader before initialization has completed
 * blocks on an internal lock held by the initializing thread. Doing that from the main thread
 * (which is where composition runs) freezes the UI until Play Services is ready — on a cold
 * device that is tens of seconds staring at the splash screen.
 */
object Ads {

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    /** Safe to call from a background thread; the callback arrives on the main thread. */
    fun initialize(context: Context) {
        runCatching {
            MobileAds.initialize(context.applicationContext) { _ready.value = true }
        }.onFailure {
            // Without ads the app still works: never let this take the app down.
            _ready.value = false
        }
    }
}
