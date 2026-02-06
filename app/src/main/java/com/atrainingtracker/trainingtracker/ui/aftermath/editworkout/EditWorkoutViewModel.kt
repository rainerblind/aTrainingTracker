package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import android.app.Application
import androidx.lifecycle.*
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsData
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaData
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderData
import com.atrainingtracker.trainingtracker.util.Event
import kotlinx.coroutines.launch

class EditWorkoutViewModel(application: Application, private val workoutId: Long) : AndroidViewModel(application) {

    private val repository = WorkoutRepository(application)

    private val workoutSummariesDatabaseManager =
        WorkoutSummariesDatabaseManager.getInstance(application)


    // LiveData to hold the entire WorkoutData object. The UI will observe this.
    val workoutData: LiveData<WorkoutData?>

    // LiveData specifically for the header
    val headerData: LiveData<Pair<Long, WorkoutHeaderData>> = repository.headerData

    // LiveData specifically for the details
    val detailsData: LiveData<Pair<Long, WorkoutDetailsData>> = repository.detailsData

    // LiveData specifically for the list of extrema values ---
    val extremaData: LiveData<Pair<Long, ExtremaData>> = repository.extremaData

    // LiveData specifically for the message from the extrema calculation worker
    val extremaCalculationMessage: LiveData<Event<String>> = repository.extremaCalculationMessage


    val saveFinishedEvent: MutableLiveData<Event<Boolean>> = repository.saveFinishedEvent
    val deleteFinishedEvent: MutableLiveData<Event<Boolean>> = repository.deleteFinishedEvent


    init {
        workoutData = repository.getWorkoutById(workoutId)

        // Tell the repository to load the initial data
        viewModelScope.launch {
            repository.loadWorkout(workoutId)
        }
    }

    fun updateWorkoutName(newName: String) {
        repository.updateWorkoutName(workoutId, newName)
    }

    fun updateSportName(newSportName: String?) {
        repository.updateSportName(workoutId, newSportName)
    }

    fun updateEquipmentName(newEquipmentName: String?) {
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