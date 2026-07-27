package com.aesthetic.gym.ui.routines

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.data.db.RoutineItemEntity
import com.aesthetic.gym.domain.model.WeightUnit
import com.aesthetic.gym.ui.components.PrimaryButton
import com.aesthetic.gym.ui.components.SecondaryButton
import com.aesthetic.gym.ui.components.SectionCard
import com.aesthetic.gym.ui.nav.Routes
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Accent
import com.aesthetic.gym.ui.theme.Danger
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Success
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.ui.theme.Violet
import com.aesthetic.gym.util.formatWeight

@Composable
fun RoutineDetailScreen(navController: NavController, routineId: Long) {
    val repo = rememberRepository()
    val vm: RoutineDetailViewModel =
        viewModel(factory = RoutineDetailViewModel.factory(repo, routineId))
    val routine by vm.routine.collectAsState()
    val shareUi by vm.shareUi.collectAsState()

    ShareDialog(shareUi, vm, navController)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { navController.navigate(Routes.editRoutine(routineId)) }) {
                Icon(Icons.Filled.Edit, "Editar rutina", tint = Color.White)
            }
            IconButton(onClick = { vm.openShare() }) {
                Icon(Icons.Filled.Share, "Compartir", tint = Color.White)
            }
            IconButton(onClick = { vm.delete { navController.popBackStack() } }) {
                Icon(Icons.Filled.DeleteOutline, "Eliminar", tint = Danger)
            }
        }

        val r = routine
        if (r == null) {
            Text("Cargando…", color = TextSecondary)
        } else {
            Text(r.routine.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp)

            if (r.routine.isActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Success, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Rutina activa", color = Success, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            } else {
                SecondaryButton("Marcar como activa", { vm.setActive() }, Modifier.fillMaxWidth())
            }

            r.sortedDays.forEach { day ->
                SectionCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(day.day.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(10.dp))
                        day.sortedItems.forEach { rowItem ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(8.dp).clip(CircleShape).background(Accent)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        rowItem.exercise?.name ?: rowItem.item.rawText,
                                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        prescription(rowItem.item),
                                        color = TextSecondary, fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(
                            "Empezar este día",
                            {
                                vm.startDay(day.day.id, "${r.routine.name} · ${day.day.name}") { sessionId ->
                                    navController.navigate(Routes.workout(sessionId))
                                }
                            },
                            Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareDialog(
    state: ShareUi,
    vm: RoutineDetailViewModel,
    navController: NavController
) {
    if (state == ShareUi.Hidden) return
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    when (state) {
        ShareUi.Hidden -> Unit

        ShareUi.Menu -> AlertDialog(
            onDismissRequest = { vm.closeShare() },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { vm.closeShare() }) { Text("Cerrar", color = TextSecondary) } },
            title = { Text("Compartir rutina", color = Color.White) },
            text = {
                Column {
                    ShareOption("Enviar a un usuario", "Le llega una solicitud que acepta desde sus Rutinas.") { vm.chooseSend() }
                    Spacer(Modifier.height(10.dp))
                    ShareOption("Crear enlace", "Un enlace que abre la rutina directamente en Zenit.") { vm.createLink() }
                }
            },
            containerColor = Surface
        )

        ShareUi.NeedsLogin -> AlertDialog(
            onDismissRequest = { vm.closeShare() },
            confirmButton = {
                TextButton(onClick = { vm.closeShare(); navController.navigate(Routes.auth("login")) }) {
                    Text("Iniciar sesión", color = Violet, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { vm.closeShare() }) { Text("Ahora no", color = TextSecondary) } },
            title = { Text("Necesitas una cuenta", color = Color.White) },
            text = {
                Text(
                    "Para compartir rutinas hay que iniciar sesión. Es gratis y opcional, desde el Perfil.",
                    color = TextSecondary, fontSize = 13.sp
                )
            },
            containerColor = Surface
        )

        is ShareUi.Send -> {
            var username by remember { mutableStateOf("") }
            val loading = state.status == SendStatus.Loading
            AlertDialog(
                onDismissRequest = { if (!loading) vm.closeShare() },
                confirmButton = {
                    TextButton(enabled = !loading && username.isNotBlank(), onClick = { vm.send(username) }) {
                        Text(
                            "Enviar",
                            color = if (username.isNotBlank() && !loading) Violet else TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { vm.closeShare() }, enabled = !loading) { Text("Cancelar", color = TextSecondary) }
                },
                title = { Text("Enviar a un usuario", color = Color.White) },
                text = {
                    Column {
                        Text("Escribe el nombre de usuario de un amigo. Solo puedes enviar rutinas a tus amigos.", color = TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it.trim() },
                            singleLine = true,
                            enabled = !loading,
                            placeholder = { Text("nombre de usuario", color = TextMuted) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Violet,
                                unfocusedBorderColor = Outline,
                                cursorColor = Violet
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (loading) {
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = Violet, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Enviando…", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                        val error = when (state.status) {
                            SendStatus.UserNotFound -> "No existe ningún usuario con ese nombre."
                            SendStatus.Self -> "No puedes enviártela a ti mismo."
                            SendStatus.NotFriends -> "Solo puedes enviar rutinas a tus amigos. Agrégalo primero."
                            SendStatus.Error -> "No se pudo enviar. Revisa tu conexión."
                            else -> null
                        }
                        error?.let {
                            Spacer(Modifier.height(12.dp))
                            Text(it, color = Danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                containerColor = Surface
            )
        }

        is ShareUi.Sent -> AlertDialog(
            onDismissRequest = { vm.closeShare() },
            confirmButton = { TextButton(onClick = { vm.closeShare() }) { Text("Hecho", color = Violet, fontWeight = FontWeight.Bold) } },
            title = { Text("Enviada ✓", color = Color.White) },
            text = {
                Text(
                    "Rutina enviada a @${state.toUsername}. Le aparecerá para aceptar en sus Rutinas.",
                    color = TextSecondary, fontSize = 13.sp
                )
            },
            containerColor = Surface
        )

        ShareUi.LinkLoading -> AlertDialog(
            onDismissRequest = { vm.closeShare() },
            confirmButton = {},
            title = { Text("Creando enlace…", color = Color.White) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Violet, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Guardando la rutina", color = TextSecondary, fontSize = 13.sp)
                }
            },
            containerColor = Surface
        )

        is ShareUi.LinkReady -> AlertDialog(
            onDismissRequest = { vm.closeShare() },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, state.url)
                    }
                    context.startActivity(Intent.createChooser(intent, "Compartir rutina"))
                }) { Text("Compartir", color = Violet, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(state.url)); vm.closeShare() }) {
                    Text("Copiar", color = TextSecondary)
                }
            },
            title = { Text("Enlace de la rutina", color = Color.White) },
            text = {
                Column {
                    Text(
                        "Quien abra este enlace en el móvil, se le abrirá Zenit y la rutina se añadirá a su galería.",
                        color = TextSecondary, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(state.url, color = Violet, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = Surface
        )

        ShareUi.LinkError -> AlertDialog(
            onDismissRequest = { vm.closeShare() },
            confirmButton = { TextButton(onClick = { vm.createLink() }) { Text("Reintentar", color = Violet, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { vm.closeShare() }) { Text("Cerrar", color = TextSecondary) } },
            title = { Text("No se pudo crear el enlace", color = Color.White) },
            text = { Text("Revisa tu conexión e inténtalo de nuevo.", color = TextSecondary, fontSize = 13.sp) },
            containerColor = Surface
        )
    }
}

@Composable
private fun ShareOption(title: String, subtitle: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceVariant)
            .clickable(onClick = onClick).padding(14.dp)
    ) {
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
    }
}

private fun prescription(item: RoutineItemEntity): String {
    val reps = when {
        item.amrap -> "${item.targetSets} × AMRAP"
        item.repsMin == item.repsMax -> "${item.targetSets} × ${item.repsMin}"
        else -> "${item.targetSets} × ${item.repsMin}-${item.repsMax}"
    }
    val weight = item.targetWeightKg?.let { " · ${formatWeight(it, WeightUnit.KG)}" } ?: ""
    return reps + weight
}
