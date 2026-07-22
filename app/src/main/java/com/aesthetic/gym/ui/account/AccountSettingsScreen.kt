package com.aesthetic.gym.ui.account

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.social.SessionState
import com.aesthetic.gym.ui.nav.Routes
import com.aesthetic.gym.ui.theme.Danger
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.ui.theme.Violet

@Composable
fun AccountSettingsScreen(navController: NavController) {
    val vm: AccountSettingsViewModel = viewModel()
    val session by vm.session.collectAsState()
    val msg by vm.msg.collectAsState()
    val busy by vm.busy.collectAsState()

    val account = (session as? SessionState.LoggedIn)?.account

    var name by remember { mutableStateOf("") }
    var loadedName by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(account) {
        if (!loadedName && account != null) { name = account.displayName; loadedName = true }
    }
    LaunchedEffect(msg) { if (msg != null) { kotlinx.coroutines.delay(2600); vm.clearMsg() } }
    // Si la cuenta desaparece (borrada / logout), volvemos al inicio.
    LaunchedEffect(session) {
        if (session is SessionState.LoggedOut) {
            navController.navigate(Routes.HOME) { popUpTo(Routes.ACCOUNT_SETTINGS) { inclusive = true } }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            confirmButton = {
                TextButton(onClick = { showDelete = false; vm.deleteAccount {} }) {
                    Text("Borrar cuenta", color = Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancelar", color = TextSecondary) } },
            title = { Text("¿Borrar tu cuenta?", color = Color.White) },
            text = {
                Text(
                    "Se eliminará tu cuenta y tus datos sociales (amigos, rutinas compartidas). " +
                        "Tus entrenos del teléfono NO se borran. Esto no se puede deshacer.",
                    color = TextSecondary, fontSize = 13.sp
                )
            },
            containerColor = Surface
        )
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
            Text("Ajustes de cuenta", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        msg?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        // ---------- NOMBRE VISIBLE ----------
        Spacer(Modifier.height(18.dp))
        Card {
            Label("NOMBRE VISIBLE")
            Field(name, "Tu nombre", KeyboardType.Text) { name = it }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Guardar nombre", busy) { vm.saveDisplayName(name) }
        }

        // ---------- CONTRASEÑA ----------
        Spacer(Modifier.height(16.dp))
        Card {
            Label("CAMBIAR CONTRASEÑA")
            Field(current, "Contraseña actual", KeyboardType.Password, password = true) { current = it }
            Spacer(Modifier.height(10.dp))
            Field(newPass, "Nueva contraseña (8+)", KeyboardType.Password, password = true) { newPass = it }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Cambiar contraseña", busy) {
                vm.changePassword(current, newPass) { current = ""; newPass = "" }
            }
        }

        // ---------- BORRAR CUENTA ----------
        Spacer(Modifier.height(16.dp))
        Card {
            Label("ZONA DE PELIGRO")
            Text(
                "Borra tu cuenta y tus datos sociales. Tus entrenos del teléfono se quedan.",
                color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceVariant)
                    .border(1.dp, Danger.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable(enabled = !busy) { showDelete = true }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Borrar mi cuenta", color = Danger, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
            .border(1.dp, Outline, RoundedCornerShape(20.dp)).padding(16.dp)
    ) { Column { content() } }
}

@Composable
private fun Label(text: String) {
    Text(text, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Field(
    value: String,
    placeholder: String,
    keyboard: KeyboardType,
    password: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(placeholder, color = TextMuted) },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Violet,
            unfocusedBorderColor = Outline,
            cursorColor = Violet
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PrimaryButton(text: String, busy: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (busy) Violet.copy(alpha = 0.5f) else Violet)
            .clickable(enabled = !busy, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        else Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
