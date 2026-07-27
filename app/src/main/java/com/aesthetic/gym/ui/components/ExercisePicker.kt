package com.aesthetic.gym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import com.aesthetic.gym.data.db.ExerciseEntity
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.ui.theme.Violet
import com.aesthetic.gym.util.formatKg
import com.aesthetic.gym.util.normalizeText

/**
 * Searchable exercise picker, shared by "create routine" and by a free workout.
 * Shows the last weight logged for each exercise, which is what tells you whether
 * you have done it before without leaving the dialog.
 */
@Composable
fun ExercisePickerDialog(
    exercises: List<ExerciseEntity>,
    onPick: (ExerciseEntity) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Añadir ejercicio"
) {
    var query by remember { mutableStateOf("") }
    val nq = normalizeText(query)
    val filtered = if (nq.isBlank()) exercises else exercises.filter { normalizeText(it.name).contains(nq) }
    val exactMatch = filtered.any { normalizeText(it.name) == nq }

    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.clip(RoundedCornerShape(22.dp)).background(Surface).padding(16.dp)) {
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Search, null, tint = TextMuted, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Buscar o crear", color = TextMuted, fontSize = 13.sp)
                        BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            cursorBrush = SolidColor(Violet), modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (query.isNotBlank() && !exactMatch) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(Violet.copy(alpha = 0.16f)).clickable { onCreate(query) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Add, null, tint = Violet, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Crear \"${query.trim()}\"", color = Violet, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(filtered, key = { it.id }) { ex ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(ex) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(SurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    MuscleIcons.forMuscle(ex.primaryMuscle), null,
                                    tint = Violet, modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    ex.name, color = Color.White, fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(ex.primaryMuscle.displayName, color = TextSecondary, fontSize = 11.sp)
                                val last = ex.lastWeightKg
                                if (last != null) {
                                    Text(
                                        "Último: ${formatKg(last)} kg" +
                                            (ex.lastReps?.let { " × $it" } ?: ""),
                                        color = TextMuted, fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
