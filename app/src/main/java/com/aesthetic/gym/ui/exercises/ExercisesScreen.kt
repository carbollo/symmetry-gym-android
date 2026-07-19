package com.aesthetic.gym.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.data.db.ExerciseEntity
import com.aesthetic.gym.domain.model.MuscleGroup
import com.aesthetic.gym.domain.model.WeightUnit
import com.aesthetic.gym.ui.components.AppTopBar
import com.aesthetic.gym.ui.components.MuscleIcons
import com.aesthetic.gym.ui.nav.Routes
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.ui.theme.Violet
import com.aesthetic.gym.util.formatWeightValue
import com.aesthetic.gym.util.normalizeText

@Composable
fun ExercisesScreen(navController: NavController) {
    val repo = rememberRepository()
    val vm: ExercisesViewModel = viewModel(factory = ExercisesViewModel.factory(repo))
    val all by vm.exercises.collectAsState()

    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<MuscleGroup?>(null) }

    // Filtering is memoised so it doesn't run on every recomposition.
    val filtered = remember(all, query, muscle) {
        val nq = normalizeText(query)
        all.filter { ex ->
            (muscle == null || ex.primaryMuscle == muscle) &&
                (nq.isBlank() || normalizeText(ex.name).contains(nq))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item(key = "topbar") {
            AppTopBar("Ejercicios") { navController.navigate(Routes.PROFILE) }
        }

        item(key = "search") {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp)).background(SurfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Buscar ejercicio", color = TextMuted, fontSize = 14.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Violet),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item(key = "chips") {
            Column {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip("Todos", muscle == null) { muscle = null }
                    MuscleGroup.ranked.forEach { m ->
                        FilterChip(m.displayName, muscle == m) { muscle = m }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "${filtered.size} EJERCICIOS",
                    color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp, modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        items(filtered, key = { it.id }) { ex ->
            ExerciseRow(ex) { navController.navigate(Routes.exerciseDetail(ex.id)) }
        }
    }
}

@Composable
private fun ExerciseRow(ex: ExerciseEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp)).background(Surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                MuscleIcons.forMuscle(ex.primaryMuscle), null,
                tint = TextSecondary, modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                ex.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${ex.primaryMuscle.displayName} · ${ex.equipment.displayName}",
                color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            ex.lastWeightKg?.let { w ->
                Spacer(Modifier.height(3.dp))
                Text(
                    "Último: ${formatWeightValue(w, WeightUnit.KG)} kg" +
                        (ex.lastReps?.let { " × $it" } ?: ""),
                    color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Filled.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) Violet else SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text,
            color = if (selected) Color.White else TextSecondary,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
        )
    }
}
