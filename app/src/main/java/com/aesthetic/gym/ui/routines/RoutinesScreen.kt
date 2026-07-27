package com.aesthetic.gym.ui.routines

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.domain.model.RoutineSource
import com.aesthetic.gym.social.IncomingShare
import com.aesthetic.gym.ui.components.AppTopBar
import com.aesthetic.gym.ui.nav.Routes
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Danger
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.ui.theme.Violet
import com.aesthetic.gym.util.formatDate

@Composable
fun RoutinesScreen(navController: NavController) {
    val repo = rememberRepository()
    val vm: RoutinesViewModel = viewModel(factory = RoutinesViewModel.factory(repo))
    val routines by vm.routines.collectAsState()
    val efficiency by vm.efficiency.collectAsState()
    val incoming by vm.incoming.collectAsState()
    val shareMsg by vm.shareMsg.collectAsState()

    // Cada vez que se abre Rutinas, refrescamos la bandeja por si llegaron rutinas nuevas.
    LaunchedEffect(Unit) { vm.refreshIncoming() }
    LaunchedEffect(shareMsg) {
        if (shareMsg != null) { kotlinx.coroutines.delay(2600); vm.clearShareMsg() }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {

        AppTopBar("Zenit") { navController.navigate(Routes.PROFILE) }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(6.dp))
            Text("RUTINAS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Gestiona tus entrenamientos de alto rendimiento.",
                color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp
            )
            Spacer(Modifier.height(18.dp))

            // ---------- BANDEJA: rutinas que me han compartido ----------
            if (incoming.isNotEmpty()) {
                IncomingSharesCard(
                    shares = incoming,
                    onAccept = { vm.accept(it) },
                    onDecline = { vm.decline(it) }
                )
                Spacer(Modifier.height(14.dp))
            }
            shareMsg?.let {
                Text(it, color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
            }

            // Primary + secondary actions
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Violet)
                    .clickable { navController.navigate(Routes.CREATE_ROUTINE) }
                    .padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Crear rutina", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface)
                    .border(1.dp, Outline, RoundedCornerShape(16.dp))
                    .clickable { navController.navigate(Routes.IMPORT) }
                    .padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Description, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Importar desde PDF/texto", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "MIS PLANES", color = TextMuted, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f)
                )
                Text("${routines.size} Total", color = TextMuted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(10.dp))

            if (routines.isEmpty()) {
                Text(
                    "Aún no tienes rutinas. Crea una o impórtala desde un PDF.",
                    color = TextSecondary, fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            routines.forEach { routine ->
                Box(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(18.dp)).background(Surface)
                        .border(
                            1.dp,
                            if (routine.isActive) Violet else Outline,
                            RoundedCornerShape(18.dp)
                        )
                        .clickable { navController.navigate(Routes.routineDetail(routine.id)) }
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape)
                                .background(if (routine.isActive) Violet.copy(alpha = 0.22f) else SurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (routine.isActive) Icons.Filled.FitnessCenter else Icons.AutoMirrored.Filled.ListAlt,
                                null,
                                tint = if (routine.isActive) Violet else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(routine.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${sourceLabel(routine.source)} · ${formatDate(routine.createdAt)}",
                                color = TextMuted, fontSize = 11.sp
                            )
                        }
                        if (routine.isActive) {
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(Violet)
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            ) {
                                Text("ACTIVA", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        // Atajo directo al editor sin pasar por el detalle.
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(SurfaceVariant)
                                .clickable { navController.navigate(Routes.editRoutine(routine.id)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Edit, "Editar rutina",
                                tint = TextSecondary, modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // ---------- WEEKLY EFFICIENCY ----------
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface)
                    .border(1.dp, Outline, RoundedCornerShape(18.dp)).padding(16.dp)
            ) {
                Column {
                    Text(
                        "EFICIENCIA SEMANAL", color = TextMuted, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("${efficiency.percent}%", color = Violet, fontWeight = FontWeight.Black, fontSize = 30.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth().height(56.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val maxIndex = efficiency.bars.indexOf(efficiency.bars.maxOrNull() ?: 0f)
                        efficiency.bars.forEachIndexed { i, v ->
                            Box(
                                Modifier.weight(1f)
                                    .height((10 + (v * 46)).dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (i == maxIndex && v > 0f) Violet else SurfaceVariant)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomingSharesCard(
    shares: List<IncomingShare>,
    onAccept: (IncomingShare) -> Unit,
    onDecline: (IncomingShare) -> Unit
) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface)
            .border(1.dp, Violet, RoundedCornerShape(18.dp)).padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Inbox, null, tint = Violet, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "RUTINAS COMPARTIDAS CONTIGO", color = Violet, fontSize = 10.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 1.sp
                )
            }
            shares.forEach { share ->
                Spacer(Modifier.height(12.dp))
                Text(share.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                Text("de @${share.fromUsername}", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Violet)
                            .clickable { onAccept(share) }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Aceptar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(SurfaceVariant)
                            .clickable { onDecline(share) }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Rechazar", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            }
        }
    }
}

private fun sourceLabel(source: RoutineSource): String = when (source) {
    RoutineSource.PDF -> "PDF"
    RoutineSource.TEXT -> "Texto"
    RoutineSource.MANUAL -> "Manual"
}
