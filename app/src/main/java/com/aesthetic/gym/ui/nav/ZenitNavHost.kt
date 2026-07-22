package com.aesthetic.gym.ui.nav

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.aesthetic.gym.ui.theme.Background
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.Violet
import com.aesthetic.gym.ui.body.BodyScreen
import com.aesthetic.gym.ui.exercises.ExerciseDetailScreen
import com.aesthetic.gym.ui.exercises.ExercisesScreen
import com.aesthetic.gym.ui.goals.GoalsScreen
import com.aesthetic.gym.ui.history.HistoryScreen
import com.aesthetic.gym.ui.history.SessionDetailScreen
import com.aesthetic.gym.ui.account.AccountSettingsScreen
import com.aesthetic.gym.ui.account.AuthScreen
import com.aesthetic.gym.ui.friends.FriendsScreen
import com.aesthetic.gym.ui.friends.PublicProfileScreen
import com.aesthetic.gym.ui.galaxy.GalaxyMapScreen
import com.aesthetic.gym.ui.galaxy.GalaxyScreen
import com.aesthetic.gym.ui.home.HomeScreen
import com.aesthetic.gym.ui.profile.ProfileScreen
import com.aesthetic.gym.ui.progress.ProgressScreen
import com.aesthetic.gym.ui.routines.CreateRoutineScreen
import com.aesthetic.gym.ui.routines.ImportScreen
import com.aesthetic.gym.ui.routines.ImportLinkScreen
import com.aesthetic.gym.ui.routines.RoutineDetailScreen
import com.aesthetic.gym.ui.routines.RoutinesScreen
import com.aesthetic.gym.ui.workout.WorkoutScreen

@Composable
fun ZenitRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomRoutes

    // Enlaces de rutina que llegan con la app ya abierta (singleTop → onNewIntent). El arranque en
    // frío lo maneja el NavHost solo; esto cubre el caso en caliente sin recrear la Activity.
    val context = LocalContext.current
    DisposableEffect(navController, context) {
        val activity = context as? ComponentActivity
        val listener = Consumer<Intent> { intent -> navController.handleDeepLink(intent) }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (showBottomBar) {
                ZenitBottomBar(navController, currentRoute)
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
            composable(Routes.BODY) { BodyScreen(navController) }
            composable(Routes.PROGRESS) { ProgressScreen(navController) }
            composable(Routes.PROFILE) { ProfileScreen(navController) }
            composable(
                Routes.AUTH,
                arguments = listOf(navArgument("mode") {
                    type = NavType.StringType
                    defaultValue = "login"
                })
            ) { entry ->
                AuthScreen(navController, entry.arguments?.getString("mode") ?: "login")
            }
            composable(Routes.GOALS) { GoalsScreen(navController) }
            composable(Routes.HISTORY) { HistoryScreen(navController) }
            composable(Routes.GALAXY) { GalaxyScreen(navController) }
            composable(Routes.GALAXY_MAP) { GalaxyMapScreen(navController) }
            composable(Routes.FRIENDS) { FriendsScreen(navController) }
            composable(Routes.ACCOUNT_SETTINGS) { AccountSettingsScreen(navController) }
            composable(
                Routes.USER_PROFILE,
                arguments = listOf(navArgument("username") { type = NavType.StringType })
            ) { entry ->
                PublicProfileScreen(navController, entry.arguments?.getString("username") ?: "")
            }
            composable(
                Routes.IMPORT_LINK,
                arguments = listOf(navArgument("code") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink {
                    uriPattern = "https://social-api-production-8ff7.up.railway.app/r/{code}"
                })
            ) { entry ->
                ImportLinkScreen(navController, entry.arguments?.getString("code") ?: "")
            }
            composable(Routes.IMPORT) { ImportScreen(navController) }
            composable(Routes.CREATE_ROUTINE) { CreateRoutineScreen(navController) }
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
            composable(
                Routes.SESSION_DETAIL,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { entry ->
                SessionDetailScreen(
                    navController,
                    entry.arguments?.getLong("sessionId") ?: 0L
                )
            }
        }
    }
}

@Composable
private fun ZenitBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar(containerColor = Surface, tonalElevation = 0.dp) {
        bottomDestinations.forEach { dest ->
            val selected = currentRoute == dest.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (currentRoute != dest.route) {
                        navController.navigate(dest.route) {
                            // Deterministic tab switching: always land on the tapped tab.
                            popUpTo(Routes.HOME) { inclusive = dest.route == Routes.HOME }
                            launchSingleTop = true
                        }
                    }
                },
                icon = { Icon(dest.icon, contentDescription = dest.label) },
                label = { Text(dest.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Violet,
                    selectedTextColor = Violet,
                    indicatorColor = Violet.copy(alpha = 0.18f),
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted
                )
            )
        }
    }
}
