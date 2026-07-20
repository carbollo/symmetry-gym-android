package com.aesthetic.gym.ui.ads

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.aesthetic.gym.BuildConfig
import com.aesthetic.gym.R
import com.aesthetic.gym.ui.theme.Gold
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.TextMuted
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Google's public test unit for native advanced ads. It always fills, which makes it the
 * only reliable way to tell "my integration is broken" apart from "there is no ad for me yet".
 */
const val TEST_NATIVE_UNIT = "ca-app-pub-3940256099942544/2247696110"
const val LIVE_NATIVE_UNIT = "ca-app-pub-8070709640197888/4667136763"

/**
 * A native advanced ad rendered with the app's own look.
 *
 * @param testMode forces Google's test unit, so the wiring can be verified on a real phone.
 * @param showDiagnostics prints the SDK's own error on screen instead of leaving a blank gap.
 */
@Composable
fun NativeAdCard(
    modifier: Modifier = Modifier,
    testMode: Boolean = false,
    showDiagnostics: Boolean = false
) {
    val context = LocalContext.current
    val ready by Ads.ready.collectAsState()
    var ad by remember { mutableStateOf<NativeAd?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val unitId = if (testMode || BuildConfig.DEBUG) TEST_NATIVE_UNIT else LIVE_NATIVE_UNIT
    val holder = remember { AdHolder() }

    DisposableEffect(Unit) {
        onDispose {
            holder.disposed = true
            holder.ad?.destroy()
            holder.ad = null
        }
    }

    // Waits for the SDK to be up before touching AdLoader. Building it while Play Services
    // is still starting blocks on an internal lock, and doing that on the main thread
    // freezes the app on the splash screen until initialization finishes.
    LaunchedEffect(unitId, ready) {
        if (!ready) {
            error = "esperando a que arranque el SDK de anuncios…"
            return@LaunchedEffect
        }
        val loader = AdLoader.Builder(context.applicationContext, unitId)
            .forNativeAd { native ->
                // The callback can arrive after the screen is gone; leaking it would
                // hold the ad forever and Google logs it as an unshown impression.
                if (holder.disposed) {
                    native.destroy()
                } else {
                    holder.ad?.destroy()
                    holder.ad = native
                    ad = native
                    error = null
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    error = "código ${adError.code}: ${adError.message}"
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    // Sin restringir la relacion de aspecto: limitarla descarta creatividades
                    // y en una cuenta nueva eso es relleno que se pierde.
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_ANY)
                    .build()
            )
            .build()
        runCatching { loader.loadAd(AdRequest.Builder().build()) }
            .onFailure { error = "no se pudo pedir el anuncio: ${it.message}" }
    }

    val current = ad
    if (current != null) {
        AndroidView(
            modifier = modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .border(1.dp, Outline, RoundedCornerShape(20.dp)),
            factory = { ctx ->
                LayoutInflater.from(ctx).inflate(R.layout.native_ad_card, null) as NativeAdView
            },
            update = { view -> view.bind(current) }
        )
        return
    }

    // No ad: stay invisible for real users, but say why when diagnosing.
    if (showDiagnostics) {
        Column(
            modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
                .border(1.dp, Outline, RoundedCornerShape(20.dp)).padding(16.dp)
        ) {
            Text(
                "DIAGNÓSTICO DE ANUNCIOS", color = Gold, fontSize = 10.sp,
                fontWeight = FontWeight.Black, letterSpacing = 1.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Unidad: " + (if (unitId == TEST_NATIVE_UNIT) "de prueba de Google" else "la tuya real"),
                color = TextMuted, fontSize = 11.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                error ?: "Pidiendo anuncio…",
                color = if (error == null) TextMuted else Gold,
                fontSize = 11.sp, lineHeight = 15.sp
            )
        }
    }
}

/** Survives recomposition so a late callback can still be cleaned up. */
private class AdHolder {
    var ad: NativeAd? = null
    var disposed = false
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

    // Some ads only ship the icon as a URI, with no drawable: showing an empty 38dp hole
    // looks broken, so the slot is dropped instead.
    val iconDrawable = nativeAd.icon?.drawable
    if (iconDrawable == null) {
        icon.visibility = View.GONE
    } else {
        icon.setImageDrawable(iconDrawable)
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
