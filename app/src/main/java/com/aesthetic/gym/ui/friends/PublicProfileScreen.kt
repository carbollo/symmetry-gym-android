package com.aesthetic.gym.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.social.FriendStatus
import com.aesthetic.gym.social.ProfileResult
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Success
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.ui.theme.Violet

@Composable
fun PublicProfileScreen(navController: NavController, username: String) {
    val vm: PublicProfileViewModel = viewModel(factory = PublicProfileViewModel.factory(username))
    val state by vm.state.collectAsState()
    val busy by vm.busy.collectAsState()

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp)
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

        Spacer(Modifier.height(30.dp))

        when (val s = state) {
            null -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Violet)
            }

            ProfileResult.NotFound -> CenterMessage("Este usuario no existe.")
            ProfileResult.NetworkError, ProfileResult.Unauthorized -> CenterMessage("No se pudo cargar el perfil.")

            is ProfileResult.Success -> {
                val p = s.profile
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(84.dp).clip(CircleShape).background(Violet.copy(alpha = 0.20f))
                            .border(2.dp, Violet, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            p.displayName.take(1).uppercase().ifBlank { "?" },
                            color = Violet, fontWeight = FontWeight.Black, fontSize = 34.sp
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(p.displayName.ifBlank { p.username }, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("@${p.username}", color = Violet, fontSize = 13.sp)

                    Spacer(Modifier.height(28.dp))
                    FriendButton(p.status, p.direction, busy, onAddOrAccept = { vm.addOrAccept() }, onRemove = { vm.removeOrCancel() })
                }
            }
        }
    }
}

@Composable
private fun FriendButton(
    status: FriendStatus,
    direction: String?,
    busy: Boolean,
    onAddOrAccept: () -> Unit,
    onRemove: () -> Unit
) {
    when (status) {
        FriendStatus.SELF -> Unit

        FriendStatus.NONE -> PrimaryAction("Añadir amigo", busy, onAddOrAccept)

        FriendStatus.PENDING ->
            if (direction == "incoming") {
                PrimaryAction("Aceptar solicitud", busy, onAddOrAccept)
            } else {
                SecondaryAction("Solicitud enviada · Cancelar", busy, onRemove)
            }

        FriendStatus.ACCEPTED -> {
            Text("Amigos ✓", color = Success, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(14.dp))
            SecondaryAction("Quitar de amigos", busy, onRemove)
        }

        FriendStatus.BLOCKED -> Text("No disponible.", color = TextMuted, fontSize = 13.sp)
    }
}

@Composable
private fun PrimaryAction(text: String, busy: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (busy) Violet.copy(alpha = 0.5f) else Violet)
            .clickable(enabled = !busy, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        else Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun SecondaryAction(text: String, busy: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceVariant)
            .border(1.dp, Outline, RoundedCornerShape(14.dp))
            .clickable(enabled = !busy, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextMuted, fontSize = 14.sp)
    }
}
