package com.atrainingtracker.trainingtracker.ui.aftermath

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.atrainingtracker.banalservice.BSportType
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
import com.atrainingtracker.trainingtracker.tracker.TrackerService
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.DescriptionDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderDataProvider
import com.atrainingtracker.trainingtracker.util.SingleLiveEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


/**
 * A repository that acts as a single source of truth for workout data.
 * It abstracts the data source (database) from the ViewModels.
 */
class WorkoutRepository private constructor(private val application: Application) : CoroutineScope {

    companion object {
        private val TAG = WorkoutRepository::class.java.simpleName
        private val DEBUG = TrainingApplication.getDebug(true)

        // The single, volatile instance of the repository.
        // @Volatile guarantees that writes to this field are immediately visible to other threads.
        @Volatile
        private var INSTANCE: WorkoutRepository? = null

        /**
         * Gets the singleton instance of the WorkoutRepository.
         *
         * @param application The application context, needed to create the instance for the first time.
         * @return The single instance of WorkoutRepository.
         */
        fun getInstance(application: Application): WorkoutRepository {
            // Double-check locking ensures thread safety and performance.
            return INSTANCE ?: synchronized(this) {
                val instance = INSTANCE
                if (instance != null) {
                    instance
                } else {
                    val newInstance = WorkoutRepository(application)
                    INSTANCE = newInstance
                    newInstance
                }
            }
        }
    }

    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + job

    // Helper instances, initialized lazily
    private val deletionHelper by lazy { WorkoutDeletionHelper(application) }
    private val summariesManager by lazy { WorkoutSummariesDatabaseManager.getInstance(application) }
    private val equipmentDbHelper by lazy { EquipmentDbHelper(application) }
    private val sportTypeDatabaseManager by lazy { SportTypeDatabaseManager.getInstance(application) }
    private val exportManager by lazy { ExportManager(application) }

