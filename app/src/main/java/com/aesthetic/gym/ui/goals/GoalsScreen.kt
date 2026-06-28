package com.aesthetic.gym.ui.goals

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.domain.model.GoalType
import com.aesthetic.gym.ui.components.PrimaryButton
import com.aesthetic.gym.ui.components.ScoreBar
import com.aesthetic.gym.ui.components.SectionCard
import com.aesthetic.gym.ui.components.SectionTitle
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Accent
import com.aesthetic.gym.ui.theme.Danger
import com.aesthetic.gym.ui.theme.Success
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextSecondary

private val addableTypes = listOf(
    GoalType.BODYWEIGHT, GoalType.WORKOUTS, GoalType.STRENGTH_SCORE, GoalType.CUSTOM
)

@Composable
fun GoalsScreen(navController: NavController) {
    val repo = rememberRepository()
    val vm: GoalsViewModel = viewModel(factory = GoalsViewModel.factory(repo))
    val goals by vm.goals.collectAsState()

    var type by remember { mutableStateOf(GoalType.BODYWEIGHT) }
    var target by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
            }
            Text("Objetivos", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }

        // New goal form
        SectionCard(Modifier.fillMaxWidth()) {
            Column {
                SectionTitle("Nuevo objetivo")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    addableTypes.forEach { t ->
                        TypeChip(t.displayName, type == t, Modifier.weight(1f)) { type = t }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (type == GoalType.CUSTOM) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it },
                        label = { Text("Título") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), colors = goalFieldColors()
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = target, onValueChange = { target = it },
                        label = { Text("Meta (${type.unit.ifBlank { "valor" }})") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), colors = goalFieldColors()
                    )
                    Spacer(Modifier.width(10.dp))
                    PrimaryButton("Añadir", {
                        target.replace(',', '.').toDoubleOrNull()?.let {
                            vm.addGoal(title, type, it)
                            target = ""; title = ""
                        }
                    }, icon = Icons.Filled.Add)
                }
            }
        }

        if (goals.isEmpty()) {
            Text("Aún no tienes objetivos. Crea uno arriba.", color = TextSecondary, fontSize = 13.sp)
        }

        goals.forEach { gp ->
            SectionCard(Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(gp.goal.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "${fmt(gp.current)} / ${fmt(gp.goal.targetValue)} ${gp.goal.type.unit}",
                                color = TextSecondary, fontSize = 12.sp
                            )
                        }
                        if (gp.done) {
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(Success.copy(alpha = 0.18f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("¡Logrado!", color = Success, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(onClick = { vm.delete(gp.goal.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.DeleteOutline, "Eliminar", tint = Danger, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ScoreBar((gp.fraction * 100).toInt(), if (gp.done) Success else Accent)
                    if (gp.goal.type == GoalType.CUSTOM) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            StepBtn(Icons.Filled.Remove) { vm.updateManual(gp.goal, gp.goal.manualCurrent - 1) }
                            Text("${fmt(gp.goal.manualCurrent)}", color = Color.White, fontWeight = FontWeight.Bold)
                            StepBtn(Icons.Filled.Add) { vm.updateManual(gp.goal, gp.goal.manualCurrent + 1) }
                            Text("ajustar progreso", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun fmt(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else ((v * 10).toInt() / 10.0).toString()

@Composable
private fun TypeChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) Accent else SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (selected) Color.White else TextSecondary,
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
        )
    }
}

@Composable
private fun StepBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(SurfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
}

@Composable
private fun goalFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    focusedBorderColor = Accent, unfocusedBorderColor = SurfaceVariant,
    focusedLabelColor = Accent, unfocusedLabelColor = TextSecondary, cursorColor = Accent
)
