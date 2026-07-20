package com.aesthetic.gym.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.data.db.DayWithItems
import com.aesthetic.gym.domain.model.Rank
import com.aesthetic.gym.ui.ads.NativeAdCard
import com.aesthetic.gym.ui.components.PrimaryButton
import com.aesthetic.gym.ui.components.RankLadderDialog
import com.aesthetic.gym.ui.nav.Routes
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Cyan
import com.aesthetic.gym.ui.theme.Gold
import com.aesthetic.gym.ui.theme.Lime
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.ui.theme.Violet

@Composable
fun HomeScreen(navController: NavController) {
    val repo = rememberRepository()
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(repo))
    val state by vm.state.collectAsState()
    var showLadder by remember { mutableStateOf(false) }

    if (showLadder) RankLadderDialog(state.overallScore) { showLadder = false }

    val nextRank = Rank.entries.firstOrNull { it.minScore > state.overallScore }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ---------- WORDMARK ----------
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "ZENIT",
                color = Color.White, fontWeight = FontWeight.Black,
                fontSize = 20.sp, letterSpacing = 3.sp, modifier = Modifier.weight(1f)
            )
            CircleIcon(Icons.Filled.Leaderboard, TextSecondary, SurfaceVariant) { showLadder = true }
            Spacer(Modifier.width(10.dp))
            CircleIcon(Icons.Filled.Person, Color.White, Violet) { navController.navigate(Routes.PROFILE) }
        }

        // ---------- GREETING ----------
        Column {
            Text("Hola, ${state.name}", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                "Hoy es un gran día para superar tus límites.",
                color = TextSecondary, fontSize = 12.sp
            )
        }

        // ---------- RANK CARD ----------
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
                .border(1.dp, Outline, RoundedCornerShape(20.dp)).padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "RANGO GLOBAL", color = Violet, fontSize = 9.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.overallRank.displayName.uppercase(),
                            color = Gold, fontWeight = FontWeight.Black, fontSize = 24.sp, maxLines = 2
                        )
                    }
                    Icon(Icons.Filled.MilitaryTech, null, tint = Gold, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(SurfaceVariant)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(state.overallScore.coerceIn(0, 100) / 100f)
                            .height(6.dp).clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(Cyan, Lime)))
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${state.overallScore} / 100 PTS",
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (nextRank != null)
                            "PRÓXIMO RANGO: ${nextRank.minScore - state.overallScore} PTS"
                        else "RANGO MÁXIMO",
                        color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ---------- STAT TILES ----------
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Icons.Filled.Bolt, Violet, "${state.streak} días", "DE RACHA", Modifier.weight(1f))
            StatCard(Icons.Filled.FitnessCenter, Cyan, "${state.totalWorkouts}", "ENTRENOS TOTALES", Modifier.weight(1f))
        }

        // ---------- ACTIVE ROUTINE ----------
        val routine = state.activeRoutine
        if (routine != null && routine.sortedDays.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Rutina activa: ${routine.routine.name}",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Ver todo", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { navController.navigate(Routes.ROUTINES) }
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                routine.sortedDays.forEachIndexed { index, day ->
                    DayCard(day = day, index = index, highlighted = index == 0) {
                        vm.startSession(
                            routineId = routine.routine.id,
                            dayId = day.day.id,
                            name = "${routine.routine.name} · ${day.day.name}"
                        ) { sessionId -> navController.navigate(Routes.workout(sessionId)) }
                    }
                }
            }
        } else {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
                    .border(1.dp, Outline, RoundedCornerShape(20.dp)).padding(18.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.FitnessCenter, null, tint = Violet, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Aún no tienes rutina activa", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Crea una rutina o impórtala desde un PDF.", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("Crear rutina", { navController.navigate(Routes.CREATE_ROUTINE) }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    // La importación existía pero solo se mencionaba en el texto de arriba,
                    // sin ninguna forma de llegar a ella desde aquí.
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(SurfaceVariant)
                            .border(1.dp, Outline, RoundedCornerShape(14.dp))
                            .clickable { navController.navigate(Routes.IMPORT) }
                            .padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.UploadFile, null,
                            tint = Color.White, modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Importar desde PDF", color = Color.White,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Ad at the very bottom of the home screen, and nowhere else: never during a
        // workout, and never next to a button that could be tapped by accident.
        // Nada de pedir anuncio hasta haber leido el perfil: con loading = true todavia
        // no sabemos si el modo de prueba esta activo, y pediriamos contra la unidad real
        // una peticion que habria que repetir acto seguido.
        if (!state.loading) {
            NativeAdCard(
                testMode = state.adsTestMode,
                showDiagnostics = state.adsTestMode
            )
        }
    }
}

@Composable
private fun CircleIcon(icon: ImageVector, tint: Color, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    iconTint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.clip(RoundedCornerShape(18.dp)).background(Surface)
            .border(1.dp, Outline, RoundedCornerShape(18.dp)).padding(16.dp)
    ) {
        Column {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(12.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayCard(day: DayWithItems, index: Int, highlighted: Boolean, onStart: () -> Unit) {
    val muscles = day.sortedItems.mapNotNull { it.exercise?.primaryMuscle?.displayName }.distinct().take(3)
    val minutes = day.items.sumOf { it.item.targetSets } * 3
    // Ancho proporcional a la pantalla en vez de fijo.
    val cardWidth = (LocalConfiguration.current.screenWidthDp.dp * 0.72f).coerceIn(210.dp, 320.dp)

    Box(
        Modifier.width(cardWidth).clip(RoundedCornerShape(20.dp))
            .background(if (highlighted) Violet.copy(alpha = 0.13f) else Surface)
            .border(
                1.dp,
                if (highlighted) Violet.copy(alpha = 0.45f) else Outline,
                RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    day.day.name.replace(" - ", " · "),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    maxLines = 2, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Violet).clickable(onClick = onStart),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, "Empezar", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconLabel(Icons.AutoMirrored.Filled.FormatListBulleted, "${day.items.size} ejercicios")
                IconLabel(Icons.Filled.Schedule, "$minutes min")
            }
            if (muscles.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                // FlowRow: si un chip no cabe, salta a la línea siguiente entero
                // en vez de comprimirse y partir la palabra.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    muscles.forEach { m ->
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(SurfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                m.uppercase(), color = TextSecondary, fontSize = 8.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                                maxLines = 1, softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = TextSecondary, fontSize = 11.sp)
    }
}
