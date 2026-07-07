package com.aesthetic.gym.ui.routines

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ListAlt
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
import com.aesthetic.gym.domain.model.RoutineSource
import com.aesthetic.gym.ui.components.EmptyState
import com.aesthetic.gym.ui.components.PrimaryButton
import com.aesthetic.gym.ui.components.SecondaryButton
import com.aesthetic.gym.ui.nav.Routes
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Accent
import com.aesthetic.gym.ui.theme.Success
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.util.formatDate

@Composable
fun RoutinesScreen(navController: NavController) {
    val repo = rememberRepository()
    val vm: RoutinesViewModel = viewModel(factory = RoutinesViewModel.factory(repo))
    val routines by vm.routines.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Rutinas", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)

        PrimaryButton(
            "Crear rutina",
            { navController.navigate(Routes.CREATE_ROUTINE) },
            Modifier.fillMaxWidth(),
            icon = Icons.Filled.Add
        )
        SecondaryButton(
            "Importar desde PDF/texto",
            { navController.navigate(Routes.IMPORT) },
            Modifier.fillMaxWidth()
        )

        if (routines.isEmpty()) {
            Spacer(Modifier.height(30.dp))
            EmptyState(
                icon = Icons.Filled.ListAlt,
                title = "Sin rutinas todavía",
                message = "Importa una rutina en PDF o pega el texto para empezar."
            )
        } else {
            routines.forEach { routine ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Surface)
                        .clickable { navController.navigate(Routes.routineDetail(routine.id)) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(SurfaceVariant),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.ListAlt, null, tint = Accent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(routine.name, color = Color.White, fontWeight = FontWeight.Bold,
                                fontSize = 16.sp, maxLines = 1)
                            if (routine.isActive) {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier.clip(RoundedCornerShape(50))
                                        .background(Success.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Activa", color = Success, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text(
                            "${sourceLabel(routine.source)} · ${formatDate(routine.createdAt)}",
                            color = TextSecondary, fontSize = 12.sp
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = TextSecondary)
                }
            }
        }
    }
}

private fun sourceLabel(source: RoutineSource): String = when (source) {
    RoutineSource.PDF -> "PDF"
    RoutineSource.TEXT -> "Texto"
    RoutineSource.MANUAL -> "Manual"
}
