package com.aesthetic.gym.ui.galaxy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.domain.planet.PlanetStage
import com.aesthetic.gym.social.AccountManager
import com.aesthetic.gym.social.GalaxyPlanet
import com.aesthetic.gym.ui.components.PrimaryButton
import com.aesthetic.gym.ui.galaxy.gl.GalaxyGLView
import com.aesthetic.gym.ui.nav.Routes
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Gold
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.ui.theme.Violet

/**
 * La galaxia global en 3D: cada mundo de la comunidad orbita en la espiral. Arrastra para
 * girar, pellizca para acercarte y toca un planeta para volar hasta él.
 */
@Composable
fun GalaxyMapScreen(navController: NavController) {
    val repo = rememberRepository()
    val context = LocalContext.current
    val vm: GalaxyViewModel = viewModel(factory = GalaxyViewModel.factory(repo, context))
    val global by vm.global.collectAsState()
    var selected by remember { mutableStateOf<GalaxyPlanet?>(null) }
    val glView = remember { arrayOfNulls<GalaxyGLView>(1) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val ownUsername = AccountManager.currentAccount()?.username

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B10))) {
        when (val g = global) {
            is GlobalGalaxyUi.Ready -> {
                AndroidView(
                    factory = { ctx ->
                        GalaxyGLView(ctx).also { v ->
                            glView[0] = v
                            v.onSelected = { p ->
                                selected = p
                                if (p == null) v.renderer.resetCamera()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { it.renderer.setPlanets(g.planets) }
                )
            }
            GlobalGalaxyUi.Loading ->
                CenteredNote("Explorando el cosmos…")
            GlobalGalaxyUi.Error ->
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No se pudo cargar la galaxia.", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Reintentar", color = Violet, fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp, modifier = Modifier.clickable { vm.refreshGlobal() }
                    )
                }
            GlobalGalaxyUi.NeedsLogin ->
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Crea una cuenta para explorar los mundos de la comunidad.",
                        color = TextSecondary, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = "Crear cuenta",
                        onClick = { navController.navigate(Routes.auth("register")) }
                    )
                }
        }

        // Barra superior flotante.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.clip(CircleShape).background(SurfaceVariant.copy(alpha = 0.85f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Text("Galaxia global", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        if (global is GlobalGalaxyUi.Ready && selected == null) {
            Text(
                "Arrastra para orbitar · pellizca para acercarte · toca un mundo",
                color = TextMuted, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
            )
        }

        // Panel del planeta seleccionado.
        selected?.let { p ->
            val stage = PlanetStage.entries[p.stage.coerceIn(0, PlanetStage.entries.size - 1)]
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(14.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Surface.copy(alpha = 0.94f))
                    .border(1.dp, Outline, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            p.displayName, color = Color.White,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp
                        )
                        Text("@${p.username}", color = Violet, fontSize = 12.sp)
                    }
                    if (p.username == ownUsername) {
                        Text(
                            "TU MUNDO", color = Gold, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Planeta nº ${p.planetIndex + 1} · ${stage.displayName}" +
                        if (p.planetIndex > 0) " · ${p.planetIndex} completado${if (p.planetIndex == 1) "" else "s"}" else "",
                    color = TextSecondary, fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Volver a la galaxia",
                    color = Violet, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        selected = null
                        glView[0]?.renderer?.resetCamera()
                    }
                )
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView[0]?.onResume()
                Lifecycle.Event.ON_PAUSE -> glView[0]?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CenteredNote(text: String) {
    Text(
        text, color = TextMuted, fontSize = 13.sp,
        modifier = Modifier.align(Alignment.Center)
    )
}
