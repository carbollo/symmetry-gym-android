package com.aesthetic.gym.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.domain.model.MuscleGroup
import com.aesthetic.gym.ui.components.MuscleIcons
import com.aesthetic.gym.ui.nav.Routes
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Accent
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.util.normalizeText

@Composable
fun ExercisesScreen(navController: NavController) {
    val repo = rememberRepository()
    val vm: ExercisesViewModel = viewModel(factory = ExercisesViewModel.factory(repo))
    val all by vm.exercises.collectAsState()

    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<MuscleGroup?>(null) }

    val nq = normalizeText(query)
    val filtered = all.filter { ex ->
        (muscle == null || ex.primaryMuscle == muscle) &&
            (nq.isBlank() || normalizeText(ex.name).contains(nq))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Ejercicios", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar ejercicio") },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextSecondary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Accent,
                unfocusedBorderColor = SurfaceVariant,
                focusedLabelColor = Accent,
                unfocusedLabelColor = TextSecondary,
                cursorColor = Accent
            )
        )

        Row(Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip("Todos", muscle == null) { muscle = null }
            MuscleGroup.ranked.forEach { m ->
                Spacer(Modifier.width(8.dp))
                FilterChip(m.displayName, muscle == m) { muscle = m }
            }
        }

        Text("${filtered.size} ejercicios", color = TextSecondary, fontSize = 12.sp)

        filtered.forEach { ex ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface)
                    .clickable { navController.navigate(Routes.exerciseDetail(ex.id)) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(SurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(MuscleIcons.forMuscle(ex.primaryMuscle), null, tint = Accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ex.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        "${ex.primaryMuscle.displayName} · ${ex.equipment.displayName}",
                        color = TextSecondary, fontSize = 12.sp
                    )
                }
                Icon(Icons.Filled.ChevronRight, null, tint = TextSecondary)
            }
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) Accent else Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            color = if (selected) Color.White else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