    private val mapper by lazy {
        // Create instances of the required providers
        val sportDataProvider = SportDataProvider(sportTypeDatabaseManager)
        val equipmentDataProvider = EquipmentDataProvider(equipmentDbHelper, sportTypeDatabaseManager)
        val headerProvider = WorkoutHeaderDataProvider(application, equipmentDbHelper, sportTypeDatabaseManager)
        val detailsProvider = WorkoutDetailsDataProvider(application)
        val extremaProvider = ExtremaDataProvider(application)
        val descriptionProvider = DescriptionDataProvider()

        // Inject them into the mapper
        WorkoutDataMapper(sportDataProvider, equipmentDataProvider, headerProvider, detailsProvider, descriptionProvider, extremaProvider)
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
    private val _initialWorkoutLoaded = SingleLiveEvent<WorkoutData>()
    val initialWorkoutLoaded: LiveData<WorkoutData> = _initialWorkoutLoaded

    //  LiveData for granular deletion progress ---
    private val _deletionProgress = MutableLiveData<DeletionProgress>(DeletionProgress.Idle)
    val deletionProgress: LiveData<DeletionProgress> = _deletionProgress



    val saveFinishedEvent = MutableLiveData<Pair<Long, Boolean>>()
    val deleteFinishedEvent = MutableLiveData<Pair<Long, Boolean>>()


    private val workoutUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == TrackerService.WORKOUT_UPDATED_INTENT) {
                // Get the specific workoutId from the broadcast. Default to -1 if not found.
                val workoutId = intent.getLongExtra(TrackerService.WORKOUT_ID, -1L)

                if (workoutId != -1L) {
                    // We have a specific ID, so reload only that workout.
                    if (DEBUG) Log.d(TAG, "Workout update broadcast received for specific workoutId=$workoutId. Reloading it.")
                    reloadWorkoutData(workoutId)
                } // TODO: we might want to reload all workouts here.
            }
        }
    }


    /*
    Observer stuff for the extrema calculation
     */

    // Keep track of the observers we create so we can remove them later if needed.
    private val activeObservers = mutableMapOf<Long, Observer<List<WorkInfo>>>()

    private fun observeExtremaCalculation(workoutId: Long) {
        // Create a new, dedicated observer for this specific workoutId.
        val newObserver = object : Observer<List<WorkInfo>> {
            private var lastProgressSequence = -1
            override fun onChanged(workInfos: List<WorkInfo>) {
                // This observer now uses its own 'workoutId', which is captured
                // from the function's scope and will never change.
                Log.d(TAG, "Observer called for its specific workoutId=$workoutId")

                val workInfo = workInfos.firstOrNull() ?: return

                if (workInfo.state.isFinished) {
                    Log.d(TAG, "Finished calculation for workout $workoutId")
                    // --- When finished, clear the calculation message, reload the data, and remove the observer.

                    // Find the current workout in the list.
                    val workout = _allWorkouts.value?.find { it.id == workoutId }
                    // If it has a calculation message, clear it.
                    if (workout != null && workout.extremaData.calculationMessage != null) {
                        val updatedExtrema = workout.extremaData.copy(calculationMessage = null)
                        updateWorkoutInList(workoutId, workout.copy(extremaData = updatedExtrema))
                    }
                    // TODO: Currently, we update the workout list twice.  This should be avoided.

                    reloadWorkoutData(workoutId)

                    // Once finished, we can clean up this specific observer.
                    WorkManager.getInstance(application).getWorkInfosByTagLiveData("extrema_calc_$workoutId").removeObserver(this)
                    activeObservers.remove(workoutId)
                } else {
                    val currentProgress =
                        workInfo.progress.getInt(CalcExtremaWorker.KEY_PROGRESS_SEQUENCE, -1)
                    if (currentProgress > lastProgressSequence) {
                        lastProgressSequence = currentProgress
                        val message = workInfo.progress.getString(CalcExtremaWorker.KEY_STARTING_MESSAGE)
                        if (message != null) {
                            // update the message in the list
                            val workoutToUpdate = _allWorkouts.value?.find { it.id == workoutId }
                            if (workoutToUpdate != null) {
                                // Create a new ExtremaData with the updated message.
                                val updatedExtrema = workoutToUpdate.extremaData.copy(calculationMessage = message)
                                // Create a new WorkoutData with the new ExtremaData.
                                val updatedWorkout = workoutToUpdate.copy(extremaData = updatedExtrema)
                                // Post the update. DiffUtil will see that extremaData has changed.
                                updateWorkoutInList(workoutId, updatedWorkout)
                            }

                        } else {
                            val updateType = workInfo.progress.getString(CalcExtremaWorker.KEY_FINISHED_MESSAGE)
                            if (updateType != null) {
                                reloadWorkoutData(workoutId)
                            }
                        }
                    }
                }
            }
        }

        // Attach the new, dedicated observer.
        launch(Dispatchers.Main) {
            val workTag = "extrema_calc_${workoutId}"
            val workManager = WorkManager.getInstance(application)

            // Remove any old observer for this ID before adding a new one.
            activeObservers[workoutId]?.let { oldObserver ->
                workManager.getWorkInfosByTagLiveData(workTag).removeObserver(oldObserver)
            }

            // Store and observe with the new one.
            activeObservers[workoutId] = newObserver
            workManager.getWorkInfosByTagLiveData(workTag).observeForever(newObserver)
        }
    }


    init {
        val filter = IntentFilter(TrackerService.WORKOUT_UPDATED_INTENT)
        LocalBroadcastManager.getInstance(application).registerReceiver(workoutUpdateReceiver, filter)
        if (DEBUG) Log.d(TAG, "WorkoutRepository initialized and workout update receiver registered.")
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
                    _initialWorkoutLoaded.postValue(workout)

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

    // Function to update the workout data from the database but keep the calculationMessage of the extrema data and the workout name if it has changed
    private fun reloadWorkoutData(workoutId: Long) {
        launch(Dispatchers.IO) {
            summariesManager.getWorkoutCursor(workoutId).use { cursor ->
                if (cursor?.moveToFirst() == true) {
                    // Get the completely fresh data from the database.
                    val freshWorkoutData = mapper.fromCursor(cursor)

                    // Get the current in-memory version of the workout to check its state.
                    val currentWorkoutInMemory = allWorkouts.value?.find { it.id == workoutId }
                    val currentMessage = currentWorkoutInMemory?.extremaData?.calculationMessage

                    // Calculate the new workout name
                    val currentWorkoutName = currentWorkoutInMemory?.headerData?.workoutName
                    val currentFileBaseName = currentWorkoutInMemory?.fileBaseName
                    val finalWorkoutName = if (currentWorkoutName == currentFileBaseName) {
                        // If the name has NOT been edited by the user, use the fresh name from the DB
                        // (which may have been promoted to the fancy/auto name by the CalcExtremaWorker.)
                        freshWorkoutData.headerData.workoutName
                    } else {
                        // If the user HAS edited the name, stick with the name currently in memory.
                        currentWorkoutName
                    }
                    // Create the final workout object to be posted.
                    val finalWorkoutData = freshWorkoutData.copy(
                        // Always preserve the calculation message if it exists
                        extremaData = freshWorkoutData.extremaData.copy(calculationMessage = currentMessage),

                        // And always use the final, intelligently decided name.
                        // Provide a fallback to the original fresh name just in case.
                        headerData = freshWorkoutData.headerData.copy(workoutName = finalWorkoutName ?: freshWorkoutData.headerData.workoutName)
                    )

                    // Update the workout list
                    updateWorkoutInList(workoutId, finalWorkoutData)
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

    fun updateSportAndEquipment(workoutId: Long, newSportName: String, newSportId: Long, newBSportType: BSportType, newEquipmentName: String?) {
        val currentList = _allWorkouts.value ?: return
        val workoutToUpdate = currentList.find { it.id == workoutId } ?: return
        if (newSportName == workoutToUpdate.sportData.sportName) return

        // update the workout.  Thereby, we have to update the sport, equipment, header, and details...
        val updatedWorkout = workoutToUpdate.copy(
            sportData = workoutToUpdate.sportData.copy(
                sportId = newSportId,
                bSportType = newBSportType,
                sportName = newSportName
            ),
            equipmentData = workoutToUpdate.equipmentData.copy(
                bSportType = newBSportType,
                equipmentName = newEquipmentName
            ),
            headerData = workoutToUpdate.headerData.copy(
                bSportType = newBSportType,
                sportName = newSportName,
                equipmentName = newEquipmentName
            ),
            detailsData = workoutToUpdate.detailsData.copy(
                bSportType = newBSportType)
        )
        updateWorkoutInList(workoutId, updatedWorkout)
    }


    fun updateEquipmentName(workoutId: Long, newEquipmentName: String?) {
        if (newEquipmentName == null) return
        val currentList = _allWorkouts.value ?: return
        val workoutToUpdate = currentList.find { it.id == workoutId } ?: return
        if (newEquipmentName == workoutToUpdate.equipmentData.equipmentName) return

        // update the workout.  Thereby, we have to update the sportAndEquipment and header ...
        val updatedWorkout = workoutToUpdate.copy(
            equipmentData = workoutToUpdate.equipmentData.copy(
                equipmentName = newEquipmentName
            ),
            headerData = workoutToUpdate.headerData.copy(
                equipmentName = newEquipmentName)
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
            val equipmentId = equipmentDbHelper.getEquipmentId(dataToSave.equipmentData.equipmentName ?: "")
            summariesManager.updateSportAndEquipment(
                workoutId,
                dataToSave.sportData.sportId,
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
            exportManager.exportWorkout(dataToSave.fileBaseName)

            saveFinishedEvent.postValue(Pair(workoutId, true))
        }
    }

    suspend fun exportWorkoutTo(workoutId: Long, fileFormat: FileFormat) {
        withContext(Dispatchers.IO) {
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