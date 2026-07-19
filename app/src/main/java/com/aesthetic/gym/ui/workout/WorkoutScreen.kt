package com.aesthetic.gym.ui.workout

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.data.db.RoutineItemWithExercise
import com.aesthetic.gym.data.db.SetLogEntity
import com.aesthetic.gym.domain.model.MeasureType
import com.aesthetic.gym.domain.model.WeightUnit
import com.aesthetic.gym.ui.components.MuscleIcons
import com.aesthetic.gym.ui.components.PrimaryButton
import com.aesthetic.gym.ui.components.ScoreBar
import com.aesthetic.gym.ui.components.SectionCard
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Accent
import com.aesthetic.gym.ui.theme.Background
import com.aesthetic.gym.ui.theme.Cyan
import com.aesthetic.gym.ui.theme.Gold
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Success
import com.aesthetic.gym.ui.theme.Surface
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
    val activity = LocalContext.current as? Activity

    val allSets = session?.sets ?: emptyList()
    val doneTotal = allSets.count { it.completed }
    val totalCount = allSets.size

    var selectedIndex by remember { mutableIntStateOf(0) }
    var showConfirm by remember { mutableStateOf(false) }
    val safeIndex = selectedIndex.coerceIn(0, (planned.size - 1).coerceAtLeast(0))

    // Lock the app during the workout: back minimizes instead of closing/leaving.
    BackHandler { activity?.moveTaskToBack(true) }

    Column(Modifier.fillMaxSize()) {
        // ---- Header ----
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { activity?.moveTaskToBack(true) }) {
                Icon(Icons.Filled.KeyboardArrowDown, "Minimizar", tint = Color.White)
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

        // ---- Timeline of exercises ----
        if (planned.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                planned.forEachIndexed { i, item ->
                    val sets = allSets.filter { it.exerciseId == item.item.exerciseId }
                    val done = sets.count { it.completed }
                    val total = sets.size
                    val complete = total > 0 && done >= total
                    if (i > 0) {
                        Box(
                            Modifier.width(20.dp).height(2.dp)
                                .background(if (complete || done > 0) Success else Outline)
                        )
                    }
                    TimelineBubble(
                        icon = MuscleIcons.forMuscle(item.exercise?.primaryMuscle),
                        selected = i == safeIndex,
                        complete = complete,
                        onClick = { selectedIndex = i }
                    )
                }
            }
        }

        // ---- Selected exercise detail ----
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (planned.isEmpty()) {
                Text("Cargando ejercicios…", color = TextSecondary, modifier = Modifier.padding(8.dp))
            } else {
                val item = planned[safeIndex]
                val exId = item.item.exerciseId
                val sets = allSets.filter { it.exerciseId == exId }.sortedBy { it.setNumber }
                val doneEx = sets.count { it.completed }
                val sugg = suggestions[exId]
                val measure = sets.firstOrNull()?.measure ?: item.exercise?.measure ?: MeasureType.REPS

                SectionCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(Accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    MuscleIcons.forMuscle(item.exercise?.primaryMuscle),
                                    null, tint = Accent, modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.exercise?.name ?: item.item.rawText,
                                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp
                                )
                                Text(targetText(item), color = TextSecondary, fontSize = 12.sp)
                            }
                            CountPill(doneEx, sets.size)
                        }
                        Spacer(Modifier.height(12.dp))
                        MeasureToggle(measure) { vm.setMeasure(exId, sets, it) }
                        if (sugg?.note != null) {
                            Spacer(Modifier.height(10.dp))
                            SuggestionPill(sugg.note)
                        }
                        Spacer(Modifier.height(12.dp))

                        sets.forEach { set ->
                            SetRow(
                                set = set,
                                onWeightSet = { vm.setWeight(set, it) },
                                onRepsSet = { vm.setReps(set, it) },
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

                if (safeIndex < planned.lastIndex) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(SurfaceVariant.copy(alpha = 0.5f))
                            .clickable { selectedIndex = safeIndex + 1 }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Siguiente: ${planned[safeIndex + 1].exercise?.name ?: "ejercicio"}",
                            color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Accent, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // ---- Bottom action ----
        Box(Modifier.fillMaxWidth().background(Background).padding(16.dp)) {
            PrimaryButton(
                "Finalizar entreno",
                { showConfirm = true },
                Modifier.fillMaxWidth()
            )
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = Surface,
            titleContentColor = Color.White,
            textContentColor = TextSecondary,
            title = { Text("¿Finalizar entrenamiento?") },
            text = { Text("Se guardará el entreno y verás el resumen.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    vm.finishWorkout()
                }) { Text("Finalizar", color = Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    vm.summary?.let { s ->
        WorkoutSummaryDialog(s) { navController.popBackStack() }
    }
}

@Composable
private fun MeasureToggle(current: MeasureType, onSelect: (MeasureType) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(SurfaceVariant).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MeasureType.entries.forEach { m ->
            val selected = m == current
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (selected) Accent else Color.Transparent)
                    .clickable { onSelect(m) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (m == MeasureType.SECONDS) "Segundos" else "Reps",
                    color = if (selected) Color.White else TextSecondary,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun WorkoutSummaryDialog(summary: WorkoutSummary, onDone: () -> Unit) {
    Dialog(onDismissRequest = onDone) {
        Box(
            Modifier.widthIn(max = 360.dp).clip(RoundedCornerShape(24.dp)).background(Surface).padding(22.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier.size(58.dp).clip(CircleShape).background(Success.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.EmojiEvents, null, tint = Success, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("¡Entreno completado!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Spacer(Modifier.height(4.dp))
                Text("Buen trabajo 💪", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryTile(formatDuration(summary.durationMin), "duración", Accent, Modifier.weight(1f))
                    SummaryTile("${summary.kcal}", "kcal", Gold, Modifier.weight(1f))
                    SummaryTile("${summary.volumeKg.roundToInt()}", "kg volumen", Cyan, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text("${summary.sets} series completadas", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(20.dp))
                PrimaryButton("Hecho", onDone, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SummaryTile(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(SurfaceVariant.copy(alpha = 0.5f)).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(label, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

private fun formatDuration(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "$minutes min"

@Composable
private fun TimelineBubble(
    icon: ImageVector,
    selected: Boolean,
    complete: Boolean,
    onClick: () -> Unit
) {
    val bg = when {
        complete -> Success
        selected -> Accent.copy(alpha = 0.20f)
        else -> SurfaceVariant
    }
    val borderColor = if (selected) Accent else Outline
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(bg)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, borderColor), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (complete) Icons.Filled.Check else icon,
            null,
            tint = if (complete) Color.White else if (selected) Accent else TextSecondary,
            modifier = Modifier.size(24.dp)
        )
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
    onWeightSet: (Double) -> Unit,
    onRepsSet: (Int) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val bg = if (set.completed) Success.copy(alpha = 0.10f) else SurfaceVariant.copy(alpha = 0.5f)
    var weightText by remember(set.id) { mutableStateOf(formatWeightValue(set.weightKg, WeightUnit.KG)) }
    var repsText by remember(set.id) { mutableStateOf(set.reps.toString()) }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${set.setNumber}",
            color = if (set.completed) Success else TextMuted,
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(22.dp)
        )
        Spacer(Modifier.width(6.dp))
        NumberField(
            value = weightText,
            unit = "KG",
            onValueChange = {
                weightText = it
                it.replace(',', '.').toDoubleOrNull()?.let { w -> onWeightSet(w) }
            }
        )
        Spacer(Modifier.width(10.dp))
        NumberField(
            value = repsText,
            unit = if (set.measure == MeasureType.SECONDS) "SEG" else "REPS",
            onValueChange = {
                repsText = it
                it.toIntOrNull()?.let { r -> onRepsSet(r) }
            }
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.DeleteOutline, "Eliminar", tint = TextMuted, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(4.dp))
        val tickMod = if (set.completed) {
            Modifier.size(40.dp).clip(CircleShape).background(Success)
        } else {
            Modifier.size(40.dp).clip(CircleShape).border(BorderStroke(2.dp, Outline), CircleShape)
        }
        Box(tickMod.clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Check, "Confirmar serie",
                tint = if (set.completed) Color.White else TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun NumberField(value: String, unit: String, onValueChange: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.width(64.dp).height(40.dp).clip(RoundedCornerShape(10.dp)).background(Surface),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
            )
        }
        Text(unit, color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}
