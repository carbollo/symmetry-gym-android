package com.aesthetic.gym.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Rowing
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.SportsHandball
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.ui.graphics.vector.ImageVector
import com.aesthetic.gym.domain.model.MuscleGroup

/** An icon per muscle group, used on the workout timeline bubbles. */
object MuscleIcons {
    fun forMuscle(muscle: MuscleGroup?): ImageVector = when (muscle) {
        MuscleGroup.CHEST -> Icons.Filled.FitnessCenter
        MuscleGroup.BACK -> Icons.Filled.Rowing
        MuscleGroup.SHOULDERS -> Icons.Filled.SportsHandball
        MuscleGroup.BICEPS -> Icons.Filled.SportsMartialArts
        MuscleGroup.TRICEPS -> Icons.Filled.SportsMma
        MuscleGroup.FOREARMS -> Icons.Filled.PanTool
        MuscleGroup.ABS -> Icons.Filled.SelfImprovement
        MuscleGroup.QUADS -> Icons.Filled.DirectionsRun
        MuscleGroup.HAMSTRINGS -> Icons.Filled.DirectionsWalk
        MuscleGroup.GLUTES -> Icons.Filled.Accessibility
        MuscleGroup.CALVES -> Icons.Filled.Hiking
        MuscleGroup.TRAPS -> Icons.Filled.SportsGymnastics
        else -> Icons.Filled.FitnessCenter
    }
}
