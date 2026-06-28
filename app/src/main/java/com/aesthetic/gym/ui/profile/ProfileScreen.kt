package com.aesthetic.gym.ui.profile

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aesthetic.gym.data.db.ProfileEntity
import com.aesthetic.gym.domain.model.Sex
import com.aesthetic.gym.domain.model.WeightUnit
import com.aesthetic.gym.ui.components.PrimaryButton
import com.aesthetic.gym.ui.components.SectionCard
import com.aesthetic.gym.ui.components.SectionTitle
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Accent
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary

@Composable
fun ProfileScreen() {
    val repo = rememberRepository()
    val vm: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(repo))
    val profile by vm.profile.collectAsState()

    var name by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf(Sex.MALE) }
    var weight by remember { mutableStateOf("75") }
    var height by remember { mutableStateOf("175") }
    var unit by remember { mutableStateOf(WeightUnit.KG) }
    var experience by remember { mutableStateOf(1) }
    var loaded by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(profile) {
        val p = profile
        if (!loaded && p != null) {
            name = p.name
            sex = p.sex
            weight = trimDouble(p.bodyweightKg)
            height = trimDouble(p.heightCm)
            unit = p.unit
            experience = p.experienceLevel
            loaded = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Perfil", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)

        SectionCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; saved = false },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = profileFieldColors()
                )

                SectionTitle("Sexo")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("Hombre", sex == Sex.MALE) { sex = Sex.MALE; saved = false }
                    SelectChip("Mujer", sex == Sex.FEMALE) { sex = Sex.FEMALE; saved = false }
                    SelectChip("Otro", sex == Sex.OTHER) { sex = Sex.OTHER; saved = false }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it; saved = false },
                        label = { Text("Peso (kg)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = profileFieldColors()
                    )
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it; saved = false },
                        label = { Text("Altura (cm)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = profileFieldColors()
                    )
                }

                SectionTitle("Unidad de peso")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("kg", unit == WeightUnit.KG) { unit = WeightUnit.KG; saved = false }
                    SelectChip("lb", unit == WeightUnit.LB) { unit = WeightUnit.LB; saved = false }
                }

                SectionTitle("Experiencia")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("Principiante", experience == 1) { experience = 1; saved = false }
                    SelectChip("Intermedio", experience == 2) { experience = 2; saved = false }
                    SelectChip("Avanzado", experience == 3) { experience = 3; saved = false }
                }
            }
        }

        Text(
            "Tu peso corporal y sexo se usan para calcular tus rangos de fuerza.",
            color = TextMuted, fontSize = 12.sp
        )

        PrimaryButton(
            if (saved) "Guardado ✓" else "Guardar perfil",
            {
                vm.save(
                    ProfileEntity(
                        id = 1,
                        name = name,
                        sex = sex,
                        bodyweightKg = weight.replace(',', '.').toDoubleOrNull() ?: 75.0,
                        heightCm = height.replace(',', '.').toDoubleOrNull() ?: 175.0,
                        unit = unit,
                        experienceLevel = experience,
                        onboarded = true,
                        createdAt = profile?.createdAt ?: 0L
                    )
                )
                saved = true
            },
            Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) Accent else Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text,
            color = if (selected) Color.White else TextSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

private fun trimDouble(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

@Composable
private fun profileFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Accent,
    unfocusedBorderColor = SurfaceVariant,
    focusedLabelColor = Accent,
    unfocusedLabelColor = TextMuted,
    cursorColor = Accent
)
