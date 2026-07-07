package com.aesthetic.gym.ui.routines

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.data.db.ExerciseEntity
import com.aesthetic.gym.ui.components.MuscleIcons
import com.aesthetic.gym.ui.components.PrimaryButton
import com.aesthetic.gym.ui.components.SectionCard
import com.aesthetic.gym.ui.nav.Routes
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Accent
import com.aesthetic.gym.ui.theme.Background
import com.aesthetic.gym.ui.theme.Danger
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.util.normalizeText

@Composable
fun CreateRoutineScreen(navController: NavController) {
    val repo = rememberRepository()
    val vm: CreateRoutineViewModel = viewModel(factory = CreateRoutineViewModel.factory(repo))
    val exercises by vm.exercises.collectAsState()

    var pickerDay by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
            }
            Text("Crear rutina", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = vm.name,
                onValueChange = { vm.updateName(it) },
                label = { Text("Nombre de la rutina") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )

            vm.days.forEachIndexed { dayIndex, day ->
                SectionCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicTextField(
                                value = day.name,
                                onValueChange = { vm.renameDay(dayIndex, it) },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp),
                                cursorBrush = SolidColor(Accent),
                                modifier = Modifier.weight(1f)
                            )
                            if (vm.days.size > 1) {
                                IconButton(onClick = { vm.removeDay(dayIndex) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.DeleteOutline, "Eliminar día", tint = Danger, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        day.items.forEachIndexed { itemIndex, item ->
                            ItemEditor(
                                item = item,
                                keyPrefix = "${day.localId}-$itemIndex",
                                onSets = { v -> vm.updateItem(dayIndex, itemIndex) { it.copy(sets = v) } },
                                onRepsMin = { v -> vm.updateItem(dayIndex, itemIndex) { it.copy(repsMin = v) } },
                                onRepsMax = { v -> vm.updateItem(dayIndex, itemIndex) { it.copy(repsMax = v) } },
                                onWeight = { v -> vm.updateItem(dayIndex, itemIndex) { it.copy(weightKg = v) } },
                                onRemove = { vm.removeItem(dayIndex, itemIndex) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        AddRow("Añadir ejercicio") { pickerDay = dayIndex }
                    }
                }
            }

            AddRow("Añadir día") { vm.addDay() }
        }

        Box(Modifier.fillMaxWidth().background(Background).padding(16.dp)) {
            PrimaryButton(
                "Guardar rutina",
                {
                    vm.save { id ->
                        navController.popBackStack()
                        navController.navigate(Routes.routineDetail(id))
                    }
                },
                Modifier.fillMaxWidth(),
                enabled = vm.canSave
            )
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

@Composable
private fun ItemEditor(
    item: DraftItem,
    keyPrefix: String,
    onSets: (Int) -> Unit,
    onRepsMin: (Int) -> Unit,
    onRepsMax: (Int) -> Unit,
    onWeight: (Double?) -> Unit,
    onRemove: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceVariant.copy(alpha = 0.5f)).padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MuscleIcons.forMuscle(item.muscle), null, tint = Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(item.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, "Quitar", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledNum("Series", item.sets.toString(), "$keyPrefix-s", 54.dp) { s -> s.toIntOrNull()?.let { onSets(it.coerceIn(1, 20)) } }
            LabeledNum("Reps mín", item.repsMin.toString(), "$keyPrefix-rmin", 58.dp) { s -> s.toIntOrNull()?.let { onRepsMin(it.coerceIn(1, 100)) } }
            LabeledNum("Reps máx", item.repsMax.toString(), "$keyPrefix-rmax", 58.dp) { s -> s.toIntOrNull()?.let { onRepsMax(it.coerceIn(1, 100)) } }
            LabeledNum("Peso kg", item.weightKg?.let { trim(it) } ?: "", "$keyPrefix-w", 64.dp) { s ->
                onWeight(if (s.isBlank()) null else s.replace(',', '.').toDoubleOrNull())
            }
        }
    }
}

@Composable
private fun LabeledNum(label: String, initial: String, key: String, width: Dp, onCommit: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        var text by remember(key) { mutableStateOf(initial) }
        Box(
            Modifier.width(width).height(38.dp).clip(RoundedCornerShape(10.dp)).background(Surface),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it; onCommit(it) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AddRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Accent.copy(alpha = 0.12f)).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Add, null, tint = Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun ExercisePickerDialog(
    exercises: List<ExerciseEntity>,
    onPick: (ExerciseEntity) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val nq = normalizeText(query)
    val filtered = if (nq.isBlank()) exercises else exercises.filter { normalizeText(it.name).contains(nq) }
    val exactMatch = filtered.any { normalizeText(it.name) == nq }

    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.clip(RoundedCornerShape(22.dp)).background(Surface).padding(16.dp)) {
            Column {
                Text("Añadir ejercicio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar o crear") },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
                if (query.isNotBlank() && !exactMatch) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(Accent.copy(alpha = 0.14f)).clickable { onCreate(query) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Add, null, tint = Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Crear \"${query.trim()}\"", color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    filtered.forEach { ex ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(ex) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(SurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(MuscleIcons.forMuscle(ex.primaryMuscle), null, tint = Accent, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ex.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(ex.primaryMuscle.displayName, color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun trim(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else ((v * 10).toInt() / 10.0).toString()

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Accent,
    unfocusedBorderColor = SurfaceVariant,
    focusedLabelColor = Accent,
    unfocusedLabelColor = TextSecondary,
    cursorColor = Accent
)
