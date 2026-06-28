package com.aesthetic.gym.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.aesthetic.gym.SymmetryApp
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.di.AppContainer
import com.aesthetic.gym.pdf.RoutineImporter

@Composable
fun appContainer(): AppContainer {
    val context = LocalContext.current
    return (context.applicationContext as SymmetryApp).container
}

@Composable
fun rememberRepository(): GymRepository = appContainer().repository

@Composable
fun rememberImporter(): RoutineImporter = appContainer().importer
