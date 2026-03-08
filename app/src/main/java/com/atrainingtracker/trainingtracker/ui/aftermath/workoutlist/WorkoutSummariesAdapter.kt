/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.ListAdapter
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDiffCallback
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutUpdatePayload

/**
 * A modern ListAdapter for the workout summaries RecyclerView.
 * It is decoupled from data creation and relies on a pre-composed WorkoutSummary object.
 *
 * @param activity The host activity.
 * @param fragmentManager The FragmentManager from the hosting fragment, used to show dialogs.
 * @param isPlayServiceAvailable A flag to determine if the map component should be initialized.
 * @param viewModel The ViewModel, used to dispatch user events like 'update name'.
 */
class WorkoutSummariesAdapter(
    private val activity: Activity,
    private val fragmentManager: FragmentManager,
    private val lifecycleOwner: LifecycleOwner,
    private val isPlayServiceAvailable: Boolean,
    private val viewModel: WorkoutSummariesViewModel
) : ListAdapter<WorkoutData, SummaryViewHolder>(WorkoutDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SummaryViewHolder {
        val row = LayoutInflater.from(parent.context)
            .inflate(R.layout.workout_summaries_row, parent, false)
        // Create the ViewHolder and inject only the dependencies it needs for UI and event handling.
        return SummaryViewHolder(
            row,
            activity,
            fragmentManager,
            lifecycleOwner,
            isPlayServiceAvailable,
            viewModel
        )
    }

    override fun onBindViewHolder(
        holder: SummaryViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            // If the payloads list is empty, it means this is a full bind.
            // We call the other onBindViewHolder to render the entire item from scratch.
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // Payloads are present, so we can perform one or more partial updates.
            // The payload from our DiffUtil is expected to be a List<WorkoutUpdatePayload>.
            payloads.forEach { payload ->
                if (payload is List<*>) {
                    // Iterate through the actual change events inside the list.
                    payload.forEach { item ->
                        when (item) {
                            is WorkoutUpdatePayload.HeaderDataChanged -> {
                                // This is a header-only update.
                                // Call a specific, lightweight update function in the ViewHolder.
                                holder.updateHeader(item.newHeaderData)
                            }

                            is WorkoutUpdatePayload.DetailsDataChanged -> {
                                // This is a details-only update.
                                holder.updateDetails(item.newDetailsData)
                            }

                            is WorkoutUpdatePayload.ExtremaDataChanged -> {
                                // This is an extrema-only update.
                                holder.updateExtrema(item.newExtremaData)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onBindViewHolder(holder: SummaryViewHolder, position: Int) {
        val workoutSummary = getItem(position)
        holder.bind(workoutSummary)
    }
}
