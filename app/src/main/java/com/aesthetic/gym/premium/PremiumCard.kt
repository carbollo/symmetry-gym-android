package com.aesthetic.gym.premium

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aesthetic.gym.social.AccountManager
import com.aesthetic.gym.social.CheckoutResult
import com.aesthetic.gym.social.SocialApi
import com.aesthetic.gym.ui.ads.WatchAdForRewardButton
import com.aesthetic.gym.ui.theme.Gold
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tarjeta de premium en Inicio. El premium está activo si CUALQUIERA de los dos lo está:
 * - por anuncios (gratis, por dispositivo — [Premium]),
 * - de pago (Whop, atado a la cuenta — [AccountManager.premiumUntil]).
 */
@Composable
fun PremiumCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val devicePremium by Premium.premiumUntil.collectAsState()
    val accountPremium by AccountManager.premiumUntil.collectAsState()
    val premiumUntil = maxOf(devicePremium, accountPremium)
    val active = premiumUntil > System.currentTimeMillis()
    var message by remember { mutableStateOf<String?>(null) }

    // Al volver a la app (p.ej. tras pagar en el navegador) refrescamos ambos premios.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                AccountManager.refreshPremium()
                Premium.refresh(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun buyPremium() {
        val token = AccountManager.currentToken()
        if (token == null) {
            message = "Inicia sesión desde el Perfil para hacerte Premium."
            return
        }
        message = "Abriendo el pago…"
        scope.launch {
            val result = withContext(Dispatchers.IO) { SocialApi.premiumCheckout(token) }
            when (result) {
                is CheckoutResult.Success -> {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.url)))
                    }.onFailure { message = "No se pudo abrir el navegador." }
                    if (message == "Abriendo el pago…") message = null
                }
                CheckoutResult.NotConfigured -> message = "El pago con Whop aún no está disponible."
                CheckoutResult.NeedsLogin -> message = "Inicia sesión desde el Perfil para hacerte Premium."
                CheckoutResult.NetworkError -> message = "No se pudo abrir el pago. Inténtalo de nuevo."
            }
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .border(1.dp, if (active) Gold else Outline, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Gold.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.WorkspacePremium, null, tint = Gold, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (active) "Premium activo" else "Premium",
                    color = if (active) Gold else TextSecondary,
                    fontWeight = FontWeight.Black, fontSize = 14.sp
                )
                Text(
                    if (active) "Sin anuncios · quedan ${remaining(premiumUntil)}"
                    else "Sin anuncios y contenido exclusivo",
                    color = TextMuted, fontSize = 11.sp
                )
            }
        }

        // Compra de pago (Whop). Se muestra siempre; si ya eres premium, sirve para renovar/gestionar.
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Gold)
                .clickable { buyPremium() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (active) "Gestionar Premium" else "Hazte Premium",
                color = Color(0xFF0B0B10), fontWeight = FontWeight.Black, fontSize = 14.sp
            )
        }

        // Camino gratis: ver un anuncio suma 1 día (por dispositivo).
        Spacer(Modifier.height(10.dp))
        WatchAdForRewardButton(
            text = if (active) "Sumar otro día (gratis)" else "Ver anuncio · 1 día gratis",
            modifier = Modifier.fillMaxWidth(),
            onReward = {
                Premium.refresh(context)
                message = "¡Recompensa verificada! Activando tu premium…"
            },
            onProblem = { message = it }
        )

        message?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Tiempo restante en formato corto: "2d 3h", "5h 12m" o "40m". */
private fun remaining(premiumUntil: Long, now: Long = System.currentTimeMillis()): String {
    val totalMin = ((premiumUntil - now).coerceAtLeast(0L)) / 60_000
    val days = totalMin / (60 * 24)
    val hours = (totalMin / 60) % 24
    val mins = totalMin % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${mins}m"
        else -> "${mins}m"
    }
}
