package com.atrainingtracker.trainingtracker.ui.aftermath

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext



import com.atrainingtracker.trainingtracker.database.WorkoutDeletionHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.exporter.FileFormat
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

    companion object {
        private val TAG = WorkoutRepository::class.java.simpleName
        private val DEBUG = TrainingApplication.getDebug(true)
    }

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

    // LiveData for all workouts
    private val _allWorkouts = MutableLiveData<List<WorkoutData>>()
    val allWorkouts: LiveData<List<WorkoutData>> = _allWorkouts

    /**
     * Returns a LiveData object that contains only the workout with the specified ID.
     * This is derived from the main 'allWorkouts' list.
     */
    fun getWorkoutById(id: Long): LiveData<WorkoutData?> {
        return allWorkouts.map { list ->
            list.find { it.id == id }
        }
    }

    // LiveData for the one-time initial load event
    private val _initialWorkoutLoaded = MutableLiveData<Event<WorkoutData>>()
    val initialWorkoutLoaded: LiveData<Event<WorkoutData>> = _initialWorkoutLoaded

    // LiveData to trigger the header, details, and extrema view to update
    private val _headerDataUpdated = MutableLiveData<Event<WorkoutHeaderData>>()
    val headerDataUpdated: LiveData<Event<WorkoutHeaderData>> = _headerDataUpdated

    private val _detailsDataUpdated = MutableLiveData<Event<WorkoutDetailsData>>()
    val detailsDataUpdated: LiveData<Event<WorkoutDetailsData>> = _detailsDataUpdated

    private val _extremaDataUpdated = MutableLiveData<Event<ExtremaData>>()
    val extremaDataUpdated: LiveData<Event<ExtremaData>> = _extremaDataUpdated



    // LiveData specifically for the message from the extrema calculation worker
    private val _extremaCalculationMessage = MutableLiveData<Pair<Long, String>>()
    val extremaCalculationMessage: LiveData<Pair<Long, String>> = _extremaCalculationMessage


    // --- LiveData for granular deletion progress ---
    private val _deletionProgress = MutableLiveData<DeletionProgress>(DeletionProgress.Idle)
    val deletionProgress: LiveData<DeletionProgress> = _deletionProgress



    val saveFinishedEvent = MutableLiveData<Pair<Long, Boolean>>()
    val deleteFinishedEvent = MutableLiveData<Pair<Long, Boolean>>()


    var workoutIdExtremaCalculation = -1L
    // observe the calculation of the extrema values
    private val extremaDataObserver = object : Observer<List<WorkInfo>> {
        private var lastProgressSequence = -1
        override fun onChanged(workInfos: List<WorkInfo>) {
            Log.d(TAG, "called for workoutId=$workoutIdExtremaCalculation")

            // Find the worker
            val workInfo = workInfos.firstOrNull() ?: return

            if (workInfo.state.isFinished) {
                Log.d(TAG, "finished calculation")
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
                        Log.d(TAG, "received message: $message")
                        _extremaCalculationMessage.postValue(Pair(workoutIdExtremaCalculation, message))
                    } else {
                        // check the update type ---
                        val updateType =
                            workInfo.progress.getString(CalcExtremaWorker.KEY_FINISHED_MESSAGE)
                        Log.d(TAG, "received update type: $updateType")
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


    private fun observeExtremaCalculation(workoutId: Long) {
        workoutIdExtremaCalculation = workoutId
        Log.d(TAG, "Attaching the single observer.")
        launch(Dispatchers.Main) {
            Log.d(TAG, "Attaching the single observer on main thread.")
            val workTag = "extrema_calc_${workoutId}"
            val workManager = WorkManager.getInstance(application)
            // Remove any stale observers first, just in case.
            workManager.getWorkInfosByTagLiveData(workTag).removeObserver(extremaDataObserver)
            // Attach our single, persistent observer.
            workManager.getWorkInfosByTagLiveData(workTag).observeForever(extremaDataObserver)
        }
    }

    // --- Public API for ViewModels ---

    /**
     * Loads a single workout by its ID into the repository's LiveData.
     */
    suspend fun loadWorkout(id: Long) {
        withContext(Dispatchers.IO) {
            summariesManager.getWorkoutCursor(id).use { cursor ->
                if (cursor?.moveToFirst() == true) {
                    val workout = mapper.fromCursor(cursor)
                    _allWorkouts.postValue(listOf(workout))
                    _initialWorkoutLoaded.postValue(Event(workout))

                    // eventually, observe the extrema calculation
                    if (workout.extremaData.isCalculating) {
                        if (DEBUG) Log.i(TAG, "Starting to observe extrema calculation for workout ${workout.id} on main thread")
                        observeExtremaCalculation(id)
                    }
                } else {
                    _allWorkouts.postValue(emptyList())
                }
            }
        }
    }


    suspend fun loadAllWorkouts() {
        withContext(Dispatchers.IO) {
            val summaryList = mutableListOf<WorkoutData>()
            val cursor = summariesManager.getCursorForAllWorkouts()
            // Safely iterate through the cursor and convert each row to a data object
            cursor.use { c ->
                if (c.moveToFirst()) {
                    do {
                        val data = mapper.fromCursor(c)
                        summaryList.add( data)
                    } while (c.moveToNext())
                }
            }
            cursor.close()

            // Post the final list to the LiveData. This will update the UI on the main thread.
            _allWorkouts.postValue(summaryList)
        }

        // After the initial list is loaded and posted, check for any ongoing calculations.
        allWorkouts.value?.forEach { workoutData ->
            if (workoutData.extremaData.isCalculating) {
                observeExtremaCalculation(workoutData.id)
            }
        }
    }

    private fun updateWorkoutInList(workoutId: Long, updatedWorkout: WorkoutData) {
        val currentList = _allWorkouts.value ?: return
        val updatedList = currentList.map {
            if (it.id == workoutId) updatedWorkout else it
        }
        _allWorkouts.postValue(updatedList)
    }

    // Function to update the workout data and trigger an update in the UI
    private fun loadHeaderData(workoutId: Long) {
        launch(Dispatchers.IO) {
            summariesManager.getWorkoutCursor(workoutId).use { cursor ->
                if (cursor?.moveToFirst() == true) {
                    val freshWorkoutData = mapper.fromCursor(cursor)
                    updateWorkoutInList(workoutId, freshWorkoutData)
                    _headerDataUpdated.postValue(Event(freshWorkoutData.headerData))
                }
            }
        }
    }

    // Function to update the workout data and trigger an update in the UI
    private fun loadDetailsAndExtremaData(workoutId: Long) {
        launch(Dispatchers.IO) {
            summariesManager.getWorkoutCursor(workoutId).use { cursor ->
                if (cursor?.moveToFirst() == true) {
                    val freshWorkoutData = mapper.fromCursor(cursor)
                    if (DEBUG) Log.d(TAG, "loadDetailsAndExtremaData: got freshWorkoutData=$freshWorkoutData")

                    updateWorkoutInList(workoutId, freshWorkoutData)

                    _detailsDataUpdated.postValue(Event(freshWorkoutData.detailsData))
                    _extremaDataUpdated.postValue(Event(freshWorkoutData.extremaData))
                }
            }
        }
    }


    // Function to update the workout name of one workout
    fun updateWorkoutName(workoutId: Long, newName: String) {
        val currentList = _allWorkouts.value ?: return
        val workoutToUpdate = currentList.find { it.id == workoutId } ?: return
        if (newName == workoutToUpdate.headerData.workoutName) return

        val updatedWorkout = workoutToUpdate.copy(
            headerData = workoutToUpdate.headerData.copy(workoutName = newName)
        )
        updateWorkoutInList(workoutId, updatedWorkout)
    }

    fun updateSportName(workoutId: Long, newSportName: String?) {
        if (newSportName == null) return
        val currentList = _allWorkouts.value ?: return
        val workoutToUpdate = currentList.find { it.id == workoutId } ?: return

        val sportTypeDatabaseManager = SportTypeDatabaseManager.getInstance(application)
        val newSportId = sportTypeDatabaseManager.getSportTypeIdFromUIName(newSportName)
        val newBSportType = sportTypeDatabaseManager.getBSportType(newSportId)

        val updatedWorkout = workoutToUpdate.copy(
            headerData = workoutToUpdate.headerData.copy(
                sportName = newSportName,
                sportId = newSportId,
                bSportType = newBSportType
            )
        )
        updateWorkoutInList(workoutId, updatedWorkout)
    }


    fun updateEquipmentName(workoutId: Long, newEquipmentName: String?) {
        if (newEquipmentName == null) return
        val currentList = _allWorkouts.value ?: return
        val workoutToUpdate = currentList.find { it.id == workoutId } ?: return

        val updatedWorkout = workoutToUpdate.copy(
            headerData = workoutToUpdate.headerData.copy(equipmentName = newEquipmentName)
        )
        updateWorkoutInList(workoutId, updatedWorkout)
    }

    fun updateDescription(workoutId: Long, newDescription: String) {
        val currentList = _allWorkouts.value ?: return
        val workoutToUpdate = currentList.find { it.id == workoutId } ?: return
        if (newDescription == workoutToUpdate.descriptionData.description) return

        val updatedWorkout = workoutToUpdate.copy(
            descriptionData = workoutToUpdate.descriptionData.copy(description = newDescription)
        )
        updateWorkoutInList(workoutId, updatedWorkout)
    }

    fun updateGoal(workoutId: Long, newGoal: String) {
        val currentList = _allWorkouts.value ?: return
        val workoutToUpdate = currentList.find { it.id == workoutId } ?: return
        if (newGoal == workoutToUpdate.descriptionData.goal) return

        val updatedWorkout = workoutToUpdate.copy(
            descriptionData = workoutToUpdate.descriptionData.copy(goal = newGoal)
        )
        updateWorkoutInList(workoutId, updatedWorkout)
    }

    fun updateMethod(workoutId: Long, newMethod: String) {
        val currentList = _allWorkouts.value ?: return
        val workoutToUpdate = currentList.find { it.id == workoutId } ?: return
        if (newMethod == workoutToUpdate.descriptionData.method) return

        val updatedWorkout = workoutToUpdate.copy(
            descriptionData = workoutToUpdate.descriptionData.copy(method = newMethod)
        )
        updateWorkoutInList(workoutId, updatedWorkout)
    }

    fun updateIsCommute(workoutId: Long, isChecked: Boolean) {
        val currentList = _allWorkouts.value ?: return
        val workoutToUpdate = currentList.find { it.id == workoutId } ?: return
        if (isChecked == workoutToUpdate.headerData.commute) return

        val updatedWorkout = workoutToUpdate.copy(
            headerData = workoutToUpdate.headerData.copy(commute = isChecked)
        )
        updateWorkoutInList(workoutId, updatedWorkout)
    }

    fun updateIsTrainer(workoutId: Long, isChecked: Boolean) {
        val currentList = _allWorkouts.value ?: return
        val workoutToUpdate = currentList.find { it.id == workoutId } ?: return
        if (isChecked == workoutToUpdate.headerData.trainer) return

        val updatedWorkout = workoutToUpdate.copy(
            headerData = workoutToUpdate.headerData.copy(trainer = isChecked)
        )
        updateWorkoutInList(workoutId, updatedWorkout)
    }

    /**
     * Saves the current state of the WorkoutData object to the database.
     */
    fun saveWorkout(workoutId: Long) {
        // Get the most recent state from the LiveData. If it's null, there's nothing to save.
        val dataToSave = allWorkouts.value?.find { it.id == workoutId } ?: return

        // Launch a coroutine in the IO dispatcher to perform database operations off the main thread.
        launch(Dispatchers.IO) {

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

            saveFinishedEvent.postValue(Pair(workoutId, true))
        }
    }

    suspend fun exportWorkoutTo(workoutId: Long, fileFormat: FileFormat) {
        withContext(Dispatchers.IO) {
            val exportManager = ExportManager(application)
            exportManager.exportWorkoutTo(workoutId, fileFormat)
        }
    }

    /**
     * Deletes a workout from the current list and posts the update.
     */
    fun deleteWorkout(id: Long) {
        launch(Dispatchers.IO) {
            // Find the workout name *before* deleting it.
            val workout = _allWorkouts.value?.find { it.id == id }
            val workoutName = workout?.headerData?.workoutName ?: "Workout ID: $id"

            // --- START PROGRESS ---
            // Post the detailed progress to the LiveData.
            _deletionProgress.postValue(DeletionProgress.InProgress(workoutName, id))

            // Perform the actual deletion in the database first
            val success = deletionHelper.deleteWorkout(id)
            if (success) {
                // Now, update the in-memory LiveData list
                val currentList = _allWorkouts.value ?: emptyList()
                val updatedList = currentList.filterNot { it.id == id }
                _allWorkouts.postValue(updatedList)

                // Post event for UI to react (e.g., close screen)
                deleteFinishedEvent.postValue(Pair(id, true))
            } else {
                deleteFinishedEvent.postValue(Pair(id, true))
            }

            // Reset the state to Idle when done or if an error occurs.
            _deletionProgress.postValue(DeletionProgress.Idle)
        }
    }


    suspend fun deleteOldWorkouts(daysToKeep: Int) {
        withContext(Dispatchers.IO) {
            try {
                // The callback lambda that will be executed inside the helper.
                val progressCallback: (Long) -> Unit = { workoutId ->
                    // Find the workout name from the current list to display it.
                    val workout = allWorkouts.value?.find { it.id == workoutId }
                    val workoutName = workout?.headerData?.workoutName ?: "Workout ID: $workoutId"

                    // Post the detailed progress to the LiveData.
                    _deletionProgress.postValue(DeletionProgress.InProgress(workoutName, workoutId))
                }

                val success = deletionHelper.deleteOldWorkouts(daysToKeep, progressCallback)

                // After deleting, reload the data so the UI updates automatically.
                if (success) {
                    loadAllWorkouts()
                }
            } finally {
                // Reset the state to Idle when done or if an error occurs.
                _deletionProgress.postValue(DeletionProgress.Idle)
            }
        }
    }


}