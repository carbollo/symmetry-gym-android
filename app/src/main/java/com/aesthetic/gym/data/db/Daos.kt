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

/** Flattened row used to export the whole training history to CSV. */
data class ExportRow(
    val date: Long,
    val sessionName: String,
    val exercise: String,
    val muscle: String,
    val setNumber: Int,
    val isWarmup: Boolean,
    val measure: String,
    val weightKg: Double,
    val reps: Int,
    val rpe: Double?,
    val completed: Boolean
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

    @Query("UPDATE exercises SET lastWeightKg = :weightKg WHERE id = :id")
    suspend fun updateLastWeight(id: String, weightKg: Double)

    @Query("UPDATE exercises SET lastReps = :reps WHERE id = :id")
    suspend fun updateLastReps(id: String, reps: Int)

    @Query("UPDATE exercises SET measure = :measure WHERE id = :id")
    suspend fun updateMeasure(id: String, measure: com.aesthetic.gym.domain.model.MeasureType)

    @Query("UPDATE exercises SET loadPoints = :points WHERE id = :id")
    suspend fun updateLoadPoints(id: String, points: Int)

    @Query("UPDATE exercises SET defaultRestSeconds = :seconds WHERE id = :id")
    suspend fun updateRest(id: String, seconds: Int)

    @Query("UPDATE exercises SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: String, notes: String?)
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

    @Update
    suspend fun updateDay(day: RoutineDayEntity)

    @Update
    suspend fun updateItem(item: RoutineItemEntity)

    /** Borrar un día arrastra sus ejercicios (FK ON DELETE CASCADE). */
    @Query("DELETE FROM routine_days WHERE id = :id")
    suspend fun deleteDay(id: Long)

    @Query("DELETE FROM routine_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("UPDATE routines SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE routines SET isActive = 1 WHERE id = :id")
    suspend fun markActive(id: Long)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: Long)

    @Query("UPDATE routine_items SET restSeconds = :seconds WHERE id = :itemId")
    suspend fun updateItemRest(itemId: Long, seconds: Int)

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

    @Transaction
    @Query("SELECT * FROM sessions WHERE finishedAt IS NOT NULL ORDER BY startedAt DESC")
    fun finishedSessionsWithSetsFlow(): Flow<List<SessionWithSets>>

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

    /** All-time best working set for an exercise (heaviest, then most reps). */
    @Query(
        """
        SELECT * FROM set_logs
        WHERE exerciseId = :exerciseId AND completed = 1 AND isWarmup = 0
        ORDER BY weightKg DESC, reps DESC LIMIT 1
        """
    )
    suspend fun bestSetForExercise(exerciseId: String): SetLogEntity?

    /** Every set (in order) from the most recent previous session that contained this exercise. */
    @Query(
        """
        SELECT * FROM set_logs
        WHERE exerciseId = :exerciseId
          AND sessionId = (
            SELECT s.id FROM sessions s
            JOIN set_logs sl ON sl.sessionId = s.id
            WHERE sl.exerciseId = :exerciseId AND s.id != :exceptSessionId
            ORDER BY s.startedAt DESC LIMIT 1
          )
        ORDER BY setNumber
        """
    )
    suspend fun previousSetsForExercise(exerciseId: String, exceptSessionId: Long): List<SetLogEntity>

    @Query(
        """
        SELECT sl.exerciseId AS exerciseId, sl.weightKg AS weightKg, sl.reps AS reps,
               e.primaryMuscle AS primaryMuscle, e.isCompound AS isCompound
        FROM set_logs sl
        JOIN exercises e ON e.id = sl.exerciseId
        WHERE sl.completed = 1 AND sl.isWarmup = 0 AND sl.measure = 'REPS'
        """
    )
    fun setMuscleRowsFlow(): Flow<List<SetMuscleRow>>

    @Query(
        """
        SELECT sl.weightKg AS weightKg, sl.reps AS reps, s.startedAt AS date
        FROM set_logs sl
        JOIN sessions s ON s.id = sl.sessionId
        WHERE sl.exerciseId = :exerciseId AND sl.completed = 1 AND sl.isWarmup = 0
              AND sl.measure = 'REPS' AND s.finishedAt IS NOT NULL
        ORDER BY s.startedAt
        """
    )
    fun setsWithDateFlow(exerciseId: String): Flow<List<SetWithDate>>

    @Query("SELECT DISTINCT exerciseId FROM set_logs")
    fun loggedExerciseIdsFlow(): Flow<List<String>>

    /** Whether a real workout (finished + a confirmed set) happened within [start, end). */
    @Query(
        """
        SELECT COUNT(*) FROM sessions s
        WHERE s.finishedAt IS NOT NULL AND s.startedAt >= :start AND s.startedAt < :end
          AND EXISTS (SELECT 1 FROM set_logs sl WHERE sl.sessionId = s.id AND sl.completed = 1)
        """
    )
    suspend fun trainedBetween(start: Long, end: Long): Int

    /** dayId of the last finished session, to suggest the next day of the routine. */
    @Query("SELECT dayId FROM sessions WHERE finishedAt IS NOT NULL AND dayId IS NOT NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun lastFinishedDayId(): Long?

    /** Every set of a session, read directly: safe even with no collectors on the flow. */
    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY id")
    suspend fun setsForSession(sessionId: Long): List<SetLogEntity>

    /** Start dates of past sessions that were actually trained (finished, with a confirmed set). */
    @Query(
        """
        SELECT s.startedAt FROM sessions s
        WHERE s.id != :exceptSessionId AND s.finishedAt IS NOT NULL
          AND EXISTS (SELECT 1 FROM set_logs sl WHERE sl.sessionId = s.id AND sl.completed = 1)
        ORDER BY s.startedAt DESC LIMIT :limit
        """
    )
    suspend fun recentLoggedSessionStarts(exceptSessionId: Long, limit: Int): List<Long>

    @Query(
        """
        SELECT COUNT(*) FROM sessions s
        WHERE s.id != :exceptSessionId AND s.finishedAt IS NOT NULL
          AND EXISTS (SELECT 1 FROM set_logs sl WHERE sl.sessionId = s.id AND sl.completed = 1)
        """
    )
    suspend fun loggedSessionCount(exceptSessionId: Long): Int

    @Query("UPDATE sessions SET comebackDays = :days WHERE id = :id")
    suspend fun markComeback(id: Long, days: Int)

    @Query("SELECT comebackDays FROM sessions WHERE id = :id")
    suspend fun comebackDays(id: Long): Int?

    /** Every logged set, flattened with session and exercise data, for the CSV export. */
    @Query(
        """
        SELECT s.startedAt AS date, s.name AS sessionName, e.name AS exercise,
               e.primaryMuscle AS muscle, sl.setNumber AS setNumber, sl.isWarmup AS isWarmup,
               sl.measure AS measure, sl.weightKg AS weightKg, sl.reps AS reps,
               sl.rpe AS rpe, sl.completed AS completed
        FROM set_logs sl
        JOIN sessions s ON s.id = sl.sessionId
        JOIN exercises e ON e.id = sl.exerciseId
        ORDER BY s.startedAt DESC, e.name, sl.setNumber
        """
    )
    suspend fun exportRows(): List<ExportRow>
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

    @Query("DELETE FROM body_metrics WHERE id = :id")
    suspend fun deleteMetric(id: Long)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY done ASC, createdAt DESC")
    fun goalsFlow(): Flow<List<GoalEntity>>

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: Long)
}
