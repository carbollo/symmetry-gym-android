package com.aesthetic.gym.ui.routines

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
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
import com.aesthetic.gym.data.db.RoutineItemEntity
import com.aesthetic.gym.domain.model.WeightUnit
import com.aesthetic.gym.ui.components.PrimaryButton
import com.aesthetic.gym.ui.components.SecondaryButton
import com.aesthetic.gym.ui.components.SectionCard
import com.aesthetic.gym.ui.nav.Routes
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Accent
import com.aesthetic.gym.ui.theme.Danger
import com.aesthetic.gym.ui.theme.Success
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.util.formatWeight

@Composable
fun RoutineDetailScreen(navController: NavController, routineId: Long) {
    val repo = rememberRepository()
    val vm: RoutineDetailViewModel =
        viewModel(factory = RoutineDetailViewModel.factory(repo, routineId))
    val routine by vm.routine.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.delete { navController.popBackStack() } }) {
                Icon(Icons.Filled.DeleteOutline, "Eliminar", tint = Danger)
            }
        }

        val r = routine
        if (r == null) {
            Text("Cargando…", color = TextSecondary)
        } else {
            Text(r.routine.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp)

            if (r.routine.isActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Success, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Rutina activa", color = Success, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            } else {
                SecondaryButton("Marcar como activa", { vm.setActive() }, Modifier.fillMaxWidth())
            }

            r.sortedDays.forEach { day ->
                SectionCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(day.day.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(10.dp))
                        day.sortedItems.forEach { rowItem ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(8.dp).clip(CircleShape).background(Accent)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        rowItem.exercise?.name ?: rowItem.item.rawText,
                                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        prescription(rowItem.item),
                                        color = TextSecondary, fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(
                            "Empezar este día",
                            {
                                vm.startDay(day.day.id, "${r.routine.name} · ${day.day.name}") { sessionId ->
                                    navController.navigate(Routes.workout(sessionId))
                                }
                            },
                            Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private fun prescription(item: RoutineItemEntity): String {
    val reps = when {
        item.amrap -> "${item.targetSets} × AMRAP"
        item.repsMin == item.repsMax -> "${item.targetSets} × ${item.repsMin}"
        else -> "${item.targetSets} × ${item.repsMin}-${item.repsMax}"
    }
    val weight = item.targetWeightKg?.let { " · ${formatWeight(it, WeightUnit.KG)}" } ?: ""
    return reps + weight
}
