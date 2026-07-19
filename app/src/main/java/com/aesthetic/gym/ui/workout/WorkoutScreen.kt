package com.aesthetic.gym.ui.workout

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.data.db.SetLogEntity
import com.aesthetic.gym.domain.model.MeasureType
import com.aesthetic.gym.domain.model.WeightUnit
import com.aesthetic.gym.ui.components.MuscleIcons
import com.aesthetic.gym.ui.components.PrimaryButton
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Background
import com.aesthetic.gym.ui.theme.Cyan
import com.aesthetic.gym.ui.theme.Gold
import com.aesthetic.gym.ui.theme.Lime
import com.aesthetic.gym.ui.theme.Magenta
import com.aesthetic.gym.ui.theme.OnLime
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceElevated
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.util.formatWeightValue
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun WorkoutScreen(navController: NavController, sessionId: Long) {
    val repo = rememberRepository()
    val vm: WorkoutViewModel = viewModel(factory = WorkoutViewModel.factory(repo, sessionId))
    val session by vm.session.collectAsState()
    val planned = vm.plannedItems
    val records = vm.records
    val activity = LocalContext.current as? Activity

    val allSets = session?.sets ?: emptyList()
    val doneTotal = allSets.count { it.completed }
    val totalCount = allSets.size

    var selectedIndex by remember { mutableIntStateOf(0) }
    var showConfirm by remember { mutableStateOf(false) }
    val safeIndex = selectedIndex.coerceIn(0, (planned.size - 1).coerceAtLeast(0))

    // Live session timer
    var elapsed by remember { mutableLongStateOf(0L) }
    val startedAt = session?.session?.startedAt
    LaunchedEffect(startedAt) {
        if (startedAt != null) {
            while (true) {
                elapsed = System.currentTimeMillis() - startedAt
                delay(1000)
            }
        }
    }
    val minutes = elapsed / 60000.0
    val intensity = when {
        doneTotal == 0 -> "—"
        minutes < 1.0 -> "ALTA"
        doneTotal / minutes >= 0.45 -> "ALTA"
        doneTotal / minutes >= 0.22 -> "MEDIA"
        else -> "BAJA"
    }

    // Locked during the workout: back minimizes instead of closing/leaving.
    BackHandler { activity?.moveTaskToBack(true) }

    Column(Modifier.fillMaxSize().background(Background)) {

        // ---------- TOP BAR ----------
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { activity?.moveTaskToBack(true) }) {
                Icon(Icons.Filled.KeyboardArrowDown, "Minimizar", tint = Lime, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    dayTitle(session?.session?.name),
                    color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 1
                )
                Text(
                    "$doneTotal de $totalCount series",
                    color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = { showConfirm = true }) {
                Icon(Icons.Filled.MoreVert, "Opciones", tint = TextSecondary)
            }
        }

        // thin lime progress bar
        Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Box(
                Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(SurfaceVariant)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(if (totalCount > 0) doneTotal / totalCount.toFloat() else 0f)
                        .height(3.dp).clip(RoundedCornerShape(50)).background(Lime)
                )
            }
        }

        // ---------- EXERCISE BUBBLES ----------
        if (planned.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                planned.forEachIndexed { i, item ->
                    val sets = allSets.filter { it.exerciseId == item.item.exerciseId }
                    val done = sets.count { it.completed }
                    val complete = sets.isNotEmpty() && done >= sets.size
                    Bubble(
                        icon = MuscleIcons.forMuscle(item.exercise?.primaryMuscle),
                        selected = i == safeIndex,
                        complete = complete,
                        onClick = { selectedIndex = i }
                    )
                }
            }
        }

        // ---------- CONTENT ----------
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            if (planned.isEmpty()) {
                Text("Cargando ejercicios…", color = TextSecondary, modifier = Modifier.padding(8.dp))
            } else {
                val item = planned[safeIndex]
                val exId = item.item.exerciseId
                val sets = allSets.filter { it.exerciseId == exId }.sortedBy { it.setNumber }
                val measure = sets.firstOrNull()?.measure ?: item.exercise?.measure ?: MeasureType.REPS
                val record = records[exId]

                Spacer(Modifier.height(6.dp))
                Text(
                    (item.exercise?.name ?: item.item.rawText).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    fontSize = 26.sp,
                    lineHeight = 30.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EmojiEvents, null, tint = Lime, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (record != null)
                            "RÉCORD: ${formatWeightValue(record.weightKg, WeightUnit.KG)} KG x ${record.reps}"
                        else "SIN RÉCORD TODAVÍA",
                        color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(16.dp))
                MeasureToggle(measure) { vm.setMeasure(exId, sets, it) }

                Spacer(Modifier.height(16.dp))
                sets.forEach { set ->
                    SetRow(
                        set = set,
                        onWeightSet = { vm.setWeight(set, it) },
                        onRepsSet = { vm.setReps(set, it) },
                        onToggle = { vm.toggleCompleted(set) },
                        onDelete = { vm.deleteSet(set) }
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable { vm.addSet(item) }.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, null, tint = Lime, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("AÑADIR SERIE", color = Lime, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ---------- TIME / INTENSITY ----------
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                .clip(RoundedCornerShape(18.dp)).background(Surface).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("TIEMPO TOTAL", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(formatTimer(elapsed), color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("INTENSIDAD", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    intensity, color = Magenta, fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic, fontSize = 22.sp
                )
            }
        }

        // ---------- FINISH ----------
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = { showConfirm = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Magenta, contentColor = Color.White)
            ) {
                Text("FINALIZAR ENTRENO", fontWeight = FontWeight.Black, fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Bolt, null, modifier = Modifier.size(20.dp))
            }
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
                }) { Text("Finalizar", color = Magenta, fontWeight = FontWeight.Bold) }
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

/** "Push Pull Legs · Día 1 - Empuje" -> "DÍA 1 - EMPUJE" */
private fun dayTitle(sessionName: String?): String {
    if (sessionName.isNullOrBlank()) return "ENTRENO"
    return sessionName.substringAfterLast("·").trim().uppercase()
}

private fun formatTimer(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
}

@Composable
private fun Bubble(icon: ImageVector, selected: Boolean, complete: Boolean, onClick: () -> Unit) {
    val bg = when {
        complete -> Magenta
        selected -> Background
        else -> SurfaceVariant
    }
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(bg)
            .then(
                if (selected && !complete) Modifier.border(BorderStroke(2.dp, Lime), CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (complete) Icons.Filled.Check else icon,
            null,
            tint = when {
                complete -> Color.White
                selected -> Lime
                else -> TextMuted
            },
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun MeasureToggle(current: MeasureType, onSelect: (MeasureType) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceVariant).padding(4.dp)
    ) {
        MeasureType.entries.forEach { m ->
            val selected = m == current
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                    .background(if (selected) SurfaceElevated else Color.Transparent)
                    .clickable { onSelect(m) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (m == MeasureType.SECONDS) "SEGUNDOS" else "REPS",
                    color = if (selected) Color.White else TextMuted,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SetRow(
    set: SetLogEntity,
    onWeightSet: (Double) -> Unit,
    onRepsSet: (Int) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var weightText by remember(set.id) { mutableStateOf(formatWeightValue(set.weightKg, WeightUnit.KG)) }
    var repsText by remember(set.id) { mutableStateOf(set.reps.toString()) }
    val done = set.completed

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .then(if (done) Modifier.border(BorderStroke(1.dp, Lime.copy(alpha = 0.6f)), RoundedCornerShape(16.dp)) else Modifier)
            .combinedClickable(onClick = {}, onLongClick = onDelete)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.widthIn(min = 68.dp)) {
            Text(
                "SERIE ${set.setNumber}",
                color = if (done) Lime else TextMuted,
                fontSize = 9.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            if (done) {
                Text(
                    "Hecha", color = Lime, fontSize = 16.sp,
                    fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic
                )
            } else {
                Text(
                    "%02d".format(set.setNumber), color = TextMuted,
                    fontSize = 20.sp, fontWeight = FontWeight.Black
                )
            }
        }
        Spacer(Modifier.weight(1f))
        NumberBox("KG", weightText, done) {
            weightText = it
            it.replace(',', '.').toDoubleOrNull()?.let { w -> onWeightSet(w) }
        }
        Spacer(Modifier.width(8.dp))
        NumberBox(if (set.measure == MeasureType.SECONDS) "SEG" else "REPS", repsText, done) {
            repsText = it
            it.toIntOrNull()?.let { r -> onRepsSet(r) }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(42.dp).clip(CircleShape)
                .then(
                    if (done) Modifier.background(Lime)
                    else Modifier.border(BorderStroke(2.dp, Outline), CircleShape)
                )
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Check, "Confirmar serie",
                tint = if (done) OnLime else TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun NumberBox(label: String, value: String, highlighted: Boolean, onValueChange: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier.width(58.dp).height(38.dp).clip(RoundedCornerShape(10.dp))
                .background(if (highlighted) Lime.copy(alpha = 0.12f) else SurfaceVariant)
                .then(
                    if (highlighted) Modifier.border(BorderStroke(1.dp, Lime), RoundedCornerShape(10.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = if (highlighted) Lime else Color.White,
                    fontSize = 16.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cursorBrush = SolidColor(Lime),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )
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
                    Modifier.size(58.dp).clip(CircleShape).background(Lime.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.EmojiEvents, null, tint = Lime, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("¡ENTRENO COMPLETADO!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 19.sp)
                Spacer(Modifier.height(4.dp))
                Text("Buen trabajo 💪", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryTile(formatDuration(summary.durationMin), "duración", Lime, Modifier.weight(1f))
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
