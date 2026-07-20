package com.aesthetic.gym.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.data.db.ProfileEntity
import com.aesthetic.gym.domain.model.Sex
import com.aesthetic.gym.domain.model.WeightUnit
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.ui.theme.Violet
import com.aesthetic.gym.util.epochToLocalDate
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(navController: NavController) {
    val repo = rememberRepository()
    val vm: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(repo))
    val profile by vm.profile.collectAsState()

    var name by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf(Sex.MALE) }
    var weight by remember { mutableStateOf("75") }
    var height by remember { mutableStateOf("175") }
    var unit by remember { mutableStateOf(WeightUnit.KG) }
    var experience by remember { mutableStateOf(1) }
    var showRpe by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val resolver = LocalContext.current.contentResolver
    var exportResult by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val count = vm.exportCsv(resolver, uri)
            exportResult = if (count != null) "Exportadas $count series ✓"
            else "No se pudo exportar"
        }
    }

    LaunchedEffect(profile) {
        val p = profile
        if (!loaded && p != null) {
            name = p.name; sex = p.sex
            weight = trimDouble(p.bodyweightKg); height = trimDouble(p.heightCm)
            unit = p.unit; experience = p.experienceLevel
            showRpe = p.showRpe
            loaded = true
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 28.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(SurfaceVariant)
                    .clickable { navController.popBackStack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("Perfil", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        // ---------- AVATAR ----------
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(Violet.copy(alpha = 0.20f))
                    .border(2.dp, Violet, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, null, tint = Violet, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                name.ifBlank { "Atleta Symmetry" },
                color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "Miembro desde ${profile?.createdAt?.takeIf { it > 0 }?.let { epochToLocalDate(it).year } ?: epochToLocalDate(System.currentTimeMillis()).year}",
                color = TextMuted, fontSize = 11.sp
            )
        }

        // ---------- FORM ----------
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
                .border(1.dp, Outline, RoundedCornerShape(20.dp)).padding(16.dp)
        ) {
            Column {
                FieldLabel("NOMBRE")
                DarkField(name, "Tu nombre", KeyboardType.Text) { name = it; saved = false }

                Spacer(Modifier.height(16.dp))
                FieldLabel("SEXO")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Hombre", sex == Sex.MALE, Modifier.weight(1f)) { sex = Sex.MALE; saved = false }
                    Chip("Mujer", sex == Sex.FEMALE, Modifier.weight(1f)) { sex = Sex.FEMALE; saved = false }
                    Chip("Otro", sex == Sex.OTHER, Modifier.weight(1f)) { sex = Sex.OTHER; saved = false }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("PESO (KG)")
                        DarkField(weight, "75", KeyboardType.Decimal) { weight = it; saved = false }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("ALTURA (CM)")
                        DarkField(height, "180", KeyboardType.Decimal) { height = it; saved = false }
                    }
                }

                Spacer(Modifier.height(16.dp))
                FieldLabel("UNIDAD DE PESO")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("kg", unit == WeightUnit.KG, Modifier.width(74.dp)) { unit = WeightUnit.KG; saved = false }
                    Chip("lb", unit == WeightUnit.LB, Modifier.width(74.dp)) { unit = WeightUnit.LB; saved = false }
                }

                Spacer(Modifier.height(16.dp))
                FieldLabel("EXPERIENCIA")
                listOf(1 to "Principiante", 2 to "Intermedio", 3 to "Avanzado").forEach { (level, label) ->
                    Box(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (experience == level) Violet else SurfaceVariant)
                            .clickable { experience = level; saved = false }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (experience == level) Color.White else TextSecondary,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariant)
                        .clickable { showRpe = !showRpe; saved = false }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Registrar RIR por serie",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                        )
                        Text(
                            "Repeticiones que te sobran al cerrar cada serie",
                            color = TextMuted, fontSize = 10.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.width(46.dp).height(26.dp).clip(RoundedCornerShape(50))
                            .background(if (showRpe) Violet else Outline),
                        contentAlignment = if (showRpe) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Box(
                            Modifier.padding(horizontal = 3.dp).size(20.dp)
                                .clip(CircleShape).background(Color.White)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, null, tint = TextMuted, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Tu peso corporal y sexo se usan para calcular tus rangos de fuerza.",
                        color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp
                    )
                }

                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Violet)
                        .clickable {
                            // Copy the stored profile instead of rebuilding it: @Upsert replaces
                            // the whole row, so any field not listed here would be wiped.
                            vm.save(
                                (profile ?: ProfileEntity()).copy(
                                    id = 1,
                                    name = name,
                                    sex = sex,
                                    bodyweightKg = weight.replace(',', '.').toDoubleOrNull() ?: 75.0,
                                    heightCm = height.replace(',', '.').toDoubleOrNull() ?: 175.0,
                                    unit = unit,
                                    experienceLevel = experience,
                                    onboarded = true,
                                    createdAt = profile?.createdAt ?: 0L,
                                    showRpe = showRpe
                                )
                            )
                            saved = true
                        }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (saved) "Guardado ✓" else "Guardar perfil",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                }
            }
        }

        // ---------- BACKUP ----------
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
                .border(1.dp, Outline, RoundedCornerShape(20.dp)).padding(16.dp)
        ) {
            Column {
                FieldLabel("TUS DATOS")
                Text(
                    "Exporta todo tu historial de series a un CSV que puedes abrir en Excel " +
                        "o guardar como copia de seguridad.",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(SurfaceVariant)
                        .clickable {
                            exportResult = null
                            exportLauncher.launch(vm.suggestedFileName())
                        }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Download, null, tint = Violet, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Exportar historial (CSV)",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                }
                exportResult?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun DarkField(value: String, placeholder: String, keyboard: KeyboardType, onChange: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceVariant)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        if (value.isEmpty()) Text(placeholder, color = TextMuted, fontSize = 13.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(Violet),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) Violet else SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color.White else TextSecondary,
            fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            maxLines = 1, softWrap = false
        )
    }
}

private fun trimDouble(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
