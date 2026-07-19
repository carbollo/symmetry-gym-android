package com.aesthetic.gym.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.domain.model.WeightUnit
import com.aesthetic.gym.ui.components.LineChart
import com.aesthetic.gym.ui.components.MuscleIcons
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Cyan
import com.aesthetic.gym.ui.theme.Gold
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.ui.theme.Violet
import com.aesthetic.gym.util.formatWeightValue
import kotlin.math.roundToInt

@Composable
fun ExerciseDetailScreen(navController: NavController, exerciseId: String) {
    val repo = rememberRepository()
    val vm: ExerciseDetailViewModel =
        viewModel(factory = ExerciseDetailViewModel.factory(repo, exerciseId))
    val stats by vm.stats.collectAsState()
    val exercise = stats.exercise

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 28.dp)
    ) {
        // ---------- HEADER ----------
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Violet.copy(alpha = 0.22f))
                    .clickable { navController.popBackStack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Violet, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (exercise?.name ?: "Ejercicio").uppercase(),
                    color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 2
                )
                if (exercise != null) {
                    Text(
                        "${exercise.primaryMuscle.displayName} · ${exercise.equipment.displayName}".uppercase(),
                        color = Violet, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                    )
                }
            }
            Icon(
                MuscleIcons.forMuscle(exercise?.primaryMuscle), null,
                tint = TextMuted, modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            exercise?.lastWeightKg?.let { w ->
                "Último peso usado: ${formatWeightValue(w, WeightUnit.KG)} kg" +
                    (exercise.lastReps?.let { " × $it reps" } ?: "")
            } ?: "Sin registros todavía",
            color = TextMuted, fontSize = 11.sp
        )

        Spacer(Modifier.height(18.dp))

        // ---------- STAT GRID ----------
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatBox("1RM estimado", "${stats.best1RM.roundToInt()}", "kg", Gold, Modifier.weight(1f))
            StatBox(
                "mejor serie",
                if (stats.hasData) "${formatWeightValue(stats.bestSetWeight, WeightUnit.KG)}×${stats.bestSetReps}" else "—",
                "", Color.White, Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatBox("entrenos", "${stats.sessions}", "", Cyan, Modifier.weight(1f))
            StatBox("volumen total", "${stats.totalVolume.roundToInt()}", "kg", Color.White, Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))

        // ---------- EVOLUTION ----------
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface)
                .border(1.dp, Outline, RoundedCornerShape(18.dp)).padding(16.dp)
        ) {
            Column {
                Text("Evolución del 1RM estimado", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                if (stats.series.size >= 2) {
                    LineChart(stats.series, lineColor = Violet)
                } else {
                    Text(
                        "Necesitas al menos 2 sesiones de este ejercicio para ver la gráfica.",
                        color = TextMuted, fontSize = 11.sp
                    )
                }
            }
        }

        // ---------- TECHNIQUE ----------
        if (!exercise?.instructions.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface)
                    .border(1.dp, Outline, RoundedCornerShape(18.dp)).padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bolt, null, tint = Violet, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "TÉCNICA", color = Color.White, fontWeight = FontWeight.Black,
                            fontSize = 12.sp, letterSpacing = 1.5.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        exercise!!.instructions,
                        color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(Surface)
            .border(1.dp, Outline, RoundedCornerShape(16.dp)).padding(14.dp)
    ) {
        Column {
            Text(label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 24.sp, maxLines = 1)
                if (unit.isNotBlank()) {
                    Spacer(Modifier.width(3.dp))
                    Text(unit, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.width(28.dp).height(3.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.8f))
            )
        }
    }
}
