package com.aesthetic.gym.ui.progress

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.aesthetic.gym.ui.components.LineChart
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
import com.aesthetic.gym.util.formatShortDate
import java.io.File
import kotlin.math.abs
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

    val last = metrics.lastOrNull()
    val delta = if (metrics.size >= 2) metrics.last().weightKg - metrics[metrics.size - 2].weightKg else null

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 28.dp)
    ) {
        // ---------- HEADER ----------
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "PROGRESO", color = Color.White, fontWeight = FontWeight.Black,
                fontSize = 22.sp, modifier = Modifier.weight(1f)
            )
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Violet.copy(alpha = 0.22f))
                    .clickable { navController.navigate(Routes.PROFILE) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, "Perfil", tint = Violet, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("ÚLTIMOS 7 DÍAS", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WeekTile("${weekly.workouts}", "ENTRENOS", Violet, Modifier.weight(1f))
            WeekTile(shortKg(weekly.volumeKg), "KG VOL.", Cyan, Modifier.weight(1f))
            WeekTile("${weekly.kcal}", "KCAL", Gold, Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LinkTile("Objetivos", Icons.Filled.Flag, Modifier.weight(1f)) { navController.navigate(Routes.GOALS) }
            LinkTile("Historial", Icons.AutoMirrored.Filled.ListAlt, Modifier.weight(1f)) { navController.navigate(Routes.HISTORY) }
        }

        // ---------- BODYWEIGHT ----------
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
                .border(1.dp, Outline, RoundedCornerShape(20.dp)).padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Peso corporal", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            last?.let { "Último: ${trim(it.weightKg)} kg" } ?: "Sin registros",
                            color = TextMuted, fontSize = 11.sp
                        )
                    }
                    if (delta != null) {
                        Text(
                            (if (delta > 0) "▲ +" else "▼ ") + trim(abs(delta)) + " kg",
                            color = if (delta > 0) Gold else Lime,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (metrics.size >= 2) {
                    LineChart(metrics.map { it.weightKg.toFloat() }, lineColor = Cyan)
                } else {
                    Text("Registra tu peso para ver la evolución.", color = TextMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(SurfaceVariant)
                            .padding(horizontal = 14.dp, vertical = 13.dp)
                    ) {
                        if (weightInput.isEmpty()) Text("Peso (kg)", color = TextMuted, fontSize = 13.sp)
                        BasicTextField(
                            value = weightInput,
                            onValueChange = { weightInput = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                            cursorBrush = SolidColor(Violet),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(Violet)
                            .clickable {
                                weightInput.replace(',', '.').toDoubleOrNull()?.let {
                                    vm.addWeight(it); weightInput = ""
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 13.dp)
                    ) {
                        Text("Añadir", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                if (metrics.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    metrics.asReversed().take(4).forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${trim(m.weightKg)} kg", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.weight(1f))
                            Text(formatShortDate(m.takenAt), color = TextMuted, fontSize = 11.sp)
                            Spacer(Modifier.width(10.dp))
                            Icon(
                                Icons.Filled.Close, "Eliminar", tint = TextMuted,
                                modifier = Modifier.size(15.dp).clickable { vm.deleteWeight(m.id) }
                            )
                        }
                    }
                }
            }
        }

        // ---------- PHOTOS ----------
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Fotos de\nprogreso", color = Color.White, fontWeight = FontWeight.Black,
                fontSize = 20.sp, lineHeight = 24.sp, modifier = Modifier.weight(1f)
            )
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Violet)
                    .clickable { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.AddAPhoto, null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Añadir\nfoto", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 13.sp)
            }
        }

        Spacer(Modifier.height(14.dp))
        if (photos.isEmpty()) {
            Text("Añade fotos para comparar tu evolución física.", color = TextMuted, fontSize = 12.sp)
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                photos.forEach { photo ->
                    Column(Modifier.padding(end = 12.dp)) {
                        Box {
                            AsyncImage(
                                model = File(photo.filePath),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(width = 150.dp, height = 200.dp)
                                    .clip(RoundedCornerShape(16.dp)).background(SurfaceVariant)
                            )
                            Box(
                                Modifier.padding(8.dp).size(24.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .clickable { vm.deletePhoto(photo) }
                                    .align(Alignment.TopEnd),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Close, "Eliminar", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            formatShortDate(photo.takenAt).uppercase(),
                            color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun trim(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else ((v * 10).roundToInt() / 10.0).toString()

private fun shortKg(v: Double): String =
    if (v >= 1000) "${((v / 100).roundToInt() / 10.0)}k" else "${v.roundToInt()}"

@Composable
private fun WeekTile(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(Surface)
            .border(1.dp, Outline, RoundedCornerShape(16.dp)).padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(label, color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        }
    }
}

@Composable
private fun LinkTile(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Surface)
            .border(1.dp, Outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Violet.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Violet, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
