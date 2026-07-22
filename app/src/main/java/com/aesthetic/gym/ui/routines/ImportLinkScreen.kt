package com.aesthetic.gym.ui.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.aesthetic.gym.social.FetchRoutineResult
import com.aesthetic.gym.social.RoutinePortable
import com.aesthetic.gym.social.SocialApi
import com.aesthetic.gym.ui.nav.Routes
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.ui.theme.Background
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.ui.theme.Violet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface LinkStatus {
    data object Loading : LinkStatus
    data class Done(val routineId: Long) : LinkStatus
    data class Error(val message: String) : LinkStatus
}

/**
 * Pantalla de aterrizaje al abrir un enlace de rutina (https://.../r/CODE). Trae la rutina pública,
 * la importa a la galería (filas nuevas) y navega a ella. No requiere cuenta.
 */
@Composable
fun ImportLinkScreen(navController: NavController, code: String) {
    val repo = rememberRepository()
    var status by remember { mutableStateOf<LinkStatus>(LinkStatus.Loading) }

    LaunchedEffect(code) {
        val c = code.trim().uppercase()
        if (c.isBlank()) { status = LinkStatus.Error("El enlace no es válido."); return@LaunchedEffect }
        status = withContext(Dispatchers.IO) {
            when (val r = SocialApi.fetchRoutine(c)) {
                is FetchRoutineResult.Success -> {
                    val id = RoutinePortable.import(r.payload, repo)
                    if (id != null) LinkStatus.Done(id) else LinkStatus.Error("No se pudo cargar la rutina.")
                }
                FetchRoutineResult.NotFound -> LinkStatus.Error("Esta rutina compartida ya no existe.")
                FetchRoutineResult.NetworkError -> LinkStatus.Error("Sin conexión. Inténtalo de nuevo.")
            }
        }
    }

    LaunchedEffect(status) {
        val s = status
        if (s is LinkStatus.Done) {
            navController.navigate(Routes.routineDetail(s.routineId)) {
                popUpTo(Routes.IMPORT_LINK) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Background).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (val s = status) {
            LinkStatus.Loading, is LinkStatus.Done ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Violet)
                    Spacer(Modifier.height(18.dp))
                    Text("Cargando la rutina compartida…", color = TextSecondary, fontSize = 14.sp)
                }

            is LinkStatus.Error ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No se pudo abrir", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(s.message, color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(20.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(Violet)
                            .clickable {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.IMPORT_LINK) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                            .padding(horizontal = 22.dp, vertical = 13.dp)
                    ) { Text("Ir a la app", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                }
        }
    }
}
