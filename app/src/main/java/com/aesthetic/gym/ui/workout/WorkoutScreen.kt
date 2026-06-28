package com.aesthetic.gym.ui.workout

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.data.db.RoutineItemWithExercise
import com.aesthetic.gym.data.db.SetLogEntity
import com.aesthetic.gym.domain.model.WeightUnit
import com.aesthetic.gym.ui.components.PrimaryButton
import com.aesthetic.gym.ui.components.ScoreBar
import com.aesthetic.gym.ui.components.SectionCard
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Accent
import com.aesthetic.gym.ui.theme.Background
import com.aesthetic.gym.ui.theme.Danger
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Success
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.util.formatWeightValue

@Composable
fun WorkoutScreen(navController: NavController, sessionId: Long) {
    val repo = rememberRepository()
    val vm: WorkoutViewModel = viewModel(factory = WorkoutViewModel.factory(repo, sessionId))
    val session by vm.session.collectAsState()
    val planned = vm.plannedItems
    val suggestions = vm.suggestions

    val allSets = session?.sets ?: emptyList()
    val doneTotal = allSets.count { it.completed }
    val totalCount = allSets.size

    Column(Modifier.fillMaxSize()) {
        // ---- Header ----
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    session?.session?.name ?: "Entreno",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1
                )
                Text(
                    "$doneTotal de $totalCount series completadas",
                    color = TextSecondary, fontSize = 12.sp
                )
            }
        }
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            ScoreBar(
                score = if (totalCount > 0) doneTotal * 100 / totalCount else 0,
                color = Success
            )
        }

        // ---- Exercises ----
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (planned.isEmpty()) {
                Text("Cargando ejercicios…", color = TextSecondary, modifier = Modifier.padding(8.dp))
            }

            planned.forEach { item ->
                val exId = item.item.exerciseId
                val sets = allSets.filter { it.exerciseId == exId }.sortedBy { it.setNumber }
                val doneEx = sets.count { it.completed }
                val sugg = suggestions[exId]

                SectionCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.exercise?.name ?: item.item.rawText,
                                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp
                                )
                                Text(targetText(item), color = TextSecondary, fontSize = 12.sp)
                            }
                            CountPill(doneEx, sets.size)
                        }
                        if (sugg?.note != null) {
                            Spacer(Modifier.height(8.dp))
                            SuggestionPill(sugg.note)
                        }
                        Spacer(Modifier.height(12.dp))

                        sets.forEach { set ->
                            SetRow(
                                set = set,
                                onWeight = { d -> vm.changeWeight(set, d) },
                                onReps = { d -> vm.changeReps(set, d) },
                                onToggle = { vm.toggleCompleted(set) },
                                onDelete = { vm.deleteSet(set) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { vm.addSet(item) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Add, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Añadir serie extra", color = TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // ---- Bottom action ----
        Box(
            Modifier.fillMaxWidth().background(Background).padding(16.dp)
        ) {
            PrimaryButton(
                "Finalizar entreno",
                { vm.finish { navController.popBackStack() } },
                Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CountPill(done: Int, total: Int) {
    val complete = total > 0 && done >= total
    val color = if (complete) Success else Accent
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text("$done/$total", color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun SuggestionPill(note: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Accent.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("💡", fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text(note, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun targetText(item: RoutineItemWithExercise): String {
    val it = item.item
    val reps = when {
        it.amrap -> "AMRAP"
        it.repsMin == it.repsMax -> "${it.repsMin}"
        else -> "${it.repsMin}-${it.repsMax}"
    }
    return "Objetivo: ${it.targetSets} × $reps"
}

@Composable
private fun SetRow(
    set: SetLogEntity,
    onWeight: (Double) -> Unit,
    onReps: (Int) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val bg = if (set.completed) Success.copy(alpha = 0.10f) else SurfaceVariant.copy(alpha = 0.5f)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${set.setNumber}",
            color = if (set.completed) Success else TextMuted,
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(20.dp)
        )
        Spacer(Modifier.width(4.dp))
        Stepper(formatWeightValue(set.weightKg, WeightUnit.KG), "KG", { onWeight(-2.5) }, { onWeight(2.5) })
        Spacer(Modifier.width(6.dp))
        Stepper("${set.reps}", "REPS", { onReps(-1) }, { onReps(1) })
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.DeleteOutline, "Eliminar", tint = TextMuted, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(4.dp))
        // Confirm tick
        val tickMod = if (set.completed) {
            Modifier.size(40.dp).clip(CircleShape).background(Success)
        } else {
            Modifier.size(40.dp).clip(CircleShape).border(BorderStroke(2.dp, Outline), CircleShape)
        }
        Box(
            tickMod.clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Check,
                "Confirmar serie",
                tint = if (set.completed) Color.White else TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun Stepper(value: String, unit: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.Filled.Remove, onMinus)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            Text(unit, color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        StepButton(Icons.Filled.Add, onPlus)
    }
}

@Composable
private fun StepButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(Surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
    }
}
