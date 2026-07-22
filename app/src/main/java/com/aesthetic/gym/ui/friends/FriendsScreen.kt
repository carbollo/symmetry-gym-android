package com.aesthetic.gym.ui.friends

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.social.FriendRequest
import com.aesthetic.gym.social.UserSummary
import com.aesthetic.gym.ui.nav.Routes
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.ui.theme.Violet

@Composable
fun FriendsScreen(navController: NavController) {
    val vm: FriendsViewModel = viewModel()
    val results by vm.searchResults.collectAsState()
    val searching by vm.searching.collectAsState()
    val requests by vm.requests.collectAsState()
    val friends by vm.friends.collectAsState()
    val sent by vm.sent.collectAsState()
    val msg by vm.msg.collectAsState()

    var query by remember { mutableStateOf("") }

    // Búsqueda con pequeño retardo para no llamar en cada tecla.
    LaunchedEffect(query) {
        if (query.trim().length < 2) { vm.clearSearch() } else {
            kotlinx.coroutines.delay(300)
            vm.search(query)
        }
    }
    LaunchedEffect(msg) { if (msg != null) { kotlinx.coroutines.delay(2600); vm.clearMsg() } }
    // Al volver a la pantalla, refrescamos solicitudes/amigos.
    LaunchedEffect(Unit) { vm.refresh() }

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
            Text("Amigos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(Modifier.height(18.dp))

        // ---------- BUSCADOR ----------
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surface)
                .border(1.dp, Outline, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Buscar por nombre de usuario", color = TextMuted, fontSize = 13.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(Violet),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searching) CircularProgressIndicator(color = Violet, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            }
        }

        msg?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        // ---------- RESULTADOS DE BÚSQUEDA ----------
        if (query.trim().length >= 2) {
            Spacer(Modifier.height(16.dp))
            SectionLabel("RESULTADOS")
            if (results.isEmpty() && !searching) {
                Text("Sin resultados.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
            results.forEach { user ->
                UserRow(
                    user = user,
                    onClick = { navController.navigate(Routes.userProfile(user.username)) },
                    trailing = {
                        val isFriend = friends.any { it.id == user.id }
                        val incoming = requests.any { it.fromUsername == user.username }
                        val requested = user.username in sent
                        when {
                            isFriend -> ActionChip("Amigo", enabled = false, violet = false) {}
                            incoming -> ActionChip("Aceptar") { vm.addFriend(user.username) }
                            requested -> ActionChip("Enviada", enabled = false) {}
                            else -> ActionChip("Añadir") { vm.addFriend(user.username) }
                        }
                    }
                )
            }
        }

        // ---------- SOLICITUDES ----------
        if (requests.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("SOLICITUDES")
            requests.forEach { req ->
                RequestRow(req, onAccept = { vm.accept(req) }, onReject = { vm.reject(req) })
            }
        }

        // ---------- AMIGOS ----------
        Spacer(Modifier.height(20.dp))
        SectionLabel("MIS AMIGOS (${friends.size})")
        if (friends.isEmpty()) {
            Text(
                "Aún no tienes amigos. Búscalos por su nombre de usuario arriba.",
                color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        friends.forEach { user ->
            UserRow(
                user = user,
                onClick = { navController.navigate(Routes.userProfile(user.username)) },
                trailing = { ActionChip("Quitar", violet = false) { vm.remove(user) } }
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun UserRow(user: UserSummary, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp))
            .background(Surface).border(1.dp, Outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(Violet.copy(alpha = 0.20f))
                .border(1.dp, Violet, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(user.displayName.take(1).uppercase().ifBlank { "?" }, color = Violet, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(user.displayName.ifBlank { user.username }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            Text("@${user.username}", color = TextMuted, fontSize = 11.sp, maxLines = 1)
        }
        trailing()
    }
}

@Composable
private fun RequestRow(req: FriendRequest, onAccept: () -> Unit, onReject: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp))
            .background(Surface).border(1.dp, Violet, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(req.fromDisplayName.ifBlank { req.fromUsername }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            Text("@${req.fromUsername} quiere ser tu amigo", color = TextMuted, fontSize = 11.sp, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        ActionChip("Aceptar") { onAccept() }
        Spacer(Modifier.width(6.dp))
        ActionChip("✕", violet = false) { onReject() }
    }
}

@Composable
private fun ActionChip(text: String, enabled: Boolean = true, violet: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (violet && enabled) Violet else SurfaceVariant)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (violet && enabled) Color.White else TextSecondary,
            fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1
        )
    }
}
