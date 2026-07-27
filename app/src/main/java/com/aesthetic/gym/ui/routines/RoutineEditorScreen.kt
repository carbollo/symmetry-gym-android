package com.aesthetic.gym.ui.routines

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.ui.components.ExercisePickerDialog
import com.aesthetic.gym.ui.components.MuscleIcons
import com.aesthetic.gym.ui.nav.Routes
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Background
import com.aesthetic.gym.ui.theme.Danger
import com.aesthetic.gym.ui.theme.Gold
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.ui.theme.Violet
import com.aesthetic.gym.util.formatKg

/** A qué fila va el ejercicio que se elija en el diálogo. */
private sealed interface PickerTarget {
    data class Add(val dayIndex: Int) : PickerTarget
    data class Replace(val dayIndex: Int, val itemIndex: Int) : PickerTarget
}

@Composable
fun CreateRoutineScreen(navController: NavController) = RoutineEditorScreen(navController, null)

/**
 * Crea una rutina ([routineId] nulo) o edita una guardada: cambiar ejercicios, series,
 * repeticiones y peso, reordenar, y añadir o quitar días.
 */
@Composable
fun RoutineEditorScreen(navController: NavController, routineId: Long?) {
    val repo = rememberRepository()
    val vm: RoutineEditorViewModel =
        viewModel(
            key = "routine-editor-${routineId ?: "new"}",
            factory = RoutineEditorViewModel.factory(repo, routineId)
        )
    val exercises by vm.exercises.collectAsState()
    var picker by remember { mutableStateOf<PickerTarget?>(null) }

    // La rutina se borró desde otra pantalla mientras se abría el editor.
    LaunchedEffect(vm.missing) { if (vm.missing) navController.popBackStack() }

    Column(Modifier.fillMaxSize().background(Background)) {

        // ---------- TOP ----------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(SurfaceVariant)
                    .clickable { navController.popBackStack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (vm.isEditing) "EDITAR RUTINA" else "CREAR RUTINA",
                color = Color.White, fontWeight = FontWeight.Black,
                fontSize = 18.sp, modifier = Modifier.weight(1f)
            )
        }

        if (vm.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cargando…", color = TextSecondary, fontSize = 13.sp)
            }
            return@Column
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 16.dp)
        ) {
            Text(
                "NOMBRE DE LA RUTINA", color = TextMuted, fontSize = 9.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(8.dp))
            LightField(vm.name, "Ej: Empuje Hipertrofia", Modifier.fillMaxWidth(), KeyboardType.Text) {
                vm.updateName(it)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Toca un ejercicio para cambiarlo por otro. Las flechas lo mueven de sitio.",
                color = TextMuted, fontSize = 10.sp
            )

            Spacer(Modifier.height(22.dp))

            vm.days.forEachIndexed { dayIndex, day ->
                Box(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Surface)
                        .border(1.dp, Outline, RoundedCornerShape(20.dp)).padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicTextField(
                                value = day.name,
                                onValueChange = { vm.renameDay(dayIndex, it) },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Violet, fontWeight = FontWeight.Black,
                                    fontSize = 15.sp, letterSpacing = 1.sp
                                ),
                                cursorBrush = SolidColor(Violet),
                                modifier = Modifier.weight(1f)
                            )
                            if (vm.days.size > 1) {
                                Icon(
                                    Icons.Filled.DeleteOutline, "Eliminar día", tint = TextMuted,
                                    modifier = Modifier.size(20.dp).clickable { vm.removeDay(dayIndex) }
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))

                        day.items.forEachIndexed { itemIndex, item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                                        .background(Violet.copy(alpha = 0.22f))
                                        .clickable { picker = PickerTarget.Replace(dayIndex, itemIndex) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        MuscleIcons.forMuscle(item.muscle), null,
                                        tint = Violet, modifier = Modifier.size(15.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    item.name, color = Color.White, fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f)
                                        .clickable { picker = PickerTarget.Replace(dayIndex, itemIndex) }
                                )
                                if (day.items.size > 1) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowUp, "Subir",
                                        tint = if (itemIndex > 0) TextSecondary else Color.Transparent,
                                        modifier = Modifier.size(20.dp)
                                            .clickable(enabled = itemIndex > 0) { vm.moveItem(dayIndex, itemIndex, -1) }
                                    )
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown, "Bajar",
                                        tint = if (itemIndex < day.items.lastIndex) TextSecondary else Color.Transparent,
                                        modifier = Modifier.size(20.dp)
                                            .clickable(enabled = itemIndex < day.items.lastIndex) {
                                                vm.moveItem(dayIndex, itemIndex, 1)
                                            }
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Icon(
                                    Icons.Filled.Close, "Quitar", tint = TextMuted,
                                    modifier = Modifier.size(17.dp).clickable { vm.removeItem(dayIndex, itemIndex) }
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Las claves van por uid (identidad estable), no por posición: al
                                // quitar o mover un ejercicio los de al lado no heredan sus números.
                                NumCol("SERIES", item.sets.toString(), "${item.uid}-s", Modifier.weight(1f)) { s ->
                                    s.toIntOrNull()?.let { v -> vm.updateItem(dayIndex, itemIndex) { it.copy(sets = v.coerceIn(1, 20)) } }
                                }
                                NumCol("REPS MÍN", item.repsMin.toString(), "${item.uid}-rmin", Modifier.weight(1f)) { s ->
                                    s.toIntOrNull()?.let { v -> vm.updateItem(dayIndex, itemIndex) { it.copy(repsMin = v.coerceIn(1, 100)) } }
                                }
                                NumCol("REPS MÁX", item.repsMax.toString(), "${item.uid}-rmax", Modifier.weight(1f)) { s ->
                                    s.toIntOrNull()?.let { v -> vm.updateItem(dayIndex, itemIndex) { it.copy(repsMax = v.coerceIn(1, 100)) } }
                                }
                                NumCol(
                                    "PESO KG", item.weightKg?.let { formatKg(it) } ?: "", "${item.uid}-w",
                                    Modifier.weight(1f), KeyboardType.Decimal
                                ) { s ->
                                    // Vacío = sin peso objetivo. Un texto a medio escribir ("1,")
                                    // se ignora en vez de borrar el peso que ya había.
                                    val parsed = s.replace(',', '.').toDoubleOrNull()
                                    when {
                                        s.isBlank() -> vm.updateItem(dayIndex, itemIndex) { it.copy(weightKg = null) }
                                        parsed != null -> vm.updateItem(dayIndex, itemIndex) { it.copy(weightKg = parsed) }
                                    }
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                        }

                        if (vm.duplicatesIn(dayIndex)) {
                            Text(
                                "Hay un ejercicio repetido en este día: durante el entreno se " +
                                    "juntarían en uno solo.",
                                color = Gold, fontSize = 10.sp
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        if (vm.dayWillBeDropped(dayIndex)) {
                            Text(
                                "Este día se quitará de la rutina al guardar: no tiene ejercicios.",
                                color = Gold, fontSize = 10.sp
                            )
                            Spacer(Modifier.height(10.dp))
                        }

                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Violet.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                .clickable { picker = PickerTarget.Add(dayIndex) }
                                .padding(vertical = 13.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.AddCircleOutline, null, tint = Violet, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "AÑADIR EJERCICIO", color = Violet, fontWeight = FontWeight.Bold,
                                fontSize = 11.sp, letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceVariant)
                    .clickable { vm.addDay() }.padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.CalendarMonth, null, tint = Color.White, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("AÑADIR DÍA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
            }
        }

        // ---------- SAVE ----------
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (vm.saveFailed) {
                Text(
                    "No se pudieron guardar los cambios. Inténtalo otra vez.",
                    color = Danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
            }
            if (vm.weeklyTargetChanges) {
                Text(
                    "Al cambiar los días de tu rutina activa se ajusta el objetivo de la racha semanal.",
                    color = Gold, fontSize = 10.sp
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (vm.canSave) Violet else SurfaceVariant)
                    .clickable(enabled = vm.canSave) {
                        vm.save { id ->
                            if (vm.isEditing) {
                                // Se vuelve al detalle, que observa la rutina y ya se repinta.
                                navController.popBackStack()
                            } else {
                                navController.popBackStack()
                                navController.navigate(Routes.routineDetail(id))
                            }
                        }
                    }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Save, null,
                    tint = if (vm.canSave) Color.White else TextMuted, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (vm.isEditing) "GUARDAR CAMBIOS" else "GUARDAR RUTINA",
                    color = if (vm.canSave) Color.White else TextMuted,
                    fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp
                )
            }
        }
    }


    when (val target = picker) {
        null -> Unit
        is PickerTarget.Add -> ExercisePickerDialog(
            exercises = exercises,
            onPick = { ex -> vm.addItem(target.dayIndex, ex); picker = null },
            onCreate = { text ->
                vm.createCustomExercise(text) { ex -> vm.addItem(target.dayIndex, ex) }
                picker = null
            },
            onDismiss = { picker = null }
        )
        is PickerTarget.Replace -> ExercisePickerDialog(
            exercises = exercises,
            onPick = { ex -> vm.replaceItem(target.dayIndex, target.itemIndex, ex); picker = null },
            onCreate = { text ->
                vm.createCustomExercise(text) { ex -> vm.replaceItem(target.dayIndex, target.itemIndex, ex) }
                picker = null
            },
            onDismiss = { picker = null },
            title = "Cambiar ejercicio"
        )
    }
}

