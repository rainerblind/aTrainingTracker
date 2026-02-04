package com.atrainingtracker.trainingtracker.ui.aftermath

import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.DescriptionData
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataRow
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsData
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaData
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderData

/**
 * A composite data class that represents all data needed for a single row in the workout list.
 * It holds the raw data AND the structured data for each component.
 */
data class WorkoutData(
    // --- Raw Data (Primary Key) ---
    val id: Long,
    val fileBaseName: String?,
    val activeTime: Long,


    // --- Composed Component Data ---
    val headerData: WorkoutHeaderData,
    val detailsData: WorkoutDetailsData,
    val descriptionData: DescriptionData,
    val extremaData: ExtremaData
)