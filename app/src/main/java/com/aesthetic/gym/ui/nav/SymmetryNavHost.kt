package com.aesthetic.gym.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aesthetic.gym.ui.theme.Accent
import com.aesthetic.gym.ui.theme.Background
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.body.BodyScreen
import com.aesthetic.gym.ui.exercises.ExerciseDetailScreen
import com.aesthetic.gym.ui.exercises.ExercisesScreen
import com.aesthetic.gym.ui.goals.GoalsScreen
import com.aesthetic.gym.ui.history.HistoryScreen
import com.aesthetic.gym.ui.home.HomeScreen
import com.aesthetic.gym.ui.profile.ProfileScreen
import com.aesthetic.gym.ui.progress.ProgressScreen
import com.aesthetic.gym.ui.routines.ImportScreen
import com.aesthetic.gym.ui.routines.RoutineDetailScreen
import com.aesthetic.gym.ui.routines.RoutinesScreen
import com.aesthetic.gym.ui.workout.WorkoutScreen

@Composable
fun SymmetryRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomRoutes

    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (showBottomBar) {
                SymmetryBottomBar(navController, currentRoute)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) { HomeScreen(navController) }
            composable(Routes.ROUTINES) { RoutinesScreen(navController) }
            composable(Routes.EXERCISES) { ExercisesScreen(navController) }
            composable(Routes.BODY) { BodyScreen() }
            composable(Routes.PROGRESS) { ProgressScreen(navController) }
            composable(Routes.PROFILE) { ProfileScreen(navController) }
            composable(Routes.GOALS) { GoalsScreen(navController) }
            composable(Routes.HISTORY) { HistoryScreen(navController) }
            composable(Routes.IMPORT) { ImportScreen(navController) }
            composable(
                Routes.EXERCISE_DETAIL,
                arguments = listOf(navArgument("exerciseId") { type = NavType.StringType })
            ) { entry ->
                ExerciseDetailScreen(navController, entry.arguments?.getString("exerciseId") ?: "")
            }
            composable(
                Routes.ROUTINE_DETAIL,
                arguments = listOf(navArgument("routineId") { type = NavType.LongType })
            ) { entry ->
                RoutineDetailScreen(
                    navController,
                    entry.arguments?.getLong("routineId") ?: 0L
                )
            }
            composable(
                Routes.WORKOUT,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { entry ->
                WorkoutScreen(
                    navController,
                    entry.arguments?.getLong("sessionId") ?: 0L
                )
            }
        }
    }
}

@Composable
private fun SymmetryBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar(containerColor = Surface, tonalElevation = 0.dp) {
        bottomDestinations.forEach { dest ->
            val selected = currentRoute == dest.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(dest.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(dest.icon, contentDescription = dest.label) },
                label = { Text(dest.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Accent,
                    selectedTextColor = Accent,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted
                )
            )
        }
    }
}
