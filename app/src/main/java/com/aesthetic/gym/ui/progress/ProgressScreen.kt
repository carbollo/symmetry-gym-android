package com.aesthetic.gym.ui.progress

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.aesthetic.gym.ui.components.EmptyState
import com.aesthetic.gym.ui.components.LineChart
import com.aesthetic.gym.ui.components.PrimaryButton
import com.aesthetic.gym.ui.components.SectionCard
import com.aesthetic.gym.ui.components.SectionTitle
import com.aesthetic.gym.ui.components.StatTile
import com.aesthetic.gym.ui.nav.Routes
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Accent
import com.aesthetic.gym.ui.theme.Cyan
import com.aesthetic.gym.ui.theme.Danger
import com.aesthetic.gym.ui.theme.Gold
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.util.formatShortDate
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(navController: NavController) {
    val repo = rememberRepository()
    val context = LocalContext.current
    val vm: ProgressViewModel = viewModel(factory = ProgressViewModel.factory(repo))

    val photos by vm.photos.collectAsState()
    val metrics by vm.metrics.collectAsState()
    val weekly by vm.weekly.collectAsState()

    var weightInput by remember { mutableStateOf("") }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.addPhoto(context, uri) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Progreso", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)

        // ---- Weekly summary ----
        SectionTitle("Esta semana")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${weekly.workouts}", "entrenos", Modifier.weight(1f), accent = Accent)
            StatTile("${weekly.volumeKg.roundToInt()}", "kg volumen", Modifier.weight(1f), accent = Cyan)
            StatTile("${weekly.kcal}", "kcal", Modifier.weight(1f), accent = Gold)
        }

        // ---- Quick links ----
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LinkCard("Objetivos", Icons.Filled.Flag, Modifier.weight(1f)) { navController.navigate(Routes.GOALS) }
            LinkCard("Historial", Icons.AutoMirrored.Filled.ListAlt, Modifier.weight(1f)) { navController.navigate(Routes.HISTORY) }
        }

        // ---- Bodyweight ----
        SectionCard(Modifier.fillMaxWidth()) {
            Column {
                SectionTitle("Peso corporal")
                Spacer(Modifier.height(10.dp))
                if (metrics.size >= 2) {
                    LineChart(metrics.map { it.weightKg.toFloat() }, lineColor = Cyan)
                    Spacer(Modifier.height(6.dp))
                    Text("Último: ${trim(metrics.last().weightKg)} kg", color = TextSecondary, fontSize = 12.sp)
                } else {
                    Text("Registra tu peso para ver la evolución.", color = TextSecondary, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Peso (kg)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = progressFieldColors()
                    )
                    Spacer(Modifier.width(10.dp))
                    PrimaryButton("Añadir", {
                        weightInput.replace(',', '.').toDoubleOrNull()?.let { vm.addWeight(it); weightInput = "" }
                    })
                }
                if (metrics.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    metrics.asReversed().take(6).forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${trim(m.weightKg)} kg", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(formatShortDate(m.takenAt), color = TextSecondary, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { vm.deleteWeight(m.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.DeleteOutline, "Eliminar", tint = Danger, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // ---- Photos ----
        SectionTitle("Fotos de progreso")
        PrimaryButton(
            "Añadir foto",
            { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            Modifier.fillMaxWidth(),
            icon = Icons.Filled.AddAPhoto
        )
        if (photos.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.AddAPhoto,
                title = "Sin fotos todavía",
                message = "Añade fotos para comparar tu evolución física."
            )
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                photos.forEach { photo ->
                    Box(Modifier.padding(end = 10.dp)) {
                        AsyncImage(
                            model = File(photo.filePath),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 120.dp, height = 168.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceVariant)
                        )
                        Box(
                            Modifier.padding(6.dp).size(26.dp).clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { vm.deletePhoto(photo) }
                                .align(Alignment.TopEnd),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Close, "Eliminar", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkCard(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Surface).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

private fun trim(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else ((v * 10).toInt() / 10.0).toString()

@Composable
private fun progressFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Accent,
    unfocusedBorderColor = SurfaceVariant,
    focusedLabelColor = Accent,
    unfocusedLabelColor = TextSecondary,
    cursorColor = Accent
)
