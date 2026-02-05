package com.atrainingtracker.trainingtracker.ui.aftermath

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext



import com.atrainingtracker.trainingtracker.database.WorkoutDeletionHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.helpers.CalcExtremaWorker
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.DescriptionDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsData
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaData
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderData
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderDataProvider
import com.atrainingtracker.trainingtracker.util.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


/**
 * A repository that acts as a single source of truth for workout data.
 * It abstracts the data source (database) from the ViewModels.
 */
class WorkoutRepository(private val application: Application) : CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + job

    // Helper instances, initialized lazily
    private val deletionHelper by lazy { WorkoutDeletionHelper(application) }
    private val summariesManager by lazy { WorkoutSummariesDatabaseManager.getInstance(application) }

    private val mapper by lazy {
        // Create instances of the required providers
        val headerProvider = WorkoutHeaderDataProvider(application, EquipmentDbHelper(application))
        val detailsProvider = WorkoutDetailsDataProvider(application)
        val extremaProvider = ExtremaDataProvider(application)
        val descriptionProvider = DescriptionDataProvider()

        // Inject them into the mapper
        WorkoutDataMapper(headerProvider, detailsProvider, descriptionProvider, extremaProvider)
    }

    // --- LiveData for Data and Progress ---

    // LiveData for the single, currently edited workout
    private val _workoutData = MutableLiveData<WorkoutData>()
    val workoutData: LiveData<WorkoutData> = _workoutData

    // LiveData specifically for the header
    private val _headerData = MutableLiveData<Pair<Long, WorkoutHeaderData>>()
    val headerData: LiveData<Pair<Long, WorkoutHeaderData>> = _headerData

    // LiveData specifically for the details
    private val _detailsData = MutableLiveData<Pair<Long, WorkoutDetailsData>>()
    val detailsData: LiveData<Pair<Long, WorkoutDetailsData>> = _detailsData

    // LiveData specifically for the list of extrema values ---
    private val _extremaData = MutableLiveData<Pair<Long, ExtremaData>>()
    val extremaData: LiveData<Pair<Long, ExtremaData>> = _extremaData



    // LiveData specifically for the message from the extrema calculation worker
    private val _extremaCalculationMessage = MutableLiveData<Event<String>>()
    val extremaCalculationMessage: LiveData<Event<String>> = _extremaCalculationMessage


    val saveFinishedEvent = MutableLiveData<Event<Boolean>>()
    val deleteFinishedEvent = MutableLiveData<Event<Boolean>>()


    var workoutIdExtremaCalculation = -1L
    // observe the calculation of the extrema values
    private val extremaDataObserver = object : Observer<List<WorkInfo>> {
        private var lastProgressSequence = -1
        override fun onChanged(workInfos: List<WorkInfo>) {
            Log.d("EditWorkoutViewModel", "called for workoutId=$workoutIdExtremaCalculation")

            // Find the worker
            val workInfo = workInfos.firstOrNull() ?: return

            if (workInfo.state.isFinished) {
                Log.d("EditWorkoutViewModel", "finished calculation")
                loadHeaderData(workoutIdExtremaCalculation)
                loadDetailsAndExtremaData(workoutIdExtremaCalculation)
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
                        _extremaCalculationMessage.postValue(Event(message, workoutIdExtremaCalculation))
                    } else {
                        // check the update type ---
                        val updateType =
                            workInfo.progress.getString(CalcExtremaWorker.KEY_FINISHED_MESSAGE)
                        Log.d("EditWorkoutViewModel", "received update type: $updateType")
                        if (updateType == CalcExtremaWorker.FINISHED_AUTO_NAME ||
                            updateType == CalcExtremaWorker.FINISHED_COMMUTE_AND_TRAINER) {
                            loadHeaderData(workoutIdExtremaCalculation)
                        } else if (updateType == CalcExtremaWorker.FINISHED_EXTREMA_VALE) {
                            loadDetailsAndExtremaData(workoutIdExtremaCalculation)
                        }
                    }
                }
            }
        }
    }


    // --- Public API for ViewModels ---

    /**
     * Loads a single workout by its ID into the repository's LiveData.
     */
    suspend fun loadWorkout(id: Long) {
        withContext(Dispatchers.IO) {
            val cursor = summariesManager.getWorkoutCursor(id)
            if (cursor != null && cursor.moveToFirst()) {
                // get the workoutData
                val data = mapper.fromCursor(cursor)

                // Post it
                _workoutData.postValue(data)

                // eventually, observe the extrema calculation
                if (data.extremaData.isCalculating) {
                    workoutIdExtremaCalculation = data.id

                    launch(Dispatchers.Main) {
                        Log.d("EditWorkoutViewModel", "Initial setup: Attaching the single observer.")
                        val workTag = "extrema_calc_${data.id}"
                        val workManager = WorkManager.getInstance(application)
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

    // Function to load only the header data
    private fun loadHeaderData(workoutId: Long) {
        launch(Dispatchers.IO) {
            val cursor = summariesManager.getWorkoutCursor(workoutId)
            if (cursor.moveToFirst()) {
                val data = mapper.fromCursor(cursor)

                _headerData.postValue(Pair(workoutId, data.headerData))
            }
            cursor.close()
        }
    }

    // Function to load only the extrema data ---
    private fun loadDetailsAndExtremaData(workoutId: Long) {
        launch(Dispatchers.IO) {
            val cursor = summariesManager.getWorkoutCursor(workoutId)
            if (cursor.moveToFirst()) {
                val data = mapper.fromCursor(cursor)

                _extremaData.postValue(Pair(workoutId, data.extremaData))
                _detailsData.postValue(Pair(workoutId, data.detailsData))
            }
            cursor.close()
        }
    }

    fun updateWorkoutName(newName: String) {
        val currentData = _workoutData.value ?: return
        if (newName == currentData.headerData.workoutName) return

        val updatedData = currentData.copy(
            headerData = currentData.headerData.copy(workoutName = newName)
        )
        _workoutData.postValue(updatedData)
    }

    fun updateSportName(newSportName: String?) {
        if (newSportName == null) return

        val currentData = _workoutData.value ?: return

        // get the sportId
        val sportTypeDatabaseManager = SportTypeDatabaseManager.getInstance(application)
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
        if (isChecked == currentData.headerData.commute) return

        _workoutData.value = currentData.copy(
            headerData = currentData.headerData.copy(commute = isChecked)
        )
    }

    fun updateIsTrainer(isChecked: Boolean) {
        val currentData = _workoutData.value ?: return
        // Avoid unnecessary updates
        if (isChecked == currentData.headerData.trainer) return

        _workoutData.value = currentData.copy(
            headerData = currentData.headerData.copy(trainer = isChecked)
        )
    }

    /**
     * Saves the current state of the WorkoutData object to the database.
     */
    fun saveWorkout() {
        // Get the most recent state from the LiveData. If it's null, there's nothing to save.
        val dataToSave = _workoutData.value ?: return

        // Launch a coroutine in the IO dispatcher to perform database operations off the main thread.
        launch(Dispatchers.IO) {
            val workoutId = dataToSave.id

            // -- update the Database
            // Update Workout Name
            summariesManager.updateWorkoutName(
                workoutId,
                dataToSave.headerData.workoutName ?: ""
            )

            // Update Sport and Equipment
            val equipmentDbHelper = EquipmentDbHelper(application)
            val equipmentId = equipmentDbHelper.getEquipmentId(dataToSave.headerData.equipmentName ?: "")
            summariesManager.updateSportAndEquipment(
                workoutId,
                dataToSave.headerData.sportId,
                equipmentId
            )

            // Update Commute and Trainer flags
            summariesManager.updateCommuteAndTrainerFlag(
                workoutId,
                dataToSave.headerData.commute,
                dataToSave.headerData.trainer
            )

            // Update Description, Goal, and Method
            summariesManager.updateDescription(
                workoutId,
                dataToSave.descriptionData.description ?: "",
                dataToSave.descriptionData.goal ?: "",
                dataToSave.descriptionData.method ?: ""
            )

            // -- trigger export
            val exportManager = ExportManager(application)
            exportManager.exportWorkout(dataToSave.fileBaseName)

            saveFinishedEvent.postValue(Event(true, workoutId))
        }
    }

    fun deleteWorkout() {
        val dataToSave = _workoutData.value ?: return

        launch(Dispatchers.IO) {
            val workoutId = dataToSave.id
            val success = deletionHelper.deleteWorkout(workoutId)
            deleteFinishedEvent.postValue(Event(success, workoutId))
        }
    }





}