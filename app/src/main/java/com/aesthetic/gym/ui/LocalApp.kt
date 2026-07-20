package com.aesthetic.gym.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.aesthetic.gym.ZenitApp
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.di.AppContainer
import com.aesthetic.gym.pdf.RoutineImporter
import com.aesthetic.gym.util.RestBell

@Composable
fun appContainer(): AppContainer {
    val context = LocalContext.current
    return (context.applicationContext as ZenitApp).container
}

@Composable
fun rememberRepository(): GymRepository = appContainer().repository

@Composable
fun rememberImporter(): RoutineImporter = appContainer().importer

@Composable
fun rememberBell(): RestBell = appContainer().bell

/** Cuándo se instaló la app: distingue un regreso real de una restauración de datos. */
@Composable
fun rememberFirstSeenAt(): Long = appContainer().install.firstSeenAt
