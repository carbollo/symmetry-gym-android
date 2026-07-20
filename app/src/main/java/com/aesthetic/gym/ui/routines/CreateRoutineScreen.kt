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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.data.db.ExerciseEntity
import com.aesthetic.gym.ui.components.ExercisePickerDialog
import com.aesthetic.gym.ui.components.MuscleIcons
import com.aesthetic.gym.ui.nav.Routes
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Background
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.ui.theme.Violet
import com.aesthetic.gym.util.normalizeText

@Composable
fun CreateRoutineScreen(navController: NavController) {
    val repo = rememberRepository()
    val vm: CreateRoutineViewModel = viewModel(factory = CreateRoutineViewModel.factory(repo))
    val exercises by vm.exercises.collectAsState()
    var pickerDay by remember { mutableStateOf<Int?>(null) }

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
                "CREAR RUTINA", color = Color.White, fontWeight = FontWeight.Black,
                fontSize = 18.sp, modifier = Modifier.weight(1f)
            )
            Text("AYUDA", color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
                                        .background(Violet.copy(alpha = 0.22f)),
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
                                    fontSize = 15.sp, modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Filled.Close, "Quitar", tint = TextMuted,
                                    modifier = Modifier.size(17.dp).clickable { vm.removeItem(dayIndex, itemIndex) }
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumCol("SERIES", item.sets.toString(), "$dayIndex-$itemIndex-s", Modifier.weight(1f)) { s ->
                                    s.toIntOrNull()?.let { v -> vm.updateItem(dayIndex, itemIndex) { it.copy(sets = v.coerceIn(1, 20)) } }
                                }
                                NumCol("REPS MÍN", item.repsMin.toString(), "$dayIndex-$itemIndex-rmin", Modifier.weight(1f)) { s ->
                                    s.toIntOrNull()?.let { v -> vm.updateItem(dayIndex, itemIndex) { it.copy(repsMin = v.coerceIn(1, 100)) } }
                                }
                                NumCol("REPS MÁX", item.repsMax.toString(), "$dayIndex-$itemIndex-rmax", Modifier.weight(1f)) { s ->
                                    s.toIntOrNull()?.let { v -> vm.updateItem(dayIndex, itemIndex) { it.copy(repsMax = v.coerceIn(1, 100)) } }
                                }
                                NumCol("PESO KG", item.weightKg?.let { trim(it) } ?: "", "$dayIndex-$itemIndex-w", Modifier.weight(1f)) { s ->
                                    vm.updateItem(dayIndex, itemIndex) {
                                        it.copy(weightKg = if (s.isBlank()) null else s.replace(',', '.').toDoubleOrNull())
                                    }
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                        }

                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Violet.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                .clickable { pickerDay = dayIndex }
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
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (vm.canSave) Violet else SurfaceVariant)
                    .clickable(enabled = vm.canSave) {
                        vm.save { id ->
                            navController.popBackStack()
                            navController.navigate(Routes.routineDetail(id))
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
                    "GUARDAR RUTINA",
                    color = if (vm.canSave) Color.White else TextMuted,
                    fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp
                )
            }
        }
    }

    val di = pickerDay
    if (di != null) {
        ExercisePickerDialog(
            exercises = exercises,
            onPick = { ex -> vm.addItem(di, ex); pickerDay = null },
            onCreate = { text -> vm.createCustomExercise(text) { ex -> vm.addItem(di, ex) }; pickerDay = null },
            onDismiss = { pickerDay = null }
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

@Composable
private fun NumCol(label: String, initial: String, key: String, modifier: Modifier = Modifier, onCommit: (String) -> Unit) {
    Column(modifier) {
        Text(label, color = TextMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(5.dp))
        var text by remember(key) { mutableStateOf(initial) }
        Box(
            Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(8.dp)).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it; onCommit(it) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

private fun trim(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else ((v * 10).toInt() / 10.0).toString()
