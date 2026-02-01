package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import android.app.Application
import androidx.compose.animation.core.copy
import androidx.lifecycle.*
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.DescriptionDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderDataProvider
import com.atrainingtracker.trainingtracker.util.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EditWorkoutViewModel(application: Application, private val workoutId: Long) : AndroidViewModel(application) {
    private val context = application

    private val workoutSummariesDatabaseManager = WorkoutSummariesDatabaseManager.getInstance()
    private val sportTypeDatabaseManager = SportTypeDatabaseManager.getInstance()
    private val equipmentDbHelper = EquipmentDbHelper(application)


    // LiveData to hold the entire WorkoutData object. The UI will observe this.
    private val _workoutData = MutableLiveData<WorkoutData>()
    val workoutData: LiveData<WorkoutData> = _workoutData
    val saveFinishedEvent = MutableLiveData<Event<Boolean>>()
    val deleteFinishedEvent = MutableLiveData<Event<Boolean>>()

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
                    activeTime = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_ACTIVE_s)),

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

    fun updateWorkoutName(newName: String) {
        val currentData = _workoutData.value ?: return
        // Avoid unnecessary updates if the text hasn't changed
        if (newName == currentData.headerData.workoutName) return

        _workoutData.value = currentData.copy(
            headerData = currentData.headerData.copy(workoutName = newName)
        )
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

    fun updateDescription(newDescription: String) {
        val currentData = _workoutData.value ?: return
        if (newDescription == currentData.descriptionData.description) return

        _workoutData.value = currentData.copy(
            descriptionData = currentData.descriptionData.copy(description = newDescription)
        )
    }

    fun updateGoal(newGoal: String) {
        val currentData = _workoutData.value ?: return
        if (newGoal == currentData.descriptionData.goal) return

        _workoutData.value = currentData.copy(
            descriptionData = currentData.descriptionData.copy(goal = newGoal)
        )
    }

    fun updateMethod(newMethod: String) {
        val currentData = _workoutData.value ?: return
        if (newMethod == currentData.descriptionData.method) return

        _workoutData.value = currentData.copy(
            descriptionData = currentData.descriptionData.copy(method = newMethod)
        )
    }

    fun updateIsCommute(isChecked: Boolean) {
        val currentData = _workoutData.value ?: return
        // Avoid unnecessary updates
        if (isChecked == currentData.isCommute) return

        _workoutData.value = currentData.copy(isCommute = isChecked)
    }

    fun updateIsTrainer(isChecked: Boolean) {
        val currentData = _workoutData.value ?: return
        // Avoid unnecessary updates
        if (isChecked == currentData.isTrainer) return

        _workoutData.value = currentData.copy(isTrainer = isChecked)
    }


    // -- fancy / auto name
    // LiveData to hold the list of fancy names for the dialog
    val fancyNameList: LiveData<List<String>> by lazy {
        MutableLiveData(WorkoutSummariesDatabaseManager.getFancyNameList())
    }

    // This function will be called when the user selects a name from the dialog.
    fun onFancyNameSelected(baseName: String) {
        val fullFancyName = WorkoutSummariesDatabaseManager.getFancyNameAndIncrement(baseName)

        updateWorkoutName(fullFancyName)
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

            // -- update the Database
            // Update Workout Name
            WorkoutSummariesDatabaseManager.updateWorkoutName(
                workoutId,
                dataToSave.headerData.workoutName ?: ""
            )

            // Update Sport and Equipment
            val equipmentDbHelper = EquipmentDbHelper(getApplication())
            val equipmentId = equipmentDbHelper.getEquipmentId(dataToSave.headerData.equipmentName ?: "")
            WorkoutSummariesDatabaseManager.updateSportAndEquipment(
                workoutId,
                dataToSave.headerData.sportId,
                equipmentId
            )

            // Update Commute and Trainer flags
            WorkoutSummariesDatabaseManager.updateCommuteAndTrainerFlag(
                workoutId,
                dataToSave.isCommute,
                dataToSave.isTrainer
            )

            // Update Description, Goal, and Method
            WorkoutSummariesDatabaseManager.updateDescription(
                workoutId,
                dataToSave.descriptionData.description ?: "",
                dataToSave.descriptionData.goal ?: "",
                dataToSave.descriptionData.method ?: ""
            )

            // -- trigger export
            val exportManager = ExportManager(application)
            exportManager.exportWorkout(dataToSave.fileBaseName)

            saveFinishedEvent.postValue(Event(true))
        }
    }

    fun deleteWorkout() {
        viewModelScope.launch(Dispatchers.IO) {
            val success = WorkoutSummariesDatabaseManager.deleteWorkout(workoutId, context)
            deleteFinishedEvent.postValue(Event(success))
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