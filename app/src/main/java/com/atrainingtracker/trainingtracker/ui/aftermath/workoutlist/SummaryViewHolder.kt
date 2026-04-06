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
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusViewHolder
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.DescriptionViewHolder
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsData
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsViewHolder
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaData
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaValuesViewHolder
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderData
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderViewHolder
import com.atrainingtracker.trainingtracker.ui.map.ATrainingTrackerMap
import com.atrainingtracker.trainingtracker.ui.map.MapState
import com.atrainingtracker.trainingtracker.ui.map.MapTrack
import com.atrainingtracker.trainingtracker.ui.map.TrackType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.map.MapViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The ViewHolder for a single workout summary row. It contains all the sub-component
 * ViewHolders and is responsible for setting up listeners and binding data to the components.
 */
class SummaryViewHolder(
    row: View,
    private val activity: Activity,
    private val fragmentManager: FragmentManager,
    private val lifecycleOwner: LifecycleOwner,
    isPlayServiceAvailable: Boolean,
    private val viewModel: WorkoutSummariesViewModel
) : RecyclerView.ViewHolder(row) {

    // --- Component ViewHolders & Components ---
    private val contentContainer: View?
    private val headerViewHolder: WorkoutHeaderViewHolder?
    private val detailsViewHolder: WorkoutDetailsViewHolder?
    private val descriptionViewHolder: DescriptionViewHolder?
    private val extremaValuesViewHolder: ExtremaValuesViewHolder?
    private val exportStatusViewHolder: ExportStatusViewHolder?

    private val mapComposeView: ComposeView?
    private val rowMapState = MutableStateFlow(MapState(isFollowMeEnabled = false))

    // The current data for this specific row, set during bind().
    private lateinit var workoutSummary: WorkoutData

    init {
        // --- Find Views ---
        contentContainer = row.findViewById(R.id.content_container)

        val headerView = row.findViewById<View>(R.id.workout_header_include)
        val detailsView = row.findViewById<View>(R.id.workout_details_include)
        val descriptionView = row.findViewById<View>(R.id.workout_description_include)
        val extremaView = row.findViewById<View>(R.id.extrema_values_include)
        val exportStatusView = row.findViewById<View>(R.id.export_status_include)
        mapComposeView = row.findViewById(R.id.workout_summaries_map_compose)

        // --- Create Component ViewHolders ---
        headerViewHolder = headerView?.let { WorkoutHeaderViewHolder(it) }
        detailsViewHolder = detailsView?.let { WorkoutDetailsViewHolder(it, activity) }
        descriptionViewHolder = descriptionView?.let { DescriptionViewHolder(it) }
        extremaValuesViewHolder = extremaView?.let { ExtremaValuesViewHolder(it) }
        exportStatusViewHolder = exportStatusView?.let { ExportStatusViewHolder(it) }

        // Call the setup method, now passing the menu button
        setupMenuButtonClickListeners(headerViewHolder?.menuButton)

        // --- Initialize Map Component ---
        val mapViewModel = MapViewModel(application = activity.application)
        if (isPlayServiceAvailable && mapComposeView != null) {
            mapComposeView.setContent {
                ATrainingTrackerTheme {
                    val state by rowMapState.collectAsState()

                    // 2. Use the new ATrainingTrackerMap
                    ATrainingTrackerMap(
                        mapState = state,
                        mapViewModel = mapViewModel,
                        currentLocationFlow = MutableStateFlow(null), // Static row
                        modifier = Modifier.fillMaxSize(),
                        onMapClick = { TrainingApplication.startTrackOnMapAftermathActivity(activity, workoutSummary.id) }
                    )
                }
            }
        } else {
            mapComposeView?.visibility = View.GONE
        }

        // --- Setup Listeners (Event Handling) ---
        setupClickListeners()
    }

    private fun setupClickListeners() {
        // This method is called only once, during ViewHolder creation.

        // create a click listener
        val detailsClickListener = View.OnClickListener {
            if (workoutSummary.headerData.finished) {  // only when tracking is finished, the EditWorkoutActivity can be opened.
                TrainingApplication.startEditWorkoutActivity(
                    workoutSummary.id,
                    false               // only show the editable fields
                )
            }
        }
        // Attach this listener to multiple views
        headerViewHolder?.view?.setOnClickListener(detailsClickListener)
        detailsViewHolder?.view?.setOnClickListener(detailsClickListener)
        extremaValuesViewHolder?.view?.setOnClickListener(detailsClickListener)
        descriptionViewHolder?.rootView?.setOnClickListener(detailsClickListener)
    }

    private fun setupMenuButtonClickListeners(menuButton: View?) {
        menuButton?.setOnClickListener { view ->
            // Create a PopupMenu, anchored to the button that was clicked.
            val popup = PopupMenu(view.context, view)
            // Inflate the same menu resource the old fragment used.
            popup.inflate(R.menu.workout_summaries_context)

            // Set a listener for when a menu item is clicked.
            popup.setOnMenuItemClickListener { item ->
                // Delegate the action to the ViewModel based on the menu item's ID.
                // This keeps the adapter clean and dumb.
                when (item.itemId) {
                    R.id.contextDelete -> {
                        // Let the ViewModel handle the deletion logic.
                        viewModel.onDeleteWorkoutClicked(workoutSummary.id)
                        true // Consume the click
                    }
                    R.id.tcxWrite -> {
                        viewModel.onExportWorkoutClicked(workoutSummary.id, FileFormat.TCX)
                        true
                    }
                    R.id.gpxWrite -> {
                        viewModel.onExportWorkoutClicked(workoutSummary.id, FileFormat.GPX)
                        true
                    }
                    R.id.csvWrite -> {
                        viewModel.onExportWorkoutClicked(workoutSummary.id, FileFormat.CSV)
                        true
                    }
                    R.id.jsonWrite -> {
                        viewModel.onExportWorkoutClicked(workoutSummary.id, FileFormat.GC)
                        true
                    }
                    R.id.stravaUpload -> {
                        viewModel.onExportWorkoutClicked(workoutSummary.id, FileFormat.STRAVA)
                        true
                    }
                    // TODO: runkeeper, trainingPeaks, ...
                    else -> false // Let the system handle other cases
                }
            }
            // Show the menu.
            popup.show()
        }
    }

    private var mapLoadJob: kotlinx.coroutines.Job? = null

    /**
     * Binds a pre-composed WorkoutSummary object to the views. This is called for each item.
     */
    fun bind(summary: WorkoutData) {
        // Store the summary for use in the click listeners.
        this.workoutSummary = summary

        // --- Pass the pre-made data objects directly to the components ---
        headerViewHolder?.bind(summary.headerData)
        detailsViewHolder?.bind(summary.detailsData)
        descriptionViewHolder?.bind(summary.descriptionData)
        extremaValuesViewHolder?.bind(summary.extremaData)
        exportStatusViewHolder?.bind(summary.fileBaseName)

        // Load the map track points asynchronously
        // Reset state and cancel previous load to prevent "ghost" tracks from recycled rows
        mapLoadJob?.cancel()
        rowMapState.value = MapState(isFollowMeEnabled = false)

        mapLoadJob = lifecycleOwner.lifecycleScope.launch {
            val points = viewModel.getWorkoutTrackPoints(summary.id)

            if (points.isNotEmpty()) {
                rowMapState.value = MapState(
                    tracks = listOf(
                        MapTrack(
                            id = summary.id,
                            path = points,
                            type = TrackType.BEST,
                            isVisible = true
                        )
                    ),
                    isFollowMeEnabled = false
                )
            }
        }

        if (!summary.headerData.finished) {
            contentContainer?.alpha = 0.5f
        }
        else {
            contentContainer?.alpha = 1.0f
        }

    }

    /**
     * A lightweight update function that only re-binds the header view.
     */
    fun updateHeader(headerData: WorkoutHeaderData) {
        headerViewHolder?.bind(headerData)
    }

    /**
     * A lightweight update function that only re-binds the details view.
     */
    fun updateDetails(detailsData: WorkoutDetailsData) {
        detailsViewHolder?.bind(detailsData)
    }

    /**
     * A lightweight update function that only re-binds the extrema values view.
     */
    fun updateExtrema(extremaData: ExtremaData) {
        extremaValuesViewHolder?.bind(extremaData)
    }
}