package com.atrainingtracker.trainingtracker.ui.aftermath

import androidx.recyclerview.widget.DiffUtil
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsData
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaData
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderData

/* Defines the types of partial updates that can occur on a workout item.
* This is used with DiffUtil payloads to perform efficient UI updates.
*/
sealed class WorkoutUpdatePayload {
    /** Indicates that only the ExtremaData has changed. */
    data class HeaderChanged(val newHeaderData: WorkoutHeaderData) : WorkoutUpdatePayload()
    data class DetailsChanged(val newDetailsData: WorkoutDetailsData) : WorkoutUpdatePayload()
    data class ExtremaChanged(val newExtremaData: ExtremaData) : WorkoutUpdatePayload()

}

/**
 * A DiffUtil.ItemCallback to help ListAdapter determine which items in a list of workouts have changed.
 * This enables efficient updates and animations, especially for partial updates via payloads.
 */
class WorkoutDiffCallback : DiffUtil.ItemCallback<WorkoutData>() {
    override fun areItemsTheSame(oldItem: WorkoutData, newItem: WorkoutData): Boolean {
        // The ID is the unique identifier for an item.
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: WorkoutData, newItem: WorkoutData): Boolean {
        // The data class's generated `equals` method compares all properties.
        return oldItem == newItem
    }

    override fun getChangePayload(oldItem: WorkoutData, newItem: WorkoutData): Any? {
        // This is called by DiffUtil only if areItemsTheSame() is true and
        // areContentsTheSame() is false.
        val payloads = mutableListOf<WorkoutUpdatePayload>()

        // Check for a change in the header data.
        if (oldItem.headerData != newItem.headerData) {
            payloads.add(WorkoutUpdatePayload.HeaderChanged(newItem.headerData))
        }

        // Check for a change in the details data.
        if (oldItem.detailsData != newItem.detailsData) {
            payloads.add(WorkoutUpdatePayload.DetailsChanged(newItem.detailsData))
        }

        // Check for a change in the extrema data.
        if (oldItem.extremaData != newItem.extremaData) {
            payloads.add(WorkoutUpdatePayload.ExtremaChanged(newItem.extremaData))
        }

        // If the list of payloads is not empty, return it.
        // Otherwise, return null to trigger a full re-bind as a fallback.
        return if (payloads.isNotEmpty()) payloads else null
    }
}