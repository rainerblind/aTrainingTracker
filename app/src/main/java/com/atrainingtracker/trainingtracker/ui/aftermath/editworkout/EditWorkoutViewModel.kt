package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import android.app.Application
import androidx.lifecycle.*
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDiffCallback
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutUpdatePayload
import com.atrainingtracker.trainingtracker.util.Event
import kotlinx.coroutines.launch

class EditWorkoutViewModel(application: Application, private val workoutId: Long) : AndroidViewModel(application) {

    private val repository = WorkoutRepository(application)

    private val workoutSummariesDatabaseManager by lazy {
        WorkoutSummariesDatabaseManager.getInstance(application) }
    private val sportTypeDatabaseManager by lazy { SportTypeDatabaseManager.getInstance(application) }


    // LiveData to hold the entire WorkoutData object. The UI will observe this.
    val workoutData: LiveData<WorkoutData?>

    val initialWorkoutLoaded: LiveData<WorkoutData> = repository.initialWorkoutLoaded

    // The current, stable state of the workout as known by the UI.
    private var currentWorkoutState: WorkoutData? = null

    // --- Two-tier cache system for remembering equipment choices ---
    private val sportNameEquipmentCache = mutableMapOf<String, String?>()
    private val bSportTypeEquipmentCache = mutableMapOf<BSportType, String?>()


    // LiveData to emit specific update payloads ---
    private val _updatePayloads = MutableLiveData<Event<List<WorkoutUpdatePayload>>>()
    val updatePayloads: LiveData<Event<List<WorkoutUpdatePayload>>> = _updatePayloads

    // Diffing utility
    private val diffCallback = WorkoutDiffCallback()

    val saveFinishedEvent: MutableLiveData<Pair<Long, Boolean>> = repository.saveFinishedEvent
    val deleteFinishedEvent: MutableLiveData<Pair<Long, Boolean>> = repository.deleteFinishedEvent


    init {
        workoutData = repository.getWorkoutById(workoutId)

        // Tell the repository to load the initial data
        viewModelScope.launch {
            repository.loadWorkout(workoutId)
        }

        // --- Prime the caches when the initial workout is loaded ---
        initialWorkoutLoaded.observeForever { initialWorkout ->
            // Only prime if the caches are empty to avoid overwriting user changes in the session
            if (sportNameEquipmentCache.isEmpty() && bSportTypeEquipmentCache.isEmpty()) {
                val initialSportName = initialWorkout.sportData.sportName
                val initialBSportType = initialWorkout.sportData.bSportType
                val initialEquipmentName = initialWorkout.equipmentData.equipmentName

                sportNameEquipmentCache[initialSportName] = initialEquipmentName
                bSportTypeEquipmentCache[initialBSportType] = initialEquipmentName
            }
        }

        // Observe the single source of truth from the repository.
        repository.allWorkouts.observeForever { list ->
            val newWorkoutState = list.find { it.id == workoutId }

            // If we have both old and new state, perform a diff.
            if (currentWorkoutState != null && newWorkoutState != null) {
                // Check if contents have actually changed.
                if (!diffCallback.areContentsTheSame(currentWorkoutState!!, newWorkoutState)) {

                    // Manually get the change payloads.
                    val payloads = diffCallback.getChangePayload(currentWorkoutState!!, newWorkoutState)

                    if (payloads is List<*>) {
                        @Suppress("UNCHECKED_CAST")
                        _updatePayloads.postValue(Event(payloads as List<WorkoutUpdatePayload>))
                    }
                }
            }

            // Always update the current state to the latest version.
            currentWorkoutState = newWorkoutState
        }
    }

    fun updateWorkoutName(newName: String) {
        repository.updateWorkoutName(workoutId, newName)
    }


    // --- Smart handler for sport type changes ---
    fun updateSportName(newSportName: String?) {
        val workout = currentWorkoutState ?: return
        if (newSportName == null || newSportName == workout.sportData.sportName) return

        // first, get the new sportId and bSportType
        val newSportId = sportTypeDatabaseManager.getSportTypeIdFromUIName(newSportName)
        val newBSportType = sportTypeDatabaseManager.getBSportType(newSportId)

        // then, get the equipment from the cache
        val cachedEquipment = sportNameEquipmentCache[newSportName] // 1. Check specific sport name
            ?: bSportTypeEquipmentCache[newBSportType]              // 2. Fallback to BSportType

        // finally, call a repository method that updates the sport and equipment data
        repository.updateSportAndEquipment(workoutId, newSportName, newSportId, newBSportType, cachedEquipment)
    }

    // --- Smart handler for equipment changes ---
    fun updateEquipmentName(newEquipmentName: String?) {
        val workout = currentWorkoutState ?: return
        if (newEquipmentName == workout.equipmentData.equipmentName) return

        // first, cache the equipment name with the new user choice
        val currentSportName = workout.sportData.sportName
        val currentBSportType = workout.sportData.bSportType
        sportNameEquipmentCache[currentSportName] = newEquipmentName
        bSportTypeEquipmentCache[currentBSportType] = newEquipmentName

        // then, call the repository method that updates the equipment data
        repository.updateEquipmentName(workoutId, newEquipmentName)
    }


    fun updateDescription(newDescription: String) {
        repository.updateDescription(workoutId, newDescription)
    }

    fun updateGoal(newGoal: String) {
        repository.updateGoal(workoutId, newGoal)
    }

    fun updateMethod(newMethod: String) {
        repository.updateMethod(workoutId, newMethod)
    }

    fun updateIsCommute(isChecked: Boolean) {
        repository.updateIsCommute(workoutId, isChecked)
    }

    fun updateIsTrainer(isChecked: Boolean) {
        repository.updateIsTrainer(workoutId, isChecked)
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
        repository.saveWorkout(workoutId)
    }

    fun deleteWorkout() {
        repository.deleteWorkout(workoutId)
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