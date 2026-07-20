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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
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
import com.aesthetic.gym.ui.rememberBell
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
import com.aesthetic.gym.util.PlateCalculator
import com.aesthetic.gym.util.formatKg
import com.aesthetic.gym.util.formatWeightValue
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun WorkoutScreen(navController: NavController, sessionId: Long) {
    val repo = rememberRepository()
    val bell = rememberBell()
    val vm: WorkoutViewModel = viewModel(factory = WorkoutViewModel.factory(repo, bell, sessionId))
    val session by vm.session.collectAsState()
    val profile by vm.profile.collectAsState()
    val planned = vm.plannedItems
    val records = vm.records
    val activity = LocalContext.current as? Activity

    val allSets = session?.sets ?: emptyList()
    val doneTotal = allSets.count { it.completed }
    val totalCount = allSets.size

    var selectedIndex by remember { mutableIntStateOf(0) }
    var showConfirm by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<SetLogEntity?>(null) }
    var platesFor by remember { mutableStateOf<Double?>(null) }
    val safeIndex = selectedIndex.coerceIn(0, (planned.size - 1).coerceAtLeast(0))

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
                        color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(SurfaceVariant)
                            .clickable {
                                platesFor = sets.firstOrNull { !it.completed }?.weightKg
                                    ?: sets.firstOrNull()?.weightKg ?: 20.0
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.FitnessCenter, null, tint = Cyan, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("DISCOS", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(16.dp))
                MeasureToggle(measure) { vm.setMeasure(exId, sets, it) }

                Spacer(Modifier.height(12.dp))
                RestSelector(
                    seconds = item.item.restSeconds ?: WorkoutViewModel.DEFAULT_REST_SECONDS,
                    onChange = { vm.setRest(item, it) }
                )

                Spacer(Modifier.height(16.dp))
                sets.forEach { set ->
                    SetRow(
                        set = set,
                        isPr = set.id in vm.prSetIds,
                        showRir = profile?.showRpe == true,
                        onWeightSet = { vm.setWeight(set, it) },
                        onRepsSet = { vm.setReps(set, it) },
                        onToggle = { vm.toggleCompleted(set) },
                        onRirSet = { vm.setRir(set, it) },
                        onLongPress = { optionsFor = set }
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

        // ---------- NEW RECORD ----------
        vm.prEvent?.let { pr ->
            PrBanner(pr) { vm.dismissPr() }
        }

        // ---------- REST COUNTDOWN ----------
        if (vm.restLeft > 0) {
            RestBanner(vm.restLeft, vm.restTotal) { vm.skipRest() }
        }

        // ---------- TIME / INTENSITY ----------
        // Kept in its own composable so the 1s tick only recomposes this card.
        TimeIntensityCard(session?.session?.startedAt, doneTotal)

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

    optionsFor?.let { set ->
        SetOptionsDialog(
            set = set,
            onWarmup = { vm.toggleWarmup(set); optionsFor = null },
            onDelete = { vm.deleteSet(set); optionsFor = null },
            onDismiss = { optionsFor = null }
        )
    }

    platesFor?.let { weight ->
        PlateDialog(
            initialKg = weight,
            barKg = profile?.barWeightKg ?: 20.0,
            onBarChange = { vm.setBarWeight(it) },
            onDismiss = { platesFor = null }
        )
    }

    vm.summary?.let { s ->
        WorkoutSummaryDialog(s) { navController.popBackStack() }
    }
}

/** Celebration shown right after a set that beats a personal record. */
@Composable
private fun PrBanner(pr: PrEvent, onDismiss: () -> Unit) {
    LaunchedEffect(pr.setId, pr.text) {
        delay(4500)
        onDismiss()
    }
    Row(
        Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)).background(Gold.copy(alpha = 0.16f))
            .border(BorderStroke(1.dp, Gold), RoundedCornerShape(16.dp))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.EmojiEvents, null, tint = Gold, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "¡NUEVO RÉCORD!", color = Gold, fontSize = 12.sp,
                fontWeight = FontWeight.Black, letterSpacing = 1.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(pr.text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(pr.exerciseName, color = TextSecondary, fontSize = 10.sp, maxLines = 1)
        }
        Icon(Icons.Filled.Close, "Cerrar", tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}

/** Long-press menu on a set: warm-up toggle and delete. */
@Composable
private fun SetOptionsDialog(
    set: SetLogEntity,
    onWarmup: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 340.dp).clip(RoundedCornerShape(22.dp)).background(Surface).padding(20.dp)
        ) {
            Text("SERIE ${set.setNumber}", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onWarmup).padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.LocalFireDepartment, null, tint = Gold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        if (set.isWarmup) "Marcar como serie normal" else "Marcar como calentamiento",
                        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "El calentamiento no cuenta para volumen, récords ni rangos",
                        color = TextMuted, fontSize = 10.sp
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onDelete).padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Delete, null, tint = Magenta, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Eliminar serie", color = Magenta, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Cerrar", color = TextSecondary)
            }
        }
    }
}

/** Plate calculator: how to load the bar for a given weight. */
@Composable
private fun PlateDialog(
    initialKg: Double,
    barKg: Double,
    onBarChange: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var target by remember { mutableStateOf(initialKg) }
    val result = remember(target, barKg) { PlateCalculator.compute(target, barKg) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 360.dp).clip(RoundedCornerShape(24.dp)).background(Surface).padding(22.dp)
        ) {
            Text(
                "CALCULADORA DE DISCOS", color = Cyan, fontSize = 11.sp,
                fontWeight = FontWeight.Black, letterSpacing = 1.sp
            )
            Spacer(Modifier.height(14.dp))

            // Target weight with fine adjustment.
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepChip("-2.5") { target = (target - 2.5).coerceAtLeast(0.0) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${formatKg(target)} kg", color = Color.White,
                        fontWeight = FontWeight.Black, fontSize = 28.sp, maxLines = 1
                    )
                    Text("objetivo", color = TextMuted, fontSize = 10.sp)
                }
                Spacer(Modifier.width(10.dp))
                StepChip("+2.5") { target += 2.5 }
            }

            Spacer(Modifier.height(18.dp))
            Text("POR CADA LADO", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            when {
                result.belowBar -> Text(
                    "Menos que la barra (${formatKg(barKg)} kg).",
                    color = TextSecondary, fontSize = 13.sp
                )
                result.perSide.isEmpty() -> Text(
                    "Solo la barra.", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlateCalculator.grouped(result.perSide).forEach { (plate, count) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(SurfaceVariant).padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${formatKg(plate)} kg", color = Color.White,
                                fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f)
                            )
                            Text("× $count", color = Cyan, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            if (result.approximate && !result.belowBar) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Con estos discos salen ${formatKg(result.achievedKg)} kg " +
                        "(faltan ${formatKg(result.leftoverKg)} kg).",
                    color = Gold, fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(18.dp))
            Text("BARRA", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PlateCalculator.BAR_OPTIONS.forEach { bar ->
                    val selected = bar == barKg
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (selected) Cyan else SurfaceVariant)
                            .clickable { onBarChange(bar) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            if (bar == 0.0) "Sin barra" else "${formatKg(bar)} kg",
                            color = if (selected) OnLime else TextSecondary,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            PrimaryButton("Cerrar", onDismiss, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StepChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(SurfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

/** "Push Pull Legs · Día 1 - Empuje" -> "DÍA 1 - EMPUJE" */
private fun dayTitle(sessionName: String?): String {
    if (sessionName.isNullOrBlank()) return "ENTRENO"
    return sessionName.substringAfterLast("·").trim().uppercase()
}

@Composable
private fun TimeIntensityCard(startedAt: Long?, doneSets: Int) {
    var elapsed by remember(startedAt) { mutableLongStateOf(0L) }
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
        doneSets == 0 -> "—"
        minutes < 1.0 -> "ALTA"
        doneSets / minutes >= 0.45 -> "ALTA"
        doneSets / minutes >= 0.22 -> "MEDIA"
        else -> "BAJA"
    }
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

/** Rest-time picker for the current exercise (saved in the routine). */
@Composable
private fun RestSelector(seconds: Int, onChange: (Int) -> Unit) {
    val presets = listOf(30, 45, 60, 90, 120, 180)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Timer, null, tint = Lime, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "DESCANSO ENTRE SERIES", color = TextMuted, fontSize = 9.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f)
            )
            Text(
                formatRest(seconds), color = Lime,
                fontSize = 12.sp, fontWeight = FontWeight.Black
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.forEach { p ->
                val selected = p == seconds
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (selected) Lime else SurfaceVariant)
                        .clickable { onChange(p) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        formatRest(p),
                        color = if (selected) OnLime else TextSecondary,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, softWrap = false
                    )
                }
            }
            // Fine adjustment
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(SurfaceVariant)
                    .clickable { onChange((seconds - 15).coerceAtLeast(0)) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) { Text("-15s", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(SurfaceVariant)
                    .clickable { onChange(seconds + 15) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) { Text("+15s", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** Countdown shown while resting; rings a boxing bell when it reaches zero. */
@Composable
private fun RestBanner(left: Int, total: Int, onSkip: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Timer, null, tint = Lime, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "DESCANSO", color = Lime, fontSize = 11.sp,
                fontWeight = FontWeight.Black, letterSpacing = 1.5.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatRest(left), color = Color.White,
                fontSize = 20.sp, fontWeight = FontWeight.Black
            )
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(SurfaceVariant)
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Saltar", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(SurfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(if (total > 0) left / total.toFloat() else 0f)
                    .height(5.dp).clip(RoundedCornerShape(50)).background(Lime)
            )
        }
    }
}

private fun formatRest(seconds: Int): String =
    if (seconds >= 60) {
        val m = seconds / 60
        val s = seconds % 60
        if (s == 0) "${m}:00" else "%d:%02d".format(m, s)
    } else "${seconds}s"

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
    isPr: Boolean,
    showRir: Boolean,
    onWeightSet: (Double) -> Unit,
    onRepsSet: (Int) -> Unit,
    onToggle: () -> Unit,
    onRirSet: (Int) -> Unit,
    onLongPress: () -> Unit
) {
    var weightText by remember(set.id) { mutableStateOf(formatWeightValue(set.weightKg, WeightUnit.KG)) }
    var repsText by remember(set.id) { mutableStateOf(set.reps.toString()) }
    val done = set.completed
    val warmup = set.isWarmup
    val accent = if (warmup) Gold else Lime

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .then(
                if (done) Modifier.border(
                    BorderStroke(1.dp, accent.copy(alpha = 0.6f)), RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1.1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (warmup) {
                        Icon(
                            Icons.Filled.LocalFireDepartment, null,
                            tint = Gold, modifier = Modifier.size(11.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(
                        if (warmup) "CALENT." else "SERIE ${set.setNumber}",
                        color = if (warmup) Gold else if (done) Lime else TextMuted,
                        fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1
                    )
                    if (isPr) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.EmojiEvents, "Récord",
                            tint = Gold, modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                if (done) {
                    Text(
                        "Hecha", color = accent, fontSize = 15.sp,
                        fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, maxLines = 1
                    )
                } else {
                    Text(
                        "%02d".format(set.setNumber), color = TextMuted,
                        fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            NumberBox("KG", weightText, done, Modifier.weight(1f)) {
                weightText = it
                it.replace(',', '.').toDoubleOrNull()?.let { w -> onWeightSet(w) }
            }
            Spacer(Modifier.width(8.dp))
            NumberBox(
                if (set.measure == MeasureType.SECONDS) "SEG" else "REPS",
                repsText, done, Modifier.weight(1f)
            ) {
                repsText = it
                it.toIntOrNull()?.let { r -> onRepsSet(r) }
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .then(
                        if (done) Modifier.background(accent)
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

        if (showRir && done && !warmup) {
            Spacer(Modifier.height(10.dp))
            RirChips(set.rpe) { onRirSet(it) }
        }
    }
}

/** Reps-in-reserve picker shown under a confirmed set (stored as RPE = 10 - RIR). */
@Composable
private fun RirChips(rpe: Double?, onSelect: (Int) -> Unit) {
    val current = rpe?.let { (WorkoutViewModel.RIR_MAX_RPE - it).roundToInt() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "RIR", color = TextMuted, fontSize = 9.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp
        )
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..4).forEach { rir ->
                val selected = current == rir
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (selected) Cyan else SurfaceVariant)
                        .clickable { onSelect(rir) }
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    Text(
                        if (rir == 4) "4+" else "$rir",
                        color = if (selected) OnLime else TextSecondary,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberBox(
    label: String,
    value: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier.fillMaxWidth().widthIn(min = 46.dp).height(38.dp).clip(RoundedCornerShape(10.dp))
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

                if (summary.prs.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(Gold.copy(alpha = 0.12f)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.EmojiEvents, null, tint = Gold, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "RÉCORDS DE HOY", color = Gold, fontSize = 10.sp,
                                fontWeight = FontWeight.Black, letterSpacing = 1.sp
                            )
                        }
                        summary.prs.forEach {
                            Text(it, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }

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
