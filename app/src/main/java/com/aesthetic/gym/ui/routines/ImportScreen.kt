package com.aesthetic.gym.ui.routines

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.ui.components.PrimaryButton
import com.aesthetic.gym.ui.components.SecondaryButton
import com.aesthetic.gym.ui.components.SectionCard
import com.aesthetic.gym.ui.components.SectionTitle
import com.aesthetic.gym.ui.nav.Routes
import com.aesthetic.gym.ui.rememberImporter
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Accent
import com.aesthetic.gym.ui.theme.Danger
import com.aesthetic.gym.ui.theme.Success
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextSecondary

private const val EXAMPLE = """Rutina: Push Pull Legs

Día 1: Empuje
- Press de banca: 4x6-8 @ 60kg
- Press inclinado mancuernas: 3x8-10 @ 22kg
- Press militar: 3x8-10
- Elevaciones laterales: 4x12-15

Día 2: Tirón
- Dominadas: 4xAMRAP
- Remo con barra: 4x6-8 @ 70kg
- Curl con barra: 3x8-10 @ 30kg

Día 3: Pierna
- Sentadilla: 4x6-8 @ 90kg
- Peso muerto rumano: 3x8-10 @ 70kg
- Curl femoral: 3x12-15"""

@Composable
fun ImportScreen(navController: NavController) {
    val repo = rememberRepository()
    val importer = rememberImporter()
    val context = LocalContext.current
    val vm: ImportViewModel = viewModel(factory = ImportViewModel.factory(repo, importer))
    val ui = vm.ui

    val pickPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importPdf(context, uri) }

    var routineName by remember { mutableStateOf("") }
    var routineText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
            }
            Text("Importar rutina", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }

        when {
            ui.importing -> {
                SectionCard(Modifier.fillMaxWidth()) {
                    Text("Procesando rutina…", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
            ui.result != null -> {
                val res = ui.result
                SectionCard(Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = Success, modifier = Modifier.height(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(res.routineName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${res.dayCount} días · ${res.exerciseCount} ejercicios · ${res.matchedCount} reconocidos automáticamente",
                            color = TextSecondary, fontSize = 13.sp
                        )
                        if (res.createdExercises.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Añadidos como personalizados: ${res.createdExercises.joinToString(", ")}",
                                color = TextSecondary, fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton(
                            "Activar y ver rutina",
                            {
                                vm.activate(res.routineId)
                                navController.popBackStack()
                                navController.navigate(Routes.routineDetail(res.routineId))
                            },
                            Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton("Importar otra", { vm.reset(); routineText = "" }, Modifier.fillMaxWidth())
                    }
                }
            }
            else -> {
                PrimaryButton(
                    "Seleccionar PDF",
                    { pickPdf.launch(arrayOf("application/pdf")) },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Filled.PictureAsPdf
                )

                if (ui.error != null) {
                    Row(
                        Modifier.fillMaxWidth().background(Danger.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = Danger, modifier = Modifier.height(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(ui.error, color = Danger, fontSize = 13.sp)
                    }
                }

                SectionTitle("Formato esperado")
                SectionCard(Modifier.fillMaxWidth(), color = SurfaceVariant) {
                    Text(
                        EXAMPLE,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }

                SectionTitle("O pega el texto de tu rutina")
                OutlinedTextField(
                    value = routineName,
                    onValueChange = { routineName = it },
                    label = { Text("Nombre (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = routineText,
                    onValueChange = { routineText = it },
                    label = { Text("Pega aquí tu rutina") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    keyboardOptions = KeyboardOptions.Default,
                    colors = fieldColors()
                )
                PrimaryButton(
                    "Importar texto",
                    { vm.importText(routineText, routineName) },
                    Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Accent,
    unfocusedBorderColor = SurfaceVariant,
    focusedLabelColor = Accent,
    unfocusedLabelColor = TextSecondary,
    cursorColor = Accent
)
