package com.aesthetic.gym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aesthetic.gym.domain.model.Rank
import com.aesthetic.gym.ui.theme.Accent
import com.aesthetic.gym.ui.theme.Gold
import com.aesthetic.gym.ui.theme.Success
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary

/** Dialog showing the full CS2-style rank ladder and how far the user is from the next rank. */
@Composable
fun RankLadderDialog(score: Int, onDismiss: () -> Unit) {
    val current = Rank.fromScore(score)
    val next = Rank.entries.firstOrNull { it.minScore > score }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier.widthIn(max = 360.dp).clip(RoundedCornerShape(22.dp)).background(Surface).padding(18.dp)
        ) {
            Column {
                Text("Rangos de fuerza", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Spacer(Modifier.size(8.dp))
                if (next != null) {
                    Text(
                        "Estás en ${current.displayName} ($score pts). Te faltan ${next.minScore - score} pts para ${next.displayName}.",
                        color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium
                    )
                } else {
                    Text("¡Has alcanzado el rango máximo: ${current.displayName}!", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.size(14.dp))

                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    Rank.entries.reversed().forEach { rank ->
                        val isCurrent = rank == current
                        val reached = score >= rank.minScore
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCurrent) Accent.copy(alpha = 0.16f) else Color.Transparent)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(rank.color)))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    rank.displayName,
                                    color = if (reached) Color.White else TextSecondary,
                                    fontWeight = if (isCurrent) FontWeight.Black else FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text("desde ${rank.minScore} pts", color = TextMuted, fontSize = 10.sp)
                            }
                            when {
                                isCurrent -> Badge("Actual", Accent)
                                reached -> Icon(Icons.Filled.Check, null, tint = Success, modifier = Modifier.size(18.dp))
                                else -> Text("faltan ${rank.minScore - score}", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.22f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