/** White input box like in the design. */
@Composable
private fun LightField(
    value: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType,
    onChange: (String) -> Unit
) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        if (value.isEmpty()) Text(placeholder, color = Color(0xFF9AA0A6), fontSize = 13.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            textStyle = TextStyle(color = Color(0xFF0B0B10), fontSize = 13.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(Violet),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Celda numérica del editor.
 *
 * @param value valor actual SEGÚN EL MODELO. Si el modelo ajusta lo escrito (p. ej. 50 series se
 *   recortan a 20), el campo se corrige solo: lo que se ve es siempre lo que se va a guardar.
 * @param key identidad estable de la fila (uid), para que al borrar o mover un ejercicio los
 *   campos de al lado no se queden con los números del que estaba en esa posición.
 */
@Composable
private fun NumCol(
    label: String,
    value: String,
    key: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Number,
    onCommit: (String) -> Unit
) {
    Column(modifier) {
        Text(label, color = TextMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(5.dp))
        var text by remember(key) { mutableStateOf(value) }
        // El campo vacío se respeta (se está reescribiendo); si hay número y no coincide con el
        // del modelo, manda el modelo.
        LaunchedEffect(value) { if (text.isNotBlank() && text != value) text = value }
        Box(
            Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(8.dp)).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it; onCommit(it) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                textStyle = TextStyle(
                    color = Color(0xFF0B0B10), fontSize = 14.sp,
                    fontWeight = FontWeight.Black, textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(Violet),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )
        }
    }
}

