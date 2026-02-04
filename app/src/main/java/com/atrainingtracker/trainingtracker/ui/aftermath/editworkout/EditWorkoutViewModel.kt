package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutDeletionHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.helpers.CalcExtremaWorker
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.DescriptionDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaData
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderDataProvider
import com.atrainingtracker.trainingtracker.util.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EditWorkoutViewModel(application: Application, private val workoutId: Long) : AndroidViewModel(application) {
    private val context = application

    private val workoutSummariesDatabaseManager = WorkoutSummariesDatabaseManager.getInstance(application)
    private val sportTypeDatabaseManager = SportTypeDatabaseManager.getInstance(application)
    private val equipmentDbHelper = EquipmentDbHelper(application)


    // LiveData to hold the entire WorkoutData object. The UI will observe this.
    private val _workoutData = MutableLiveData<WorkoutData>()
    val workoutData: LiveData<WorkoutData> = _workoutData

    // LiveData specifically for the list of extrema values ---
    private val _extremaData = MutableLiveData<ExtremaData>()
    val extremaData: LiveData<ExtremaData> = _extremaData

    // LiveData specifically for the auto-calculated workout name ---
    private val _autoWorkoutName = MutableLiveData<Event<String>>()
    val autoWorkoutName: LiveData<Event<String>> = _autoWorkoutName

    // LiveData specifically for the message from the extrema calculation worker
    private val _extremaCalculationMessage = MutableLiveData<Event<String>>()
    val extremaCalculationMessage: LiveData<Event<String>> = _extremaCalculationMessage

    val saveFinishedEvent = MutableLiveData<Event<Boolean>>()
    val deleteFinishedEvent = MutableLiveData<Event<Boolean>>()

    private val headerDataProvider =
        WorkoutHeaderDataProvider(application, EquipmentDbHelper(application))
    private val detailsDataProvider = WorkoutDetailsDataProvider(application)
    private val extremaDataProvider = ExtremaDataProvider(application)
    private val descriptionDataProvider = DescriptionDataProvider()

    // observe the calculation of the extrema values
    private val extremaDataObserver = object : Observer<List<WorkInfo>> {
        private var lastProgressSequence = -1
        override fun onChanged(workInfos: List<WorkInfo>) {
            Log.d("EditWorkoutViewModel", "called for workoutId=$workoutId")

            // Find the worker
            val workInfo = workInfos.firstOrNull() ?: return

            if (workInfo.state.isFinished) {
                Log.d("EditWorkoutViewModel", "finished calculation")
                loadExtremaData()
            } else {
                val currentProgress =
                    workInfo.progress.getInt(CalcExtremaWorker.KEY_PROGRESS_SEQUENCE, -1)
                if (currentProgress > lastProgressSequence) {
                    lastProgressSequence = currentProgress

                    // first, check for a message regarding the doing
                    val message =
                        workInfo.progress.getString(CalcExtremaWorker.KEY_STARTING_MESSAGE)
                    if (message != null) {
                        Log.d("EditWorkoutViewModel", "received message: $message")
                        _extremaCalculationMessage.postValue(Event(message))
                    } else {
                        // check the update type ---
                        val updateType =
                            workInfo.progress.getString(CalcExtremaWorker.KEY_FINISHED_MESSAGE)
                        Log.d("EditWorkoutViewModel", "received update type: $updateType")
                        if (updateType == CalcExtremaWorker.FINISHED_AUTO_NAME) {
                            // The fancy/aut name was calculated. We need to fetch it.
                            loadWorkoutName()
                        } else if (updateType == CalcExtremaWorker.FINISHED_COMMUTE_AND_TRAINER) {
                            // TODO: loadCommuteAndTrainer()
                        } else if (updateType == CalcExtremaWorker.FINISHED_EXTREMA_VALE)
                            loadExtremaData()
                    }
                }
            }
        }
    }


    init {
        loadInitialWorkout()
    }

    private fun loadWorkoutName() {
        viewModelScope.launch(Dispatchers.IO) {
            val newName = workoutSummariesDatabaseManager.getWorkoutName(workoutId)
            _autoWorkoutName.postValue(Event(newName))
        }
    }

    // Function to load only the extrema data ---
    private fun loadExtremaData() {
        viewModelScope.launch(Dispatchers.IO) {
            val db = workoutSummariesDatabaseManager.getDatabase()
            val cursor = db.query(
                WorkoutSummaries.TABLE,
                null, // We only need extrema columns, but null is fine for performance here
                "${WorkoutSummaries.C_ID} = ?",
                arrayOf(workoutId.toString()),
                null, null, null
            )

            if (cursor.moveToFirst()) {
                val newExtremaList = extremaDataProvider.getExtremaData(cursor)
                // Post the new list to the specific LiveData for the ViewHolder
                _extremaData.postValue(newExtremaList)
            }
            cursor.close()
        }
    }

    private fun loadInitialWorkout() {
        viewModelScope.launch(Dispatchers.IO) {
            val db = workoutSummariesDatabaseManager.getDatabase()
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
                val extremaData = extremaDataProvider.getExtremaData(cursor)

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

                // Post to the main LiveData for the one-time setup
                _workoutData.postValue(data)
                // Also post the initial list to the new LiveData
                _extremaData.postValue(extremaData)

                if (extremaData.isCalculating) {
                    launch(Dispatchers.Main) {
                        Log.d("EditWorkoutViewModel", "Initial setup: Attaching the single observer.")
                        val workTag = "extrema_calc_${data.id}"
                        val workManager = WorkManager.getInstance(getApplication())
                        // Remove any stale observers first, just in case.
                        workManager.getWorkInfosByTagLiveData(workTag).removeObserver(extremaDataObserver)
                        // Attach our single, persistent observer.
                        workManager.getWorkInfosByTagLiveData(workTag).observeForever(extremaDataObserver)
                    }
                }

            }
            cursor.close()
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
        val sportTypeDatabaseManager = SportTypeDatabaseManager.getInstance(context)
        val newSportId = sportTypeDatabaseManager.getSportTypeIdFromUIName(newSportName)
        val newBSportType = sportTypeDatabaseManager.getBSportType(newSportId)

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
        MutableLiveData(workoutSummariesDatabaseManager.getFancyNameList())
    }

    // This function will be called when the user selects a name from the dialog.
    fun onFancyNameSelected(baseName: String) {
        val fullFancyName = workoutSummariesDatabaseManager.getFancyNameAndIncrement(baseName)

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
            workoutSummariesDatabaseManager.updateWorkoutName(
                workoutId,
                dataToSave.headerData.workoutName ?: ""
            )

            // Update Sport and Equipment
            val equipmentDbHelper = EquipmentDbHelper(getApplication())
            val equipmentId = equipmentDbHelper.getEquipmentId(dataToSave.headerData.equipmentName ?: "")
            workoutSummariesDatabaseManager.updateSportAndEquipment(
                workoutId,
                dataToSave.headerData.sportId,
                equipmentId
            )

            // Update Commute and Trainer flags
            workoutSummariesDatabaseManager.updateCommuteAndTrainerFlag(
                workoutId,
                dataToSave.isCommute,
                dataToSave.isTrainer
            )

            // Update Description, Goal, and Method
            workoutSummariesDatabaseManager.updateDescription(
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
            val workoutDeletionHelper = WorkoutDeletionHelper(context)
            val success = workoutDeletionHelper.deleteWorkout(workoutId)
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