package com.aesthetic.gym.ui.exercises

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.domain.model.WeightUnit
import com.aesthetic.gym.ui.components.LineChart
import com.aesthetic.gym.ui.components.MuscleIcons
import com.aesthetic.gym.ui.components.SectionCard
import com.aesthetic.gym.ui.components.SectionTitle
import com.aesthetic.gym.ui.components.StatTile
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Accent
import com.aesthetic.gym.ui.theme.Cyan
import com.aesthetic.gym.ui.theme.Gold
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.util.formatWeight
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
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(MuscleIcons.forMuscle(exercise?.primaryMuscle), null, tint = Accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                exercise?.name ?: "Ejercicio",
                color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 2
            )
        }

        if (exercise != null) {
            Text(
                "${exercise.primaryMuscle.displayName} · ${exercise.equipment.displayName}",
                color = TextSecondary, fontSize = 13.sp
            )
            exercise.lastWeightKg?.let { w ->
                Spacer(Modifier.height(2.dp))
                Text(
                    "Último peso usado: ${formatWeightValue(w, WeightUnit.KG)} kg" +
                        (exercise.lastReps?.let { " × $it reps" } ?: ""),
                    color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (!stats.hasData) {
            SectionCard(Modifier.fillMaxWidth()) {
                Text(
                    "Aún no has registrado series de este ejercicio. Cuando lo entrenes verás aquí tu 1RM, tu mejor serie y tu evolución.",
                    color = TextSecondary, fontSize = 13.sp
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("${stats.best1RM.roundToInt()} kg", "1RM estimado", Modifier.weight(1f), accent = Gold)
                StatTile(
                    "${formatWeightValue(stats.bestSetWeight, WeightUnit.KG)}×${stats.bestSetReps}",
                    "mejor serie", Modifier.weight(1f), accent = Accent
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("${stats.sessions}", "entrenos", Modifier.weight(1f), accent = Cyan)
                StatTile("${stats.totalVolume.roundToInt()} kg", "volumen total", Modifier.weight(1f))
            }

            SectionCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionTitle("Evolución del 1RM estimado")
                    Spacer(Modifier.height(10.dp))
                    if (stats.series.size >= 2) {
                        LineChart(stats.series, lineColor = Accent)
                    } else {
                        Text("Necesitas al menos 2 sesiones para ver la gráfica.", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        if (!exercise?.instructions.isNullOrBlank()) {
            SectionCard(Modifier.fillMaxWidth()) {
                Column {
                    SectionTitle("Técnica")
                    Spacer(Modifier.height(8.dp))
                    Text(exercise!!.instructions, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}
