package com.aesthetic.gym.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
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
import com.aesthetic.gym.ui.components.EmptyState
import com.aesthetic.gym.ui.components.SectionCard
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Accent
import com.aesthetic.gym.ui.theme.Cyan
import com.aesthetic.gym.ui.theme.Gold
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.util.formatDate
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(navController: NavController) {
    val repo = rememberRepository()
    val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(repo))
    val sessions by vm.sessions.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
            }
            Text("Historial", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }

        if (sessions.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            EmptyState(
                icon = Icons.Filled.History,
                title = "Sin entrenos todavía",
                message = "Cuando completes un entreno aparecerá aquí con su volumen y calorías."
            )
        } else {
            sessions.forEach { s ->
                SectionCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(s.session.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(formatDate(s.session.startedAt), color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Metric("${s.volumeKg.roundToInt()} kg", "volumen", Accent)
                            Metric("${s.kcal}", "kcal", Gold)
                            Metric("${s.sets}", "series", Cyan)
                            Metric("${s.durationMin}'", "min", TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(value: String, label: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(SurfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(label, color = TextSecondary, fontSize = 10.sp)
        }
    }
}
