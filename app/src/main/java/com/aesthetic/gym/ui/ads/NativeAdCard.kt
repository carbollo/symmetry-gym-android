package com.aesthetic.gym.ui.ads

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aesthetic.gym.BuildConfig
import com.aesthetic.gym.R
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Surface
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Google's public test unit. Used in debug builds on purpose: loading the real unit while
 * developing is invalid traffic, and Google suspends AdMob accounts for it.
 */
private const val TEST_NATIVE_UNIT = "ca-app-pub-3940256099942544/2247696110"
private const val LIVE_NATIVE_UNIT = "ca-app-pub-8070709640197888/4667136763"

private val nativeAdUnitId: String
    get() = if (BuildConfig.DEBUG) TEST_NATIVE_UNIT else LIVE_NATIVE_UNIT

/**
 * A native advanced ad rendered with the app's own look.
 * Draws nothing at all until an ad actually loads, so a failure (no network, no fill)
 * leaves no gap and never blocks the screen.
 */
@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var ad by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(Unit) {
        var loaded: NativeAd? = null
        val loader = AdLoader.Builder(context.applicationContext, nativeAdUnitId)
            .forNativeAd { native ->
                loaded?.destroy()
                loaded = native
                ad = native
            }
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()
        runCatching { loader.loadAd(AdRequest.Builder().build()) }
        onDispose {
            ad = null
            loaded?.destroy()
        }
    }

    val current = ad ?: return
    AndroidView(
        modifier = modifier.fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .background(Surface)
            .border(1.dp, Outline, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)),
        factory = { ctx ->
            LayoutInflater.from(ctx).inflate(R.layout.native_ad_card, null) as NativeAdView
        },
        update = { view -> view.bind(current) }
    )
}

/** Wires each asset to its view and hides the ones this particular ad didn't provide. */
private fun NativeAdView.bind(nativeAd: NativeAd) {
    val headline = findViewById<TextView>(R.id.ad_headline)
    val body = findViewById<TextView>(R.id.ad_body)
    val icon = findViewById<ImageView>(R.id.ad_app_icon)
    val cta = findViewById<Button>(R.id.ad_call_to_action)
    val advertiser = findViewById<TextView>(R.id.ad_advertiser)
    val media = findViewById<MediaView>(R.id.ad_media)

    headline.text = nativeAd.headline
    headlineView = headline

    body.text = nativeAd.body
    body.visibility = if (nativeAd.body.isNullOrBlank()) View.GONE else View.VISIBLE
    bodyView = body

    val iconAsset = nativeAd.icon
    if (iconAsset == null) {
        icon.visibility = View.GONE
    } else {
        icon.setImageDrawable(iconAsset.drawable)
        icon.visibility = View.VISIBLE
        iconView = icon
    }

    cta.text = nativeAd.callToAction
    cta.visibility = if (nativeAd.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    callToActionView = cta

    advertiser.text = nativeAd.advertiser
    advertiser.visibility = if (nativeAd.advertiser.isNullOrBlank()) View.GONE else View.VISIBLE
    advertiserView = advertiser

    if (nativeAd.mediaContent == null) {
        media.visibility = View.GONE
    } else {
        media.visibility = View.VISIBLE
        media.mediaContent = nativeAd.mediaContent
        mediaView = media
    }

    // Must be last: it is what registers every view above for clicks and impressions.
    setNativeAd(nativeAd)
}
