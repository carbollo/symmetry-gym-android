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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.aesthetic.gym.domain.model.MuscleGroup
import com.aesthetic.gym.ui.components.BodyMap
import com.aesthetic.gym.ui.components.RankChip
import com.aesthetic.gym.ui.components.ScoreBar
import com.aesthetic.gym.ui.components.SectionCard
import com.aesthetic.gym.ui.components.SectionTitle
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.util.formatWeight
import com.aesthetic.gym.domain.model.WeightUnit

@Composable
fun BodyScreen() {
    val repo = rememberRepository()
    val vm: BodyViewModel = viewModel(factory = BodyViewModel.factory(repo))
    val summary by vm.summary.collectAsState()
    var selected by remember { mutableStateOf<MuscleGroup?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Tu cuerpo", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)

        SectionCard(Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BodyMap(
                    colorFor = { muscle ->
                        val mr = summary.of(muscle)
                        if (mr.hasData) Color(mr.rank.color) else Outline
                    },
                    selected = selected,
                    onSelect = { selected = it },
                    modifier = Modifier.fillMaxWidth().height(330.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    Text("Frente", color = TextMuted, fontSize = 12.sp)
                    Text("Espalda", color = TextMuted, fontSize = 12.sp)
                }
            }
        }

        val sel = selected
        if (sel != null) {
            val mr = summary.of(sel)
            SectionCard(Modifier.fillMaxWidth()) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(sel.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        RankChip(mr.rank)
                    }
                    Spacer(Modifier.height(10.dp))
                    ScoreBar(mr.score, Color(mr.rank.color))
                    Spacer(Modifier.height(6.dp))
                    val detail = if (mr.hasData)
                        "${mr.score}/100 · Mejor 1RM est. ${formatWeight(mr.bestE1rmKg, WeightUnit.KG)}"
                    else
                        "Sin datos todavía. Registra entrenos de ${sel.displayName.lowercase()}."
                    Text(detail, color = TextSecondary, fontSize = 12.sp)
                }
            }
        }

        SectionTitle("Rango por músculo")
        summary.perMuscle.sortedByDescending { it.score }.forEach { mr ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface)
                    .clickable { selected = mr.muscle }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(mr.muscle.displayName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(mr.rank.displayName, color = Color(mr.rank.color), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    ScoreBar(mr.score, Color(mr.rank.color))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
