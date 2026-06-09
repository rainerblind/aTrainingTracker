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

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyPreferenceManager
import com.atrainingtracker.trainingtracker.ui.util.SingleLiveEvent
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.ui.aftermath.DeletionProgress
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class WorkoutSortOrder(@StringRes val labelResId: Int) {
    DATE(R.string.sort_date),
    WORKOUT_DURATION(R.string.sort_duration),
    WORKOUT_DISTANCE(R.string.sort_length),
    TOTAL_ELEVATION_GAIN(R.string.sort_elevation_gain),
}

class WorkoutSummariesViewModel(application: Application) : AndroidViewModel(application) {
    private val workoutRepo = WorkoutRepository.getInstance(application)
    private val prefManager = MyPreferenceManager(application)

    private val _sortOrder = MutableStateFlow(WorkoutSortOrder.DATE)
    val sortOrder = _sortOrder.asStateFlow()

    private var lastScrolledOrder: WorkoutSortOrder? = null

    fun shouldScrollToTop(currentOrder: WorkoutSortOrder): Boolean {
        if (lastScrolledOrder != currentOrder) {
            lastScrolledOrder = currentOrder
            return true
        }
        return false
    }

    fun setSortOrder(order: WorkoutSortOrder) {
        _sortOrder.value = order
    }

    val workouts: StateFlow<List<WorkoutData>> = combine(
        workoutRepo.allWorkouts,
        _sortOrder
    ) { workoutList, order ->
        if (workoutList.isEmpty()) {
            emptyList()
        } else {
            // Apply sorting logic
            when (order) {
                WorkoutSortOrder.DATE ->
                    workoutList.sortedByDescending { it.startTimeS }
                WorkoutSortOrder.TOTAL_ELEVATION_GAIN ->
                    workoutList.sortedByDescending { it.ascentMeters }
                WorkoutSortOrder.WORKOUT_DISTANCE ->
                    workoutList.sortedByDescending { it.totalDistance }
                WorkoutSortOrder.WORKOUT_DURATION ->
                    workoutList.sortedByDescending { it.activeTimeSec }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 2. Observe the preference from DataStore
    // We use stateIn to convert the Flow to a StateFlow for the UI
    val isCompactView: StateFlow<Boolean> = prefManager.isCompactViewFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // 3. Update the toggle logic to save to DataStore
    fun toggleCompactView() {
        viewModelScope.launch {
            val currentValue = isCompactView.value
            prefManager.setCompactView(!currentValue)
        }
    }


    // LiveData to trigger showing the "Delete Old Workouts" dialog
    val showDeleteOldWorkoutsDialogEvent = SingleLiveEvent<Unit>()

    // --- LiveData for granular deletion progress ---
    val deletionProgress: LiveData<DeletionProgress> = workoutRepo.deletionProgress


    val confirmDeleteWorkoutEvent = SingleLiveEvent<Long>()

    // only load the workouts if the list of workouts is null or empty
    fun loadWorkoutsIfNeeded() {
        if (workouts.value.isEmpty() == true) {
            loadWorkouts()
        }
    }

    fun loadWorkouts() {
        // Use the ViewModel's coroutine scope to launch on a background thread.
        viewModelScope.launch {
            workoutRepo.loadAllWorkouts()
        }
    }

    /**
     * Returns a LiveData of workouts
     * If null, returns all workouts.
     */
    fun getFilteredWorkouts(
        bSportType: BSportType? = null,
        sportTypeId: Long? = null,
        equipmentId: Long? = null,
        startTimeS: Long? = null,
        endTimeS: Long? = null
    ): Flow<List<WorkoutData>> {
        return workouts.map { list ->
            list.filter { workout ->
                val matchesBSport = bSportType == null || workout.bSportType == bSportType
                val matchesSportId = sportTypeId == null || workout.sportId == sportTypeId
                val matchesEquip = equipmentId == null || workout.equipmentId == equipmentId

                val workoutTime = workout.headerData.startTimeS
                val matchesTime = (startTimeS == null || workoutTime >= startTimeS) && (endTimeS == null || workoutTime <= endTimeS)

                matchesBSport && matchesSportId && matchesEquip && matchesTime
            }
        }
    }

    fun onDeleteWorkoutClicked(id: Long) {
        // Post an event to the LiveData. The fragment will observe this
        // and show the confirmation dialog.
        confirmDeleteWorkoutEvent.postValue(id)
    }

    /**
     * This method will be called by the Fragment *after* the user confirms the deletion.
     */
    fun deleteWorkout(id: Long) {
        workoutRepo.deleteWorkout(id)
    }


    // --- Methods for the delection of old workouts ---
    /**
     * Called from the Fragment's menu. Triggers the dialog.
     */
    fun onDeleteOldWorkoutsClicked() {
        showDeleteOldWorkoutsDialogEvent.postValue(Unit)
    }

    /**
     * Called from the Fragment AFTER the user confirms the date in the dialog.
     */
    fun executeDeleteOldWorkouts(daysToKeep: Int) {
        viewModelScope.launch {
            workoutRepo.deleteOldWorkouts(daysToKeep)
        }
    }


    fun onExportWorkoutTo(id: Long, format: FileFormat) {
        // Post an event commanding the fragment/activity to handle the export.
        exportWorkout(id, format)    }

    fun exportWorkout(workoutId: Long, fileFormat: FileFormat) {
        viewModelScope.launch {
            workoutRepo.exportWorkoutTo(workoutId, fileFormat)
        }
    }

    companion object {
        const val DEBUG = false
        const val TAG = "WorkoutSummariesViewModel"
    }
}