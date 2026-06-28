package com.aesthetic.gym.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val HOME = "home"
    const val ROUTINES = "routines"
    const val BODY = "body"
    const val PROGRESS = "progress"
    const val PROFILE = "profile"
    const val IMPORT = "import"
    const val ROUTINE_DETAIL = "routine/{routineId}"
    const val WORKOUT = "workout/{sessionId}"

    fun routineDetail(routineId: Long) = "routine/$routineId"
    fun workout(sessionId: Long) = "workout/$sessionId"
}

data class BottomDest(val route: String, val label: String, val icon: ImageVector)

val bottomDestinations = listOf(
    BottomDest(Routes.HOME, "Inicio", Icons.Filled.Home),
    BottomDest(Routes.ROUTINES, "Rutinas", Icons.Filled.FitnessCenter),
    BottomDest(Routes.BODY, "Cuerpo", Icons.Filled.Accessibility),
    BottomDest(Routes.PROGRESS, "Progreso", Icons.Filled.Insights),
    BottomDest(Routes.PROFILE, "Perfil", Icons.Filled.Person)
)

val bottomRoutes = bottomDestinations.map { it.route }.toSet()
