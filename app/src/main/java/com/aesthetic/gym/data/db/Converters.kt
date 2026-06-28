package com.aesthetic.gym.data.db

import androidx.room.TypeConverter
import com.aesthetic.gym.domain.model.Equipment
import com.aesthetic.gym.domain.model.GoalType
import com.aesthetic.gym.domain.model.MuscleGroup
import com.aesthetic.gym.domain.model.PhotoPose
import com.aesthetic.gym.domain.model.RoutineSource
import com.aesthetic.gym.domain.model.Sex
import com.aesthetic.gym.domain.model.WeightUnit

class Converters {
    @TypeConverter fun muscleToString(value: MuscleGroup): String = value.name
    @TypeConverter fun stringToMuscle(value: String): MuscleGroup = MuscleGroup.valueOf(value)

    @TypeConverter
    fun muscleListToString(value: List<MuscleGroup>): String = value.joinToString(",") { it.name }

    @TypeConverter
    fun stringToMuscleList(value: String): List<MuscleGroup> =
        if (value.isBlank()) emptyList() else value.split(",").map { MuscleGroup.valueOf(it) }

    @TypeConverter fun equipmentToString(value: Equipment): String = value.name
    @TypeConverter fun stringToEquipment(value: String): Equipment = Equipment.valueOf(value)

    @TypeConverter fun sexToString(value: Sex): String = value.name
    @TypeConverter fun stringToSex(value: String): Sex = Sex.valueOf(value)

    @TypeConverter fun unitToString(value: WeightUnit): String = value.name
    @TypeConverter fun stringToUnit(value: String): WeightUnit = WeightUnit.valueOf(value)

    @TypeConverter fun sourceToString(value: RoutineSource): String = value.name
    @TypeConverter fun stringToSource(value: String): RoutineSource = RoutineSource.valueOf(value)

    @TypeConverter fun poseToString(value: PhotoPose): String = value.name
    @TypeConverter fun stringToPose(value: String): PhotoPose = PhotoPose.valueOf(value)

    @TypeConverter fun goalTypeToString(value: GoalType): String = value.name
    @TypeConverter fun stringToGoalType(value: String): GoalType = GoalType.valueOf(value)
}
