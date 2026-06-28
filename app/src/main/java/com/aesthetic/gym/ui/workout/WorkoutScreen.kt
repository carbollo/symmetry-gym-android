package com.aesthetic.gym.ui.workout

import androidx.compose.foundation.background
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
import com.aesthetic.gym.ui.components.SectionCard
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Accent
import com.aesthetic.gym.ui.theme.Danger
import com.aesthetic.gym.ui.theme.Success
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.util.formatWeightValue
import kotlin.math.roundToInt

@Composable
fun WorkoutScreen(navController: NavController, sessionId: Long) {
    val repo = rememberRepository()
    val vm: WorkoutViewModel = viewModel(factory = WorkoutViewModel.factory(repo, sessionId))
    val session by vm.session.collectAsState()
    val planned = vm.plannedItems
    val suggestions = vm.suggestions

    val allSets = session?.sets ?: emptyList()
    val totalSets = allSets.count { it.completed }
    val volume = allSets.filter { it.completed }.sumOf { it.weightKg * it.reps }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
            }
            Text(
                session?.session?.name ?: "Entreno",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("$totalSets", "series", Modifier.weight(1f))
            MiniStat("${volume.roundToInt()}", "kg de volumen", Modifier.weight(1f))
        }

        if (planned.isEmpty()) {
            Text("Cargando ejercicios…", color = TextSecondary, modifier = Modifier.padding(8.dp))
        }

        planned.forEach { item ->
            val exId = item.item.exerciseId
            val sets = allSets.filter { it.exerciseId == exId }.sortedBy { it.setNumber }
            val sugg = suggestions[exId]
            SectionCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        item.exercise?.name ?: item.item.rawText,
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp
                    )
                    Text(targetText(item), color = TextSecondary, fontSize = 12.sp)
                    if (sugg?.note != null) {
                        Spacer(Modifier.height(4.dp))
                        Text("💡 ${sugg.note}", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                        Modifier.fillMaxWidth().clip(CircleShape)
                            .background(SurfaceVariant)
                            .clickable { vm.addSet(item) }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Add, null, tint = Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Añadir serie", color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        PrimaryButton(
            "Finalizar entreno",
            { vm.finish { navController.popBackStack() } },
            Modifier.fillMaxWidth()
        )
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
private fun MiniStat(value: String, label: String, modifier: Modifier = Modifier) {
    SectionCard(modifier) {
        Column {
            Text(value, color = Accent, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Text(label, color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SetRow(
    set: SetLogEntity,
    onWeight: (Double) -> Unit,
    onReps: (Int) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("#${set.setNumber}", color = TextMuted, fontSize = 12.sp, modifier = Modifier.width(24.dp))
        Spacer(Modifier.width(4.dp))
        Stepper(formatWeightValue(set.weightKg, WeightUnit.KG), "kg", { onWeight(-2.5) }, { onWeight(2.5) })
        Spacer(Modifier.width(6.dp))
        Stepper("${set.reps}", "reps", { onReps(-1) }, { onReps(1) })
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(34.dp).clip(CircleShape)
                .background(if (set.completed) Success else SurfaceVariant)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, "Hecha", tint = if (set.completed) Color.White else TextMuted,
                modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.DeleteOutline, "Eliminar", tint = Danger, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Stepper(value: String, unit: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.Filled.Remove, onMinus)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(42.dp)
        ) {
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            Text(unit, color = TextMuted, fontSize = 9.sp)
        }
        StepButton(Icons.Filled.Add, onPlus)
    }
}

@Composable
private fun StepButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(SurfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}
