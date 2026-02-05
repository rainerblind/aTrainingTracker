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
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.DescriptionDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderDataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date


class WorkoutSummariesViewModel(application: Application) : AndroidViewModel(application) {

    // Private MutableLiveData that we will post updates to.
    private val _workouts = MutableLiveData<List<WorkoutData>>()
    // Public LiveData that the Fragment will observe. This is immutable.
    val workouts: LiveData<List<WorkoutData>> = _workouts

    //
    // LiveData to trigger showing the "Delete Old Workouts" dialog
    val showDeleteOldWorkoutsDialogEvent = SingleLiveEvent<Unit>()

    // --- LiveData for granular deletion progress ---
    private val _deletionProgress = MutableLiveData<DeletionProgress>(DeletionProgress.Idle)
    val deletionProgress: LiveData<DeletionProgress> = _deletionProgress

    private val workoutSummariesDatabaseManager = WorkoutSummariesDatabaseManager.getInstance(application)

    private val headerDataProvider = WorkoutHeaderDataProvider(application, EquipmentDbHelper(application))
    private val detailsDataProvider = WorkoutDetailsDataProvider(application)
    private val extremaDataProvider = ExtremaDataProvider(application)
    private val descriptionDataProvider = DescriptionDataProvider()


    val confirmDeleteWorkoutEvent = SingleLiveEvent<Long>()

    fun loadWorkouts() {
        // Use the ViewModel's coroutine scope to launch on a background thread.
        viewModelScope.launch(Dispatchers.IO) {
            val summaryList = mutableListOf<WorkoutData>()

            val db = workoutSummariesDatabaseManager.getDatabase()
            val cursor = db.query(
                WorkoutSummaries.TABLE,
                null, null, null, null, null,
                WorkoutSummaries.TIME_START + " DESC"
            )

            // Safely iterate through the cursor and convert each row to a data object
            cursor.use { c ->
                if (c.moveToFirst()) {
                    do {
                        val headerData = headerDataProvider.createWorkoutHeaderData(c)
                        val detailsData = detailsDataProvider.getWorkoutDetailsData(c)
                        val descriptionData = descriptionDataProvider.createDescriptionData(c)
                        val extremaData = extremaDataProvider.getExtremaData(c)

                        summaryList.add(
                            WorkoutData(
                                id = c.getLong(c.getColumnIndexOrThrow(WorkoutSummaries.C_ID)),
                                fileBaseName = c.getString(c.getColumnIndexOrThrow(WorkoutSummaries.FILE_BASE_NAME)),
                                activeTime = c.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_ACTIVE_s)),

                                headerData = headerData,
                                detailsData = detailsData,
                                descriptionData = descriptionData,
                                extremaData = extremaData,
                            )
                        )
                    } while (c.moveToNext())
                }
            }

            // Post the final list to the LiveData. This will update the UI on the main thread.
            _workouts.postValue(summaryList)
        }

        // After the initial list is loaded and posted, check for any ongoing calculations.
        workouts.value?.forEach { workoutData ->
            if (workoutData.extremaData.isCalculating) {
                observeExtremaCalculation(workoutData.id)
            }
        }
    }

    private fun observeExtremaCalculation(workoutId: Long) {
        val workManager = WorkManager.getInstance(getApplication())
        val workTag = "extrema_calc_${workoutId}"

        // Observe the worker's status using LiveData, which is lifecycle-aware.
        // We use observeForever because the ViewModel's lifecycle is longer than the View's.
        val observer = object : androidx.lifecycle.Observer<List<WorkInfo>> {
            override fun onChanged(workInfos: List<WorkInfo>) {
                val workInfo = workInfos.firstOrNull() ?: return

                val isRunning = workInfo.state == WorkInfo.State.RUNNING
                val isFinished = workInfo.state.isFinished

                // If work is running or has just finished, it means the database has new data.
                // We reload the workouts to reflect the latest state.
                if (isRunning || isFinished) {
                    // Trigger a reload from the database. The `loadWorkouts` function will
                    // handle getting the new data and posting it to the UI.
                    loadWorkouts()
                }

                // If the work is finished, we don't need to observe it anymore.
                if (isFinished) {
                    workManager.getWorkInfosByTagLiveData(workTag).removeObserver(this)
                }
            }
        }

        workManager.getWorkInfosByTagLiveData(workTag).observeForever(observer)
    }

    /**
     * Updates the name of a workout in the database and reloads the list.
     */
    fun updateWorkoutName(id: Long, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            workoutSummariesDatabaseManager.updateWorkoutName(id, newName)

            // After updating, reload the data to reflect the change.
            loadWorkouts()
        }
    }

    /**
     * Updates the description, goal, and method of a workout in the database and reloads the list.
     */
    fun updateDescription(id: Long, newDescription: String, newGoal: String, newMethod: String) {
        viewModelScope.launch(Dispatchers.IO) {
            workoutSummariesDatabaseManager.updateDescription(id, newDescription, newGoal, newMethod)

            // Reload to show the updated data.
            loadWorkouts()
        }
    }

    /**
     * Updates the sport and equipment for a workout in the database and reloads the list.
     */
    fun updateSportAndEquipment(id: Long, newSportId: Long, newEquipmentId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            workoutSummariesDatabaseManager.updateSportAndEquipment(id, newSportId, newEquipmentId)

            // Reload to show the updated sport icon and equipment name.
            loadWorkouts()
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Find the workout name *before* deleting it.
                val workout = _workouts.value?.find { it.id == id }
                val workoutName = workout?.headerData?.workoutName ?: "Workout ID: $id"

                // --- START PROGRESS ---
                // Post the detailed progress to the LiveData.
                _deletionProgress.postValue(DeletionProgress.InProgress(workoutName, id))

                // Perform the actual deletion.
                val workoutDeletionHelper = WorkoutDeletionHelper(getApplication())
                val success = workoutDeletionHelper.deleteWorkout(id)

                // After deleting, reload the data so the UI updates automatically.
                if (success) {
                    loadWorkouts()
                }

            } finally {
                // --- END PROGRESS ---
                // Reset the state to Idle when done or if an error occurs.
                _deletionProgress.postValue(DeletionProgress.Idle)
            }
        }
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val workoutDeletionHelper = WorkoutDeletionHelper(getApplication())

                // The callback lambda that will be executed inside the helper.
                val progressCallback: (Long) -> Unit = { workoutId ->
                    // Find the workout name from the current list to display it.
                    val workout = _workouts.value?.find { it.id == workoutId }
                    val workoutName = workout?.headerData?.workoutName ?: "Workout ID: $workoutId"

                    // Post the detailed progress to the LiveData.
                    _deletionProgress.postValue(DeletionProgress.InProgress(workoutName, workoutId))
                }

                val success = workoutDeletionHelper.deleteOldWorkouts(daysToKeep, progressCallback)

                // After deleting, reload the data so the UI updates automatically.
                if (success) {
                    loadWorkouts()
                }
            } finally {
                // Reset the state to Idle when done or if an error occurs.
                _deletionProgress.postValue(DeletionProgress.Idle)
            }
        }
    }


    fun onExportWorkoutClicked(id: Long, format: FileFormat) {
        // Post an event commanding the fragment/activity to handle the export.
        exportWorkout(id, format)    }

    fun exportWorkout(workoutId: Long, fileFormat: FileFormat) {
        viewModelScope.launch(Dispatchers.IO) {
            val exportManager = ExportManager(application)
            exportManager.exportWorkoutTo(workoutId, fileFormat)
        }
    }
}