package com.atrainingtracker.trainingtracker.ui.workoutlist

import android.app.Application
import android.content.ContentValues
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.trainingtracker.SingleLiveEvent
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.DescriptionDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderDataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WorkoutSummariesViewModel(application: Application) : AndroidViewModel(application) {

    // Private MutableLiveData that we will post updates to.
    private val _workouts = MutableLiveData<List<WorkoutSummary>>()
    // Public LiveData that the Fragment will observe. This is immutable.
    val workouts: LiveData<List<WorkoutSummary>> = _workouts

    private val headerDataProvider = WorkoutHeaderDataProvider(application, EquipmentDbHelper(application))
    private val detailsDataProvider = WorkoutDetailsDataProvider()
    private val extremaDataProvider = ExtremaDataProvider(application)
    private val descriptionDataProvider = DescriptionDataProvider()


    val confirmDeleteWorkoutEvent = SingleLiveEvent<Long>()
    val exportWorkoutEvent = SingleLiveEvent<Pair<Long, FileFormat>>()

    fun loadWorkouts() {
        // Use the ViewModel's coroutine scope to launch on a background thread.
        viewModelScope.launch(Dispatchers.IO) {
            val summaryList = mutableListOf<WorkoutSummary>()
            val db = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase()
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
                        val detailsData = detailsDataProvider.createWorkoutDetailsData(c)
                        val descriptionData = descriptionDataProvider.createDescriptionData(c)
                        val extremaData = extremaDataProvider.getExtremaDataList(c)

                        summaryList.add(
                            WorkoutSummary(
                                id = c.getLong(c.getColumnIndexOrThrow(WorkoutSummaries.C_ID)),
                                fileBaseName = c.getString(c.getColumnIndexOrThrow(WorkoutSummaries.FILE_BASE_NAME)),
                                headerData = headerData,
                                detailsData = detailsData,
                                descriptionData = descriptionData,
                                extremaData = extremaData
                            )
                        )
                    } while (c.moveToNext())
                }
            }
            WorkoutSummariesDatabaseManager.getInstance().closeDatabase()

            // Post the final list to the LiveData. This will update the UI on the main thread.
            _workouts.postValue(summaryList)
        }
    }

    /**
     * Updates the name of a workout in the database and reloads the list.
     */
    fun updateWorkoutName(id: Long, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase()
            val values = ContentValues().apply {
                put(WorkoutSummaries.WORKOUT_NAME, newName)
            }
            db.update(WorkoutSummaries.TABLE, values, "${WorkoutSummaries.C_ID} = ?", arrayOf(id.toString()))
            WorkoutSummariesDatabaseManager.getInstance().closeDatabase()

            // After updating, reload the data to reflect the change.
            loadWorkouts()
        }
    }

    /**
     * Updates the description, goal, and method of a workout in the database and reloads the list.
     */
    fun updateDescription(id: Long, newDescription: String, newGoal: String, newMethod: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase()
            val values = ContentValues().apply {
                put(WorkoutSummaries.DESCRIPTION, newDescription)
                put(WorkoutSummaries.GOAL, newGoal)
                put(WorkoutSummaries.METHOD, newMethod)
            }
            db.update(WorkoutSummaries.TABLE, values, "${WorkoutSummaries.C_ID} = ?", arrayOf(id.toString()))
            WorkoutSummariesDatabaseManager.getInstance().closeDatabase()

            // Reload to show the updated data.
            loadWorkouts()
        }
    }

    /**
     * Updates the sport and equipment for a workout in the database and reloads the list.
     */
    fun updateSportAndEquipment(id: Long, newSportId: Long, newEquipmentId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase()
            val values = ContentValues().apply {
                put(WorkoutSummaries.SPORT_ID, newSportId)
                put(WorkoutSummaries.EQUIPMENT_ID, newEquipmentId)
            }
            db.update(WorkoutSummaries.TABLE, values, "${WorkoutSummaries.C_ID} = ?", arrayOf(id.toString()))
            WorkoutSummariesDatabaseManager.getInstance().closeDatabase()

            // Reload to show the updated sport icon and equipment name.
            loadWorkouts()
        }
    }


    fun onDeleteWorkoutClicked(id: Long) {
        // Post an event to the LiveData. The fragment will observe this
        // and show the confirmation dialog.
        confirmDeleteWorkoutEvent.postValue(id)
    }

    fun onExportWorkoutClicked(id: Long, format: FileFormat) {
        // Post an event commanding the fragment/activity to handle the export.
        exportWorkoutEvent.postValue(Pair(id, format))
    }

    /**
     * This method will be called by the Fragment *after* the user confirms the deletion.
     */
    fun deleteWorkout(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase()
            // Using DeleteWorkoutThread is good practice if it does more than just a DB delete.
            // For a simple delete, this is also fine:
            db.delete(WorkoutSummaries.TABLE, "${WorkoutSummaries.C_ID} = ?", arrayOf(id.toString()))
            WorkoutSummariesDatabaseManager.getInstance().closeDatabase()

            // After deleting, reload the data. The UI will update automatically.
            loadWorkouts()
        }
    }
}