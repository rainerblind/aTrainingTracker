package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.trainingtracker.util.SingleLiveEvent
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.ui.aftermath.DeletionProgress
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class WorkoutSummariesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WorkoutRepository(application)

    val workouts: LiveData<List<WorkoutData>> = repository.allWorkouts

    //
    // LiveData to trigger showing the "Delete Old Workouts" dialog
    val showDeleteOldWorkoutsDialogEvent = SingleLiveEvent<Unit>()

    // --- LiveData for granular deletion progress ---
    val deletionProgress: LiveData<DeletionProgress> = repository.deletionProgress


    val confirmDeleteWorkoutEvent = SingleLiveEvent<Long>()

    fun loadWorkouts() {
        // Use the ViewModel's coroutine scope to launch on a background thread.
        viewModelScope.launch(Dispatchers.IO) {
            repository.loadAllWorkouts()
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