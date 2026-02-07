package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import android.R.id
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.atrainingtracker.trainingtracker.SingleLiveEvent
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutDeletionHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.ui.aftermath.DeletionProgress
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.DescriptionDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderDataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date


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