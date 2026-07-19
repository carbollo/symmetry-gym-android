package com.aesthetic.gym.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
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
import com.aesthetic.gym.util.formatDate
import kotlin.math.roundToInt

private val accents = listOf(Lime, Cyan, Violet, Gold)

@Composable
fun HistoryScreen(navController: NavController) {
    val repo = rememberRepository()
    val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(repo))
    val state by vm.state.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp)
    ) {
        item(key = "top") {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(SurfaceVariant)
                            .clickable { navController.popBackStack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, "Volver",
                            tint = Color.White, modifier = Modifier.size(17.dp)
                        )
                    }
                    Text(
                        "HISTORIAL", color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
                        modifier = Modifier.weight(1f).padding(start = 12.dp)
                    )
                }

                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "ACTIVIDAD RECIENTE", color = Violet, fontSize = 9.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Text("Tus Sesiones", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                    }
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Violet),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CalendarMonth, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Column {
                        Text("ESTE MES", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(3.dp))
                        Text("${state.sessionsThisMonth} Sesiones", color = Lime, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    }
                    Column {
                        Text("TIEMPO TOTAL", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${((state.totalHours * 10).roundToInt() / 10.0)}h",
                            color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))

                if (state.sessions.isEmpty()) {
                    Text(
                        "Cuando completes un entreno aparecerá aquí con su volumen y calorías.",
                        color = TextSecondary, fontSize = 13.sp
                    )
                }
            }
        }

        itemsIndexed(state.sessions, key = { _, s -> s.session.id }) { i, s ->
            SessionCard(s, accents[i % accents.size])
        }

        if (state.sessions.isNotEmpty()) {
            item(key = "motivation") {
                Box(
                    Modifier.fillMaxWidth().padding(top = 4.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(SurfaceVariant.copy(alpha = 0.5f)).padding(18.dp)
                ) {
                    Column {
                        Text(
                            "SIGUES PROGRESANDO", color = TextMuted, fontSize = 9.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "CONSISTENCIA ES LA CLAVE",
                            color = Color.White, fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic, fontSize = 20.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(s: SessionSummary, accent: Color) {
    Box(
        Modifier.fillMaxWidth().padding(bottom = 12.dp)
            .clip(RoundedCornerShape(16.dp)).background(Surface)
            .border(1.dp, Outline, RoundedCornerShape(16.dp))
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(4.dp).height(96.dp).background(accent))
            Column(Modifier.weight(1f).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.session.name, color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Filled.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    formatDate(s.session.startedAt).uppercase(),
                    color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("${s.kcal} KCAL", Violet)
                    Pill("${s.sets} SERIES", Cyan)
                    Pill("${s.volumeKg.roundToInt()} KG", accent)
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}
