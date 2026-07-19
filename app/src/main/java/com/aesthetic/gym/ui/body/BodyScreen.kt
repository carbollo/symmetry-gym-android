package com.aesthetic.gym.ui.body

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.domain.model.MuscleGroup
import com.aesthetic.gym.ui.components.AppTopBar
import com.aesthetic.gym.ui.components.BodyMap
import com.aesthetic.gym.ui.components.BodySide
import com.aesthetic.gym.ui.nav.Routes
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Cyan
import com.aesthetic.gym.ui.theme.Gold
import com.aesthetic.gym.ui.theme.Lime
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.ui.theme.Violet

@Composable
fun BodyScreen(navController: NavController) {
    val repo = rememberRepository()
    val vm: BodyViewModel = viewModel(factory = BodyViewModel.factory(repo))
    val summary by vm.summary.collectAsState()
    var selected by remember { mutableStateOf<MuscleGroup?>(null) }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {

        AppTopBar("Tu cuerpo") { navController.navigate(Routes.PROFILE) }

        // gradient accent line
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(Violet, Cyan, Lime, Gold)))
        )

        Spacer(Modifier.height(16.dp))

        // ---------- FRONT / BACK PANELS ----------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BodyPanel("FRENTE", BodySide.FRONT, summary, selected, { selected = it }, Modifier.weight(1f))
            BodyPanel("ESPALDA", BodySide.BACK, summary, selected, { selected = it }, Modifier.weight(1f))
        }

        Spacer(Modifier.height(22.dp))

        // ---------- RANK PER MUSCLE ----------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Rango por músculo",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.Info, null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.height(10.dp))

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            summary.perMuscle.sortedByDescending { it.score }.forEach { mr ->
                val rankColor = Color(mr.rank.color)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface)
                        .clickable { selected = mr.muscle }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            mr.muscle.displayName.uppercase(),
                            color = Color.White, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            mr.rank.displayName.uppercase(),
                            color = rankColor, fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50))
                                .background(SurfaceVariant)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(mr.score.coerceIn(0, 100) / 100f)
                                    .height(5.dp).clip(RoundedCornerShape(50))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(rankColor.copy(alpha = 0.65f), rankColor)
                                        )
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "${mr.score}",
                        color = if (mr.hasData) Color.White else TextMuted,
                        fontWeight = FontWeight.Black, fontSize = 18.sp
                    )
                }
            }
        }

        // selected muscle detail
        val sel = selected
        if (sel != null) {
            val mr = summary.of(sel)
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Violet.copy(alpha = 0.10f))
                    .padding(14.dp)
            ) {
                Text(
                    if (mr.hasData)
                        "${sel.displayName}: ${mr.rank.displayName} · ${mr.score}/100 pts"
                    else "${sel.displayName}: sin datos todavía. Entrena este grupo para subir de rango.",
                    color = TextSecondary, fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun BodyPanel(
    label: String,
    side: BodySide,
    summary: com.aesthetic.gym.domain.rank.RankSummary,
    selected: MuscleGroup?,
    onSelect: (MuscleGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Surface, Color(0xFF0E0E14))))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BodyMap(
            side = side,
            colorFor = { muscle ->
                val mr = summary.of(muscle)
                if (mr.hasData) Color(mr.rank.color) else Outline
            },
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth().height(190.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            label, color = Color.White, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp
        )
    }
}
