package com.aesthetic.gym.ui.account

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.social.SessionState
import com.aesthetic.gym.ui.theme.Danger
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.ui.theme.Violet

private const val MODE_LOGIN = "login"
private const val MODE_REGISTER = "register"

@Composable
fun AuthScreen(navController: NavController, initialMode: String) {
    val vm: AccountViewModel = viewModel()
    val session by vm.session.collectAsState()
    val ui by vm.ui.collectAsState()

    var register by remember { mutableStateOf(initialMode == MODE_REGISTER) }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // En cuanto haya sesión (registro o login correcto), volvemos al Perfil.
    LaunchedEffect(session) {
        if (session is SessionState.LoggedIn) navController.popBackStack()
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 28.dp)
    ) {
        // ---------- HEADER ----------
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(SurfaceVariant)
                    .clickable { navController.popBackStack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("Cuenta", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            if (register) "Crea tu cuenta" else "Inicia sesión",
            color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Solo para las funciones sociales (amigos y compartir rutinas). " +
                "Tus entrenos siguen guardándose en el teléfono, con cuenta o sin ella.",
            color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp
        )

        // ---------- TOGGLE ----------
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surface)
                .border(1.dp, Outline, RoundedCornerShape(14.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SegTab("Iniciar sesión", !register, Modifier.weight(1f)) {
                if (register) { register = false; vm.clearError() }
            }
            SegTab("Crear cuenta", register, Modifier.weight(1f)) {
                if (!register) { register = true; vm.clearError() }
            }
        }

        // ---------- FORM ----------
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
                .border(1.dp, Outline, RoundedCornerShape(20.dp)).padding(16.dp)
        ) {
            Column {
                FieldLabel("CORREO")
                AuthField(email, "tu@correo.com", KeyboardType.Email) { email = it; vm.clearError() }

                if (register) {
                    Spacer(Modifier.height(14.dp))
                    FieldLabel("NOMBRE DE USUARIO")
                    AuthField(username, "p.ej. carbollo", KeyboardType.Text) { username = it; vm.clearError() }

                    Spacer(Modifier.height(14.dp))
                    FieldLabel("NOMBRE VISIBLE (OPCIONAL)")
                    AuthField(displayName, "Cómo te verán tus amigos", KeyboardType.Text) {
                        displayName = it; vm.clearError()
                    }
                }

                Spacer(Modifier.height(14.dp))
                FieldLabel("CONTRASEÑA")
                PasswordField(
                    password,
                    if (register) "Mínimo 8 caracteres" else "Tu contraseña",
                    imeAction = ImeAction.Done,
                    onDone = { submit(vm, register, email, username, displayName, password) }
                ) { password = it; vm.clearError() }

                ui.error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
                }

                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (ui.loading) Violet.copy(alpha = 0.5f) else Violet)
                        .clickable(enabled = !ui.loading) {
                            submit(vm, register, email, username, displayName, password)
                        }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (ui.loading) {
                        CircularProgressIndicator(
                            color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            if (register) "Crear cuenta" else "Entrar",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Lock, null, tint = TextMuted, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Guardamos solo tu correo y un nombre de usuario para conectarte con amigos. " +
                    "Puedes cerrar sesión cuando quieras desde el Perfil.",
                color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp
            )
        }
    }
}

private fun submit(
    vm: AccountViewModel,
    register: Boolean,
    email: String,
    username: String,
    displayName: String,
    password: String
) {
    if (register) vm.register(email, password, username, displayName)
    else vm.login(email, password)
}

@Composable
private fun SegTab(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(11.dp))
            .background(if (selected) Violet else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color.White else TextSecondary,
            fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun AuthField(
    value: String,
    placeholder: String,
    keyboard: KeyboardType,
    onChange: (String) -> Unit
) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceVariant)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        if (value.isEmpty()) Text(placeholder, color = TextMuted, fontSize = 13.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(Violet),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PasswordField(
    value: String,
    placeholder: String,
    imeAction: ImeAction,
    onDone: () -> Unit,
    onChange: (String) -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceVariant)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, color = TextMuted, fontSize = 13.sp)
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
                    keyboardActions = KeyboardActions(onDone = { onDone() }),
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(Violet),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = if (visible) "Ocultar contraseña" else "Mostrar contraseña",
                tint = TextMuted,
                modifier = Modifier.size(18.dp).clickable { visible = !visible }
            )
        }
    }
}
