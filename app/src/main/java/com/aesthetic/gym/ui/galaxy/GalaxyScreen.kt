package com.aesthetic.gym.ui.galaxy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.domain.planet.PlanetEngine
import com.aesthetic.gym.domain.planet.PlanetStage
import com.aesthetic.gym.ui.components.PrimaryButton
import com.aesthetic.gym.ui.galaxy.gl.Planet3D
import com.aesthetic.gym.ui.nav.Routes
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Gold
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.ui.theme.Violet
import kotlin.random.Random

/**
 * Tu planeta, tu galaxia privada y la galaxia global. Todo se dibuja sobre un campo de
 * estrellas fijo (semilla constante: las estrellas no "bailan" entre visitas).
 */
@Composable
fun GalaxyScreen(navController: NavController) {
    val repo = rememberRepository()
    val context = LocalContext.current
    val vm: GalaxyViewModel = viewModel(factory = GalaxyViewModel.factory(repo, context))
    val planet by vm.planet.collectAsState()
    val global by vm.global.collectAsState()

    Box(Modifier.fillMaxSize()) {
        StarField(Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 10.dp, bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.clip(CircleShape).background(SurfaceVariant)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Text("Tu galaxia", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }

            Spacer(Modifier.height(18.dp))

            // ---------- PLANETA ACTUAL (3D real: arrastra para girarlo) ----------
            Planet3D(
                seed = planet.seed,
                stage = planet.stage,
                progressQ = planet.progressQ,
                species = planet.species,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(24.dp))
            )

            Spacer(Modifier.height(12.dp))
            Text(
                planet.stage.displayName.uppercase(),
                color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                planet.stage.hint,
                color = TextSecondary, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(14.dp))

            // Progreso de etapa + estimación honesta en entrenos, nunca cuentas atrás.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Surface)
                    .border(1.dp, Outline, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "PLANETA Nº ${planet.planetIndex + 1}",
                        color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp, modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${planet.xpInPlanet} / ${planet.planetCost} pts",
                        color = TextSecondary, fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { planet.stageFraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = Violet, trackColor = SurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                val next = planet.nextStage
                Text(
                    when {
                        !planet.hasHistory -> "Tu primer entreno despertará este mundo"
                        next == null -> "Un mundo nuevo te espera al completarlo"
                        else -> "A ~${planet.daysToNextStage} entrenos de ${next.displayName}"
                    },
                    color = TextSecondary, fontSize = 12.sp
                )
                if (planet.lastDayXp > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Último entreno: +${planet.lastDayXp} pts de vida",
                        color = Gold, fontSize = 11.sp
                    )
                }
                if (planet.species > 1) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${planet.species} especies · entrenar más grupos musculares trae más vida",
                        color = TextMuted, fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            // ---------- GALAXIA PRIVADA ----------
            SectionTitle("MI GALAXIA")
            Spacer(Modifier.height(10.dp))
            if (planet.completedSeeds.isEmpty()) {
                Text(
                    "Cuando completes tu primer planeta, orbitará aquí para siempre.",
                    color = TextMuted, fontSize = 12.sp
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    planet.completedSeeds.forEachIndexed { i, seed ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // species FIJO: un mundo sellado no cambia de aspecto nunca,
                            // aunque tu diversidad muscular siga creciendo después.
                            PlanetCanvas(
                                seed = seed, stage = PlanetStage.LUCES, progressQ = 3,
                                species = PlanetEngine.MAX_SPECIES,
                                completed = true, animated = false, mini = true,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("Nº ${i + 1}", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // ---------- GALAXIA GLOBAL ----------
            SectionTitle("GALAXIA GLOBAL")
            Spacer(Modifier.height(10.dp))
            when (val g = global) {
                GlobalGalaxyUi.NeedsLogin -> {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
                            .border(1.dp, Outline, RoundedCornerShape(20.dp)).padding(16.dp)
                    ) {
                        Text(
                            "Crea una cuenta para ver los mundos de la comunidad y publicar el tuyo.",
                            color = TextSecondary, fontSize = 12.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(
                            text = "Crear cuenta",
                            onClick = { navController.navigate(Routes.auth("register")) }
                        )
                    }
                }
                GlobalGalaxyUi.Loading -> Text("Explorando el cosmos…", color = TextMuted, fontSize = 12.sp)
                GlobalGalaxyUi.Error -> {
                    Column {
                        Text("No se pudo cargar la galaxia.", color = TextMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Reintentar", color = Violet, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { vm.refreshGlobal() }
                        )
                    }
                }
                is GlobalGalaxyUi.Ready -> {
                    if (g.planets.isEmpty()) {
                        Text(
                            "Aún no hay mundos publicados. El tuyo puede ser el primero.",
                            color = TextMuted, fontSize = 12.sp
                        )
                    } else {
                        // Entrada al mapa 3D navegable con todos los mundos.
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                                .background(Surface)
                                .border(1.dp, Outline, RoundedCornerShape(20.dp))
                                .clickable { navController.navigate(Routes.GALAXY_MAP) }
                                .padding(16.dp)
                        ) {
                            Text(
                                "EXPLORAR LA GALAXIA EN 3D",
                                color = Violet, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            val n = g.planets.size
                            Text(
                                (if (n == 1) "1 mundo ha crecido" else "$n mundos han crecido") +
                                    " en los últimos 30 días. Orbita entre ellos y tócalos para visitarlos.",
                                color = TextSecondary, fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, color = TextMuted, fontSize = 11.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 2.sp
    )
}

/** Fondo estrellado fijo (misma semilla siempre: el cielo no cambia entre visitas). */
@Composable
fun StarField(modifier: Modifier = Modifier) {
    val stars = remember {
        val rnd = Random(42)
        List(90) {
            Triple(
                Offset(rnd.nextFloat(), rnd.nextFloat()),
                rnd.nextFloat() * 1.2f + 0.4f,
                rnd.nextFloat() * 0.45f + 0.25f
            )
        }
    }
    Canvas(modifier) {
        for ((pos, r, alpha) in stars) {
            drawCircle(
                Color.White.copy(alpha = alpha),
                radius = r * density,
                center = Offset(pos.x * size.width, pos.y * size.height)
            )
        }
    }
}
