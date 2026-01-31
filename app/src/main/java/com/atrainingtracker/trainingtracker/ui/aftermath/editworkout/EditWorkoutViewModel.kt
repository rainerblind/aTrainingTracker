package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import android.app.Application
import androidx.lifecycle.*
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.DescriptionDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderDataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EditWorkoutViewModel(application: Application, private val workoutId: Long) : AndroidViewModel(application) {

    private val workoutSummariesDatabaseManager = WorkoutSummariesDatabaseManager.getInstance()
    private val sportTypeDatabaseManager = SportTypeDatabaseManager.getInstance()
    private val equipmentDbHelper = EquipmentDbHelper(application)


    // LiveData to hold the entire WorkoutData object. The UI will observe this.
    private val _workoutData = MutableLiveData<WorkoutData>()
    val workoutData: LiveData<WorkoutData> = _workoutData

    private val headerDataProvider =
        WorkoutHeaderDataProvider(application, EquipmentDbHelper(application))
    private val detailsDataProvider = WorkoutDetailsDataProvider()
    private val extremaDataProvider = ExtremaDataProvider(application)
    private val descriptionDataProvider = DescriptionDataProvider()

    init {
        loadWorkout()
    }

    private fun loadWorkout() {
        viewModelScope.launch(Dispatchers.IO) {
            val db = workoutSummariesDatabaseManager.getOpenDatabase()
            val cursor = db.query(
                WorkoutSummaries.TABLE,
                null,
                "${WorkoutSummaries.C_ID} = ?",
                arrayOf(workoutId.toString()),
                null, null, null
            )

            if (cursor.moveToFirst()) {
                val headerData = headerDataProvider.createWorkoutHeaderData(cursor)
                val detailsData = detailsDataProvider.createWorkoutDetailsData(cursor)
                val descriptionData = descriptionDataProvider.createDescriptionData(cursor)
                val extremaData = extremaDataProvider.getExtremaDataList(cursor)

                val data = WorkoutData(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.C_ID)),
                    fileBaseName = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.FILE_BASE_NAME)),
                    isCommute = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.COMMUTE)) > 0,
                    isTrainer = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.TRAINER)) > 0,

                    headerData = headerData,
                    detailsData = detailsData,
                    descriptionData = descriptionData,
                    extremaData = extremaData
                )

                _workoutData.postValue(data)
            }
            cursor.close()
            workoutSummariesDatabaseManager.closeDatabase()
        }
    }

    fun updateSportName(newSportName: String?) {
        if (newSportName == null) return

        val currentData = _workoutData.value ?: return

        // get the sportId
        val newSportId = SportTypeDatabaseManager.getSportTypeIdFromUIName(newSportName)
        val newBSportType = SportTypeDatabaseManager.getBSportType(newSportId)

        // update the LiveData state with all the new information
        _workoutData.value = currentData.copy(
            headerData = currentData.headerData.copy(
                sportName = newSportName,
                sportId = newSportId,
                bSportType = newBSportType
            )
        )
    }

    fun updateEquipmentName(newEquipmentName: String?) {
        if (newEquipmentName == null) return

        val currentData = _workoutData.value ?: return

        _workoutData.value = currentData.copy(
            headerData = currentData.headerData.copy(
                equipmentName = newEquipmentName
            )
        )
    }

    /**
     * Updates the description data in the current WorkoutData object.
     * This keeps the state in the ViewModel updated as the user types.
     */
    fun updateDescription(newDescription: String, newGoal: String, newMethod: String) {
        // Get the current state from the LiveData. If it's null, we can't update anything.
        val currentData = _workoutData.value ?: return

        // Create an updated copy of the DescriptionData.
        val updatedDescriptionData = currentData.descriptionData.copy(
            description = newDescription,
            goal = newGoal,
            method = newMethod
        )

        // Create an updated copy of the entire WorkoutData with the new DescriptionData.
        val updatedWorkoutData = currentData.copy(
            descriptionData = updatedDescriptionData
        )

        // Post the new state to the LiveData. Any observers (like the Fragment) will be notified.
        _workoutData.value = updatedWorkoutData
    }

    /**
     * Saves the current state of the WorkoutData object to the database.
     */
    fun saveChanges() {
        // Get the most recent state from the LiveData. If it's null, there's nothing to save.
        val dataToSave = _workoutData.value ?: return

        // Launch a coroutine in the IO dispatcher to perform database operations off the main thread.
        viewModelScope.launch(Dispatchers.IO) {
            val workoutId = dataToSave.id

            // 1. Update Description, Goal (Fancy Name), and Method
            WorkoutSummariesDatabaseManager.updateDescription(
                workoutId,
                dataToSave.descriptionData.description ?: "",
                dataToSave.descriptionData.goal ?: "",
                dataToSave.descriptionData.method ?: ""
            )

            // 2. Update Sport and Equipment
            val equipmentDbHelper = EquipmentDbHelper(getApplication())
            val equipmentId = equipmentDbHelper.getEquipmentId(dataToSave.headerData.equipmentName ?: "")
            WorkoutSummariesDatabaseManager.updateSportAndEquipment(
                workoutId,
                dataToSave.headerData.sportId,
                equipmentId
            )

            // 3. Update Commute and Trainer flags
            WorkoutSummariesDatabaseManager.updateCommuteAndTrainerFlag(
                workoutId,
                dataToSave.isCommute,
                dataToSave.isTrainer
            )

        }
    }
}

class EditWorkoutViewModelFactory(private val application: Application, private val workoutId: Long) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditWorkoutViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EditWorkoutViewModel(application, workoutId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}