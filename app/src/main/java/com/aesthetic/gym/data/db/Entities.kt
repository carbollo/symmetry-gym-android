package com.aesthetic.gym.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aesthetic.gym.domain.model.Equipment
import com.aesthetic.gym.domain.model.MuscleGroup
import com.aesthetic.gym.domain.model.PhotoPose
import com.aesthetic.gym.domain.model.RoutineSource
import com.aesthetic.gym.domain.model.Sex
import com.aesthetic.gym.domain.model.WeightUnit

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val sex: Sex = Sex.MALE,
    val bodyweightKg: Double = 75.0,
    val heightCm: Double = 175.0,
    val unit: WeightUnit = WeightUnit.KG,
    val experienceLevel: Int = 1,
    val onboarded: Boolean = false,
    val createdAt: Long = 0L
)

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val equipment: Equipment = Equipment.OTHER,
    val isCompound: Boolean = false,
    val instructions: String = "",
    val isCustom: Boolean = false
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val source: RoutineSource = RoutineSource.MANUAL,
    val notes: String? = null,
    val isActive: Boolean = false,
    val createdAt: Long = 0L
)

@Entity(
    tableName = "routine_days",
    foreignKeys = [ForeignKey(
        entity = RoutineEntity::class,
        parentColumns = ["id"],
        childColumns = ["routineId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("routineId")]
)
data class RoutineDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val name: String,
    val orderIndex: Int
)

@Entity(
    tableName = "routine_items",
    foreignKeys = [ForeignKey(
        entity = RoutineDayEntity::class,
        parentColumns = ["id"],
        childColumns = ["dayId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("dayId"), Index("exerciseId")]
)
data class RoutineItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val exerciseId: String,
    val orderIndex: Int,
    val targetSets: Int = 3,
    val repsMin: Int = 8,
    val repsMax: Int = 12,
    val targetWeightKg: Double? = null,
    val restSeconds: Int? = null,
    val amrap: Boolean = false,
    val rawText: String = "",
    val notes: String? = null
)

@Entity(
    tableName = "sessions",
    indices = [Index("routineId"), Index("dayId")]
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long? = null,
    val dayId: Long? = null,
    val name: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val notes: String? = null
)

@Entity(
    tableName = "set_logs",
    foreignKeys = [ForeignKey(
        entity = WorkoutSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId"), Index("exerciseId")]
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val routineItemId: Long? = null,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Double,
    val rpe: Double? = null,
    val isWarmup: Boolean = false,
    val completed: Boolean = true,
    val createdAt: Long = 0L
)

@Entity(tableName = "body_photos")
data class BodyPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val takenAt: Long,
    val pose: PhotoPose = PhotoPose.FRONT,
    val weightKg: Double? = null,
    val note: String? = null
)

@Entity(tableName = "body_metrics")
data class BodyMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val takenAt: Long,
    val weightKg: Double
)
