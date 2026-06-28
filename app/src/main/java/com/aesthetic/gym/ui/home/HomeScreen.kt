package com.aesthetic.gym.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.ui.components.PrimaryButton
import com.aesthetic.gym.ui.components.RankChip
import com.aesthetic.gym.ui.components.RankLadderDialog
import com.aesthetic.gym.ui.components.ScoreBar
import com.aesthetic.gym.ui.components.SectionCard
import com.aesthetic.gym.ui.components.SectionTitle
import com.aesthetic.gym.ui.components.StatTile
import com.aesthetic.gym.ui.nav.Routes
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Accent
import com.aesthetic.gym.ui.theme.Gold
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.util.relativeDay

@Composable
fun HomeScreen(navController: NavController) {
    val repo = rememberRepository()
    val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(repo))
    val state by vm.state.collectAsState()
    var showLadder by remember { mutableStateOf(false) }

    if (showLadder) RankLadderDialog(state.overallScore) { showLadder = false }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Hola, ${state.name}", color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Black, fontSize = 28.sp, modifier = Modifier.weight(1f))
            androidx.compose.material3.IconButton(onClick = { navController.navigate(Routes.PROFILE) }) {
                Icon(
                    androidx.compose.material.icons.Icons.Filled.AccountCircle,
                    "Perfil", tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        // Overall rank card (gradient tinted with the current tier color)
        val rankColor = androidx.compose.ui.graphics.Color(state.overallRank.color)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(rankColor.copy(alpha = 0.30f), Surface)
                    )
                )
                .border(1.dp, rankColor.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle("Rango global")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            state.overallRank.displayName,
                            color = rankColor,
                            fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 2
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f))
                            .clickable { showLadder = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Leaderboard, "Ver todos los rangos",
                            tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                ScoreBar(state.overallScore, rankColor)
                Spacer(Modifier.height(6.dp))
                Text("${state.overallScore} / 100 puntos de fuerza", color = TextSecondary, fontSize = 12.sp)
            }
        }

        // Stats row
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("${state.streak}", "días de racha", Modifier.weight(1f), accent = Gold)
            StatTile("${state.totalWorkouts}", "entrenos totales", Modifier.weight(1f))
        }

        // Active routine
        val routine = state.activeRoutine
        if (routine != null && routine.sortedDays.isNotEmpty()) {
            SectionTitle("Rutina activa · ${routine.routine.name}")
            routine.sortedDays.forEachIndexed { index, day ->
                DayCard(
                    title = day.day.name,
                    subtitle = "${day.items.size} ejercicios",
                    highlighted = index == 0
                ) {
                    vm.startSession(
                        routineId = routine.routine.id,
                        dayId = day.day.id,
                        name = "${routine.routine.name} · ${day.day.name}"
                    ) { sessionId -> navController.navigate(Routes.workout(sessionId)) }
                }
            }
        } else {
            SectionCard(Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.FitnessCenter, null, tint = Accent, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Aún no tienes rutina activa", color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Importa tu rutina desde un PDF y empieza a entrenar.",
                        color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("Importar rutina", { navController.navigate(Routes.IMPORT) },
                        Modifier.fillMaxWidth())
                }
            }
        }

        // Recent workouts
        if (state.recent.isNotEmpty()) {
            SectionTitle("Actividad reciente")
            state.recent.forEach { session ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(SurfaceVariant),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.FitnessCenter, null, tint = Accent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(session.name, color = androidx.compose.ui.graphics.Color.White,
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                        Text(relativeDay(session.startedAt), color = TextSecondary, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun DayCard(title: String, subtitle: String, highlighted: Boolean, onStart: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (highlighted) Accent.copy(alpha = 0.14f) else Surface)
            .clickable(onClick = onStart)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, "Empezar", tint = androidx.compose.ui.graphics.Color.White)
        }
    }
    Spacer(Modifier.height(10.dp))
}
