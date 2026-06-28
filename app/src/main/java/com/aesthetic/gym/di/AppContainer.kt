package com.aesthetic.gym.di

import android.content.Context
import com.aesthetic.gym.data.db.AppDatabase
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.pdf.RoutineImporter

/** Simple manual dependency container (kept lightweight instead of pulling in a DI framework). */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.get(context)
    val repository: GymRepository = GymRepository(database)
    val importer: RoutineImporter = RoutineImporter(repository)
}
