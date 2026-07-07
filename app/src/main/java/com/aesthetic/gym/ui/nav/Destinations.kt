package com.aesthetic.gym.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val HOME = "home"
    const val ROUTINES = "routines"
    const val EXERCISES = "exercises"
    const val BODY = "body"
    const val PROGRESS = "progress"

    const val IMPORT = "import"
    const val CREATE_ROUTINE = "create_routine"
    const val PROFILE = "profile"
    const val GOALS = "goals"
    const val HISTORY = "history"
    const val ROUTINE_DETAIL = "routine/{routineId}"
    const val WORKOUT = "workout/{sessionId}"
    const val EXERCISE_DETAIL = "exercise/{exerciseId}"

    fun routineDetail(routineId: Long) = "routine/$routineId"
    fun workout(sessionId: Long) = "workout/$sessionId"
    fun exerciseDetail(exerciseId: String) = "exercise/$exerciseId"
}

data class BottomDest(val route: String, val label: String, val icon: ImageVector)

val bottomDestinations = listOf(
    BottomDest(Routes.HOME, "Inicio", Icons.Filled.Home),
    BottomDest(Routes.ROUTINES, "Rutinas", Icons.AutoMirrored.Filled.ListAlt),
    BottomDest(Routes.EXERCISES, "Ejercicios", Icons.Filled.FitnessCenter),
    BottomDest(Routes.BODY, "Cuerpo", Icons.Filled.Accessibility),
    BottomDest(Routes.PROGRESS, "Progreso", Icons.Filled.Insights)
)

val bottomRoutes = bottomDestinations.map { it.route }.toSet()
