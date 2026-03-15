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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.util.SingleLiveEvent
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.ui.aftermath.DeletionProgress
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class WorkoutSummariesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WorkoutRepository.getInstance(application)

    val workouts: LiveData<List<WorkoutData>> = repository.allWorkouts

    // Loading State
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    //
    // LiveData to trigger showing the "Delete Old Workouts" dialog
    val showDeleteOldWorkoutsDialogEvent = SingleLiveEvent<Unit>()

    // --- LiveData for granular deletion progress ---
    val deletionProgress: LiveData<DeletionProgress> = repository.deletionProgress


    val confirmDeleteWorkoutEvent = SingleLiveEvent<Long>()

    fun loadWorkouts() {
        _isLoading.value = true // Show spinner
        // Use the ViewModel's coroutine scope to launch on a background thread.
        viewModelScope.launch(Dispatchers.IO) {
            repository.loadAllWorkouts()
        }
        _isLoading.value = false // Hide spinner
    }

    /**
     * Returns a LiveData of workouts filtered by the base sport type.
     * If null, returns all workouts.
     */
    fun getWorkoutsForTab(bSportType: BSportType?): LiveData<List<WorkoutData>> {
        return if (bSportType == null) {
            workouts
        } else {
            // Use the .map extension function directly on the LiveData object
            workouts.map { list ->
                list.filter { it.sportData.bSportType == bSportType }
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
        repository.deleteWorkout(id)
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
            repository.deleteOldWorkouts(daysToKeep)
        }
    }


    fun onExportWorkoutClicked(id: Long, format: FileFormat) {
        // Post an event commanding the fragment/activity to handle the export.
        exportWorkout(id, format)    }

    fun exportWorkout(workoutId: Long, fileFormat: FileFormat) {
        viewModelScope.launch {
            repository.exportWorkoutTo(workoutId, fileFormat)
        }
    }
}