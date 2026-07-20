package com.aesthetic.gym.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.data.db.SetLogEntity
import com.aesthetic.gym.domain.model.MeasureType
import com.aesthetic.gym.domain.model.WeightUnit
import com.aesthetic.gym.ui.components.MuscleIcons
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
import com.aesthetic.gym.util.formatDate
import com.aesthetic.gym.util.formatWeightValue
import kotlin.math.roundToInt

@Composable
fun SessionDetailScreen(navController: NavController, sessionId: Long) {
    val repo = rememberRepository()
    val vm: SessionDetailViewModel = viewModel(factory = SessionDetailViewModel.factory(repo, sessionId))
    val state by vm.state.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp)
    ) {
        item(key = "header") {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(SurfaceVariant)
                            .clickable { navController.popBackStack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, "Volver",
                            tint = Color.White, modifier = Modifier.size(17.dp)
                        )
                    }
                    Text(
                        "DETALLE DEL DÍA", color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
                        modifier = Modifier.weight(1f).padding(start = 12.dp)
                    )
                }

                Spacer(Modifier.height(18.dp))
                when {
                    state.loading ->
                        Text("Cargando…", color = TextSecondary, fontSize = 13.sp)
                    state.session == null ->
                        Text("Este entreno ya no existe.", color = TextSecondary, fontSize = 13.sp)
                    else -> {
                        val s = state.session!!
                        Text(
                            formatDate(s.startedAt).uppercase(), color = Violet, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(s.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)

                        state.comebackDays?.let {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.clip(RoundedCornerShape(50)).background(Violet.copy(alpha = 0.16f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Bolt, null, tint = Violet, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    "DE VUELTA", color = Violet, fontSize = 9.sp,
                                    fontWeight = FontWeight.Black, letterSpacing = 1.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        // ---- Stats of the day ----
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile("${state.durationMin}m", "DURACIÓN", Lime, Modifier.weight(1f))
                            StatTile("${state.kcal}", "KCAL", Gold, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile("${state.volumeKg.roundToInt()}", "KG VOLUMEN", Cyan, Modifier.weight(1f))
                            StatTile("${state.sets}", "SERIES", Violet, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            state.exerciseCount.let {
                                if (it == 1) "1 ejercicio" else "$it ejercicios"
                            },
                            color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }

        items(state.exercises, key = { it.exerciseId }) { ex ->
            ExerciseBlock(ex)
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(Surface)
            .border(1.dp, Outline, RoundedCornerShape(16.dp)).padding(14.dp)
    ) {
        Column {
            Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        }
    }
}

@Composable
private fun ExerciseBlock(ex: SessionExercise) {
    Box(
        Modifier.fillMaxWidth().padding(bottom = 12.dp)
            .clip(RoundedCornerShape(16.dp)).background(Surface)
            .border(1.dp, Outline, RoundedCornerShape(16.dp)).padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(SurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        MuscleIcons.forMuscle(ex.muscle), null,
                        tint = Violet, modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(ex.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2)
                    Text(ex.muscleName, color = TextSecondary, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(12.dp))
            ex.sets.forEach { set -> SetLine(set, isBest = set.id == ex.bestSet?.id) }

            Spacer(Modifier.height(8.dp))
            Text(
                "Volumen: ${ex.volumeKg.roundToInt()} kg",
                color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SetLine(set: SetLogEntity, isBest: Boolean) {
    val warmup = set.isWarmup
    val done = set.completed
    val accent = if (warmup) Gold else Lime
    val amount = if (set.measure == MeasureType.SECONDS) "${set.reps} s"
    else "${formatWeightValue(set.weightKg, WeightUnit.KG)} kg × ${set.reps}"

    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Set number, or a flame for a warm-up.
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (warmup) {
                Icon(Icons.Filled.LocalFireDepartment, null, tint = Gold, modifier = Modifier.size(12.dp))
            } else {
                Text("${set.setNumber}", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            amount,
            color = if (done) Color.White else TextMuted,
            fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
        )
        // RIR, when it was logged.
        set.rpe?.let { rpe ->
            val rir = (10.0 - rpe).roundToInt().coerceIn(0, 10)
            Text("RIR $rir", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
        }
        if (isBest) {
            Icon(Icons.Filled.EmojiEvents, "Mejor serie", tint = Gold, modifier = Modifier.size(14.dp))
        } else if (!done) {
            Text("sin hacer", color = TextMuted, fontSize = 9.sp)
        }
    }
}
