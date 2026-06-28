package com.aesthetic.gym.data.repo

import com.aesthetic.gym.data.db.AppDatabase
import com.aesthetic.gym.data.db.BodyMetricEntity
import com.aesthetic.gym.data.db.BodyPhotoEntity
import com.aesthetic.gym.data.db.ExerciseEntity
import com.aesthetic.gym.data.db.GoalEntity
import com.aesthetic.gym.data.db.ProfileEntity
import com.aesthetic.gym.data.db.RoutineDayEntity
import com.aesthetic.gym.data.db.RoutineEntity
import com.aesthetic.gym.data.db.RoutineItemEntity
import com.aesthetic.gym.data.db.SetLogEntity
import com.aesthetic.gym.data.db.WorkoutSessionEntity
import com.aesthetic.gym.data.seed.ExerciseCatalog

/** Single point of access to the database for the rest of the app. */
class GymRepository(private val db: AppDatabase) {

    private val exerciseDao = db.exerciseDao()
    private val routineDao = db.routineDao()
    private val workoutDao = db.workoutDao()
    private val bodyDao = db.bodyDao()
    private val profileDao = db.profileDao()
    private val goalDao = db.goalDao()

    fun now(): Long = System.currentTimeMillis()

    // ---- Exercises ----
    val exercisesFlow = exerciseDao.allFlow()
    suspend fun getAllExercises() = exerciseDao.getAll()
    suspend fun getExercise(id: String) = exerciseDao.byId(id)
    suspend fun upsertExercise(exercise: ExerciseEntity) = exerciseDao.upsert(exercise)
    suspend fun updateExerciseLastWeight(id: String, weightKg: Double) =
        exerciseDao.updateLastWeight(id, weightKg)
    suspend fun updateExerciseLastReps(id: String, reps: Int) =
        exerciseDao.updateLastReps(id, reps)

    suspend fun ensureSeeded() {
        if (exerciseDao.count() == 0) {
            exerciseDao.upsertAll(ExerciseCatalog.entities)
        }
    }

    // ---- Routines ----
    fun routinesFlow() = routineDao.routinesFlow()
    fun activeRoutineFlow() = routineDao.activeRoutineFlow()
    suspend fun activeRoutine() = routineDao.activeRoutine()
    fun routineWithDaysFlow(id: Long) = routineDao.routineWithDaysFlow(id)
    suspend fun routineWithDays(id: Long) = routineDao.routineWithDays(id)

    suspend fun setActiveRoutine(id: Long) {
        routineDao.clearActive()
        routineDao.markActive(id)
    }

    suspend fun deleteRoutine(id: Long) = routineDao.deleteRoutine(id)
    suspend fun insertRoutine(routine: RoutineEntity): Long = routineDao.insertRoutine(routine)
    suspend fun insertDay(day: RoutineDayEntity): Long = routineDao.insertDay(day)
    suspend fun insertItem(item: RoutineItemEntity): Long = routineDao.insertItem(item)

    // ---- Workout sessions ----
    suspend fun startSession(routineId: Long?, dayId: Long?, name: String): Long =
        workoutDao.insertSession(
            WorkoutSessionEntity(routineId = routineId, dayId = dayId, name = name, startedAt = now())
        )

    suspend fun finishSession(id: Long) = workoutDao.finishSession(id, now())
    suspend fun deleteSession(id: Long) = workoutDao.deleteSession(id)
    suspend fun sessionById(id: Long) = workoutDao.sessionById(id)
    fun sessionWithSetsFlow(id: Long) = workoutDao.sessionWithSetsFlow(id)
    fun recentSessionsFlow(limit: Int) = workoutDao.recentSessionsFlow(limit)
    fun allFinishedSessionsFlow() = workoutDao.allFinishedSessionsFlow()
    fun finishedSessionsWithSetsFlow() = workoutDao.finishedSessionsWithSetsFlow()
    fun finishedCountFlow() = workoutDao.finishedCountFlow()
    suspend fun setCountForSession(sessionId: Long) = workoutDao.setCountForSession(sessionId)

    suspend fun addSet(set: SetLogEntity): Long = workoutDao.insertSet(set.copy(createdAt = now()))
    suspend fun updateSet(set: SetLogEntity) = workoutDao.updateSet(set)
    suspend fun deleteSet(id: Long) = workoutDao.deleteSet(id)
    suspend fun lastSetsForExercise(exerciseId: String, exceptSessionId: Long) =
        workoutDao.lastSetsForExercise(exerciseId, exceptSessionId)

    fun setMuscleRowsFlow() = workoutDao.setMuscleRowsFlow()
    fun setsWithDateFlow(exerciseId: String) = workoutDao.setsWithDateFlow(exerciseId)
    fun loggedExerciseIdsFlow() = workoutDao.loggedExerciseIdsFlow()

    // ---- Body progress ----
    fun photosFlow() = bodyDao.photosFlow()
    suspend fun addPhoto(photo: BodyPhotoEntity): Long = bodyDao.insertPhoto(photo)
    suspend fun photoById(id: Long) = bodyDao.photoById(id)
    suspend fun deletePhoto(id: Long) = bodyDao.deletePhoto(id)
    fun metricsFlow() = bodyDao.metricsFlow()
    suspend fun addMetric(metric: BodyMetricEntity) = bodyDao.insertMetric(metric)
    suspend fun deleteMetric(id: Long) = bodyDao.deleteMetric(id)

    // ---- Profile ----
    fun profileFlow() = profileDao.profileFlow()
    suspend fun getProfile() = profileDao.getProfile()
    suspend fun saveProfile(profile: ProfileEntity) = profileDao.upsert(profile)

    // ---- Goals ----
    fun goalsFlow() = goalDao.goalsFlow()
    suspend fun insertGoal(goal: GoalEntity): Long = goalDao.insert(goal)
    suspend fun updateGoal(goal: GoalEntity) = goalDao.update(goal)
    suspend fun deleteGoal(id: Long) = goalDao.delete(id)
}
