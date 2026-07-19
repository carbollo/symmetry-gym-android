package com.aesthetic.gym.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.domain.model.GoalType
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Lime
import com.aesthetic.gym.ui.theme.OnLime
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.ui.theme.Violet
import kotlin.math.abs
import kotlin.math.roundToInt

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

    val doneCount = goals.count { it.done }
    val completionPct = if (goals.isEmpty()) 0 else doneCount * 100 / goals.size

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 28.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(SurfaceVariant)
                    .clickable { navController.popBackStack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Text(
                "OBJETIVOS", color = Color.White, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        Text("NUEVO OBJETIVO", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            addableTypes.forEach { t ->
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (type == t) Violet else SurfaceVariant)
                        .clickable { type = t }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(
                        t.displayName,
                        color = if (type == t) Color.White else TextSecondary,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        if (type == GoalType.CUSTOM) {
            Text("TÍTULO", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(6.dp))
            DarkField(title, "Ej. Hacer 10 dominadas", KeyboardType.Text) { title = it }
            Spacer(Modifier.height(14.dp))
        }
        Text(
            "META ${if (type.unit.isNotBlank()) "(${type.unit})" else ""}",
            color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(6.dp))
        DarkField(target, "Ej. 75.0", KeyboardType.Number) { target = it }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Violet)
                .clickable {
                    target.replace(',', '.').toDoubleOrNull()?.let {
                        vm.addGoal(title, type, it); target = ""; title = ""
                    }
                }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("AÑADIR", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "TUS METAS ACTIVAS", color = TextMuted, fontSize = 9.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(12.dp))

        if (goals.isEmpty()) {
            Text("Aún no tienes objetivos. Crea uno arriba.", color = TextSecondary, fontSize = 13.sp)
        }

        goals.forEach { gp ->
            val pct = (gp.fraction * 100).roundToInt()
            val barColor = if (gp.done) Lime else Violet
            val remaining = abs(gp.goal.targetValue - gp.current)

            Box(
                Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(18.dp)).background(Surface)
                    .border(1.dp, if (gp.done) Lime.copy(alpha = 0.5f) else Outline, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            gp.goal.title, color = Color.White, fontWeight = FontWeight.Bold,
                            fontSize = 17.sp, modifier = Modifier.weight(1f)
                        )
                        if (gp.done) {
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(Lime)
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            ) {
                                Text("¡CONSEGUIDO!", color = OnLime, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(
                            Icons.Filled.DeleteOutline, "Eliminar", tint = TextMuted,
                            modifier = Modifier.size(18.dp).clickable { vm.delete(gp.goal.id) }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${fmt(gp.current)} / ${fmt(gp.goal.targetValue)} ${gp.goal.type.unit}".trim(),
                        color = TextMuted, fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)).background(SurfaceVariant)
                    ) {
                        Box(
                            Modifier.fillMaxWidth(gp.fraction).height(7.dp)
                                .clip(RoundedCornerShape(50)).background(barColor)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (gp.done) {
                            Icon(Icons.Filled.Check, null, tint = Lime, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Meta alcanzada", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("$pct% completado", color = barColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${fmt(remaining)} ${gp.goal.type.unit} restantes".trim(),
                                color = TextMuted, fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        if (goals.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(SurfaceVariant.copy(alpha = 0.5f)).padding(18.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Bolt, null, tint = Violet, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("SIGUE ASÍ, CAMPEÓN", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Has completado el $completionPct% de tus objetivos.",
                        color = TextMuted, fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DarkField(value: String, placeholder: String, keyboard: KeyboardType, onChange: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceVariant)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        if (value.isEmpty()) Text(placeholder, color = TextMuted, fontSize = 13.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(Violet),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun fmt(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else ((v * 10).roundToInt() / 10.0).toString()
