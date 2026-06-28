package com.aesthetic.gym.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Projection: a logged set together with the date of its session (for progress charts). */
data class SetWithDate(
    val weightKg: Double,
    val reps: Int,
    val date: Long
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun profileFlow(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun getProfile(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name")
    fun allFlow(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun byId(id: String): ExerciseEntity?

    @Upsert
    suspend fun upsert(exercise: ExerciseEntity)

    @Upsert
    suspend fun upsertAll(exercises: List<ExerciseEntity>)
}

@Dao
interface RoutineDao {
    @Insert
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Insert
    suspend fun insertDay(day: RoutineDayEntity): Long

    @Insert
    suspend fun insertItem(item: RoutineItemEntity): Long

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Query("UPDATE routines SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE routines SET isActive = 1 WHERE id = :id")
    suspend fun markActive(id: Long)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: Long)

    @Query("SELECT * FROM routines ORDER BY createdAt DESC")
    fun routinesFlow(): Flow<List<RoutineEntity>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    fun routineWithDaysFlow(id: Long): Flow<RoutineWithDays?>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun routineWithDays(id: Long): RoutineWithDays?

    @Transaction
    @Query("SELECT * FROM routines WHERE isActive = 1 LIMIT 1")
    fun activeRoutineFlow(): Flow<RoutineWithDays?>

    @Transaction
    @Query("SELECT * FROM routines WHERE isActive = 1 LIMIT 1")
    suspend fun activeRoutine(): RoutineWithDays?
}

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Query("UPDATE sessions SET finishedAt = :finishedAt WHERE id = :id")
    suspend fun finishSession(id: Long, finishedAt: Long)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Insert
    suspend fun insertSet(set: SetLogEntity): Long

    @Update
    suspend fun updateSet(set: SetLogEntity)

    @Query("DELETE FROM set_logs WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun sessionById(id: Long): WorkoutSessionEntity?

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    fun sessionWithSetsFlow(id: Long): Flow<SessionWithSets?>

    @Query("SELECT * FROM sessions WHERE finishedAt IS NOT NULL ORDER BY startedAt DESC LIMIT :limit")
    fun recentSessionsFlow(limit: Int): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM sessions WHERE finishedAt IS NOT NULL ORDER BY startedAt DESC")
    fun allFinishedSessionsFlow(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT COUNT(*) FROM sessions WHERE finishedAt IS NOT NULL")
    fun finishedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM set_logs WHERE sessionId = :sessionId")
    suspend fun setCountForSession(sessionId: Long): Int

    /** Sets from the most recent finished session that contained this exercise (excluding [exceptSessionId]). */
    @Query(
        """
        SELECT * FROM set_logs
        WHERE exerciseId = :exerciseId AND completed = 1 AND isWarmup = 0
          AND sessionId = (
            SELECT s.id FROM sessions s
            JOIN set_logs sl ON sl.sessionId = s.id
            WHERE sl.exerciseId = :exerciseId AND s.id != :exceptSessionId AND s.finishedAt IS NOT NULL
            ORDER BY s.startedAt DESC LIMIT 1
          )
        ORDER BY setNumber
        """
    )
    suspend fun lastSetsForExercise(exerciseId: String, exceptSessionId: Long): List<SetLogEntity>

    @Query(
        """
        SELECT sl.exerciseId AS exerciseId, sl.weightKg AS weightKg, sl.reps AS reps,
               e.primaryMuscle AS primaryMuscle, e.isCompound AS isCompound
        FROM set_logs sl
        JOIN exercises e ON e.id = sl.exerciseId
        WHERE sl.completed = 1 AND sl.isWarmup = 0
        """
    )
    fun setMuscleRowsFlow(): Flow<List<SetMuscleRow>>

    @Query(
        """
        SELECT sl.weightKg AS weightKg, sl.reps AS reps, s.startedAt AS date
        FROM set_logs sl
        JOIN sessions s ON s.id = sl.sessionId
        WHERE sl.exerciseId = :exerciseId AND sl.completed = 1 AND sl.isWarmup = 0
              AND s.finishedAt IS NOT NULL
        ORDER BY s.startedAt
        """
    )
    fun setsWithDateFlow(exerciseId: String): Flow<List<SetWithDate>>

    @Query("SELECT DISTINCT exerciseId FROM set_logs")
    fun loggedExerciseIdsFlow(): Flow<List<String>>
}

@Dao
interface BodyDao {
    @Query("SELECT * FROM body_photos ORDER BY takenAt DESC")
    fun photosFlow(): Flow<List<BodyPhotoEntity>>

    @Insert
    suspend fun insertPhoto(photo: BodyPhotoEntity): Long

    @Query("SELECT * FROM body_photos WHERE id = :id")
    suspend fun photoById(id: Long): BodyPhotoEntity?

    @Query("DELETE FROM body_photos WHERE id = :id")
    suspend fun deletePhoto(id: Long)

    @Query("SELECT * FROM body_metrics ORDER BY takenAt")
    fun metricsFlow(): Flow<List<BodyMetricEntity>>

    @Insert
    suspend fun insertMetric(metric: BodyMetricEntity): Long
}
