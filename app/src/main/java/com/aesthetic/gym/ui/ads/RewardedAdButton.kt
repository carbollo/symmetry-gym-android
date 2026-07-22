package com.aesthetic.gym.ui.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.aesthetic.gym.ui.components.PrimaryButton

/** Sube por los ContextWrapper hasta la Activity: LocalContext puede venir envuelto en Compose. */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Botón para ver, de forma VOLUNTARIA, un anuncio intersticial recompensado con verificación por
 * servidor (SSV).
 *
 * - Solo se habilita cuando hay un anuncio precargado (el usuario nunca pulsa "en vacío").
 * - Mientras el servidor confirma, muestra "Verificando…" y queda deshabilitado.
 * - [onReward] se ejecuta ÚNICAMENTE cuando el servidor de AdMob confirma el visionado.
 * - [onProblem] recibe un mensaje si no se pudo mostrar o no se pudo verificar (para un aviso).
 *
 * Es el punto de entrada listo para colocar en la pantalla donde se ofrezca la recompensa.
 */
@Composable
fun WatchAdForRewardButton(
    text: String,
    onReward: () -> Unit,
    modifier: Modifier = Modifier,
    verifyingText: String = "Verificando…",
    onProblem: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val loaded by RewardedAds.loaded.collectAsState()
    val phase by RewardedAds.phase.collectAsState()
    val busy = phase != RewardPhase.Idle

    PrimaryButton(
        text = if (busy) verifyingText else text,
        icon = Icons.Filled.CardGiftcard,
        enabled = loaded && !busy,
        modifier = modifier,
        onClick = {
            val activity = context.findActivity()
            if (activity == null) {
                onProblem("no se pudo abrir el anuncio")
                return@PrimaryButton
            }
            RewardedAds.show(activity) { outcome ->
                when (outcome) {
                    RewardOutcome.Verified -> onReward()
                    RewardOutcome.NotEarned -> Unit // cerró antes de completarlo: sin aviso
                    is RewardOutcome.Unverified -> onProblem("No pudimos verificar el anuncio. Inténtalo de nuevo.")
                    is RewardOutcome.NotShown -> onProblem("El anuncio no está disponible ahora mismo.")
                }
            }
        }
    )
}
