/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDiffCallback
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutUpdatePayload
import com.atrainingtracker.trainingtracker.ui.util.Event
import kotlinx.coroutines.launch

class EditWorkoutViewModel(application: Application, private val workoutId: Long) : AndroidViewModel(application) {
    companion object {
        private val TAG = "EditWorkoutViewModel"
        private var DEBUG = TrainingApplication.getDebug(true)
    }
    private val repository = WorkoutRepository.getInstance(application)

    private val workoutSummariesDatabaseManager by lazy {
        WorkoutSummariesDatabaseManager.getInstance(application) }
    private val sportTypeDatabaseManager by lazy { SportTypeDatabaseManager.getInstance(application) }

    private val discoveryManager by lazy { EquipmentAndSportTypeDiscoveryManager.getInstance(application) }
    private val equipmentManager by lazy { EquipmentDbHelper(application) }


    // LiveData to hold the entire WorkoutData object. The UI will observe this.
    // val workoutData: LiveData<WorkoutData?>

    val initialWorkoutLoaded: LiveData<WorkoutData> = repository.initialWorkoutLoaded

    // The current, stable state of the workout as known by the UI.
    public var currentWorkoutState: WorkoutData? = null

    private lateinit var currentBSportType: BSportType

    // LiveData for the SportType spinner
    private val _sportTypeNames = MutableLiveData<List<String>>()
    val sportTypeNames: LiveData<List<String>> = _sportTypeNames
    lateinit var suggestedSportTypeName: String
    var userSelectedSportTypeName: String? = null
    private var showAllSportTypes = false
    private var showAbsolutelyAllSportTypes = false


    // LifeData for the Equipment spinner
    private val _equipmentNames = MutableLiveData<List<String>>()
    val equipmentNames: LiveData<List<String>> = _equipmentNames
    var suggestedEquipmentName: String? = null
    private var showAllEquipment = false

    // Event to signal the View to open a specific spinner
    private val _openSpinnerEvent = MutableLiveData<Event<SpinnerType>>()
    val openSpinnerEvent: LiveData<Event<SpinnerType>> = _openSpinnerEvent

    enum class SpinnerType { SPORT, EQUIPMENT }

    // constants for the equipment spinner
    val NO_EQUIPMENT = application.getString(R.string.equipment_none)
    val ALL_EQUIPMENT = application.getString(R.string.equipment_all)
    val ALL_SHOES = application.getString(R.string.equipment_all_shoes)
    val ALL_BIKES = application.getString(R.string.equipment_all_bikes)
    val ALL_SPORT_TYPES = application.getString(R.string.show_all_sport_types)




    // LiveData to emit specific update payloads ---
    private val _updatePayloads = MutableLiveData<Event<List<WorkoutUpdatePayload>>>()
    val updatePayloads: LiveData<Event<List<WorkoutUpdatePayload>>> = _updatePayloads

    // Diffing utility
    private val diffCallback = WorkoutDiffCallback()

    val saveFinishedEvent: MutableLiveData<Pair<Long, Boolean>> = repository.saveFinishedEvent


    init {
        // workoutData = repository.getWorkoutById(workoutId)

        // Tell the repository to load the initial data
        viewModelScope.launch {
            repository.loadWorkout(workoutId)
        }

        initialWorkoutLoaded.observeForever { initialWorkout ->
            initSuggestedSportAndEquipmentNames(initialWorkout)
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

    fun initSuggestedSportAndEquipmentNames(initialWorkout: WorkoutData) {

        currentBSportType = initialWorkout.sportData.bSportType
        suggestedSportTypeName = initialWorkout.sportData.sportName
        suggestedEquipmentName = initialWorkout.equipmentData.equipmentName

        // get the linked sport types
        var suggestedSportNames = discoveryManager.getLinkedSportTypeNames(workoutId)
        if (suggestedSportNames.isEmpty()) {
            // when the linked sport types are empty, use the speed-based guess
            suggestedSportNames = discoveryManager.getSpeedBasedSportTypeNames(
                currentBSportType,
                initialWorkout.sportData.avgSpeedMps
            )
        }

        // The stored sport type is not in the list (this happens when the user has changed the sport type) -> show all sport types.
        if (!suggestedSportNames.contains(suggestedSportTypeName)) {
            showAllSportTypes = true
            suggestedSportNames = emptySet()
        }

        // use the helper to finalize the sport names
        finalizeSportNames(suggestedSportNames)


        // get the set of linked equipment
        var suggestedEquipmentNames = discoveryManager.getLinkedEquipmentNames(workoutId)
        if (suggestedEquipmentNames.isEmpty()) {
            // when the linked equipment is empty, try to get the equipment from the sport types
            suggestedEquipmentNames = discoveryManager.getEquipmentNamesForSports(suggestedSportNames)
        }

        if (!suggestedEquipmentNames.contains(suggestedEquipmentName)) {
            showAllEquipment = true
            suggestedEquipmentNames = emptySet()
        }

        // use the helper to finalize the equipment names
        finalizeEquipmentNames(suggestedEquipmentNames)
    }

    fun finalizeSportNames(sportNames: Set<String>) {
        if (DEBUG) Log.i(TAG, "finalizeSportNames, {sportNames: $sportNames}")

        var suggestedSportNames = sportNames
        val allSportTypes = if (showAbsolutelyAllSportTypes) {
            sportTypeDatabaseManager.getSportTypesUiNameList().toSet()
        }
        else {
            sportTypeDatabaseManager.getSportTypesUiNameList(currentBSportType).toSet()
        }

        // when the list is empty, we should show all sport types and remember this choice
        if (suggestedSportNames.isEmpty()) {
            showAllSportTypes = true
        }
        // when we found exactly one sport, we show all sports but preselect this one.
        if (suggestedSportNames.size == 1) {
            if (userSelectedSportTypeName == null) {  // but not when the user already selected one.
                suggestedSportTypeName = suggestedSportNames.first()
            }
            showAllSportTypes = true
        }

        // when requested by the user or we found out that we should show all sport types, we show all
        if (showAllSportTypes) {
            suggestedSportNames = allSportTypes
        }

        val suggestedSportNamesList = suggestedSportNames.toMutableList()

        if (!showAbsolutelyAllSportTypes) {
            suggestedSportNamesList.add(ALL_SPORT_TYPES)
        }

        _sportTypeNames.value = suggestedSportNamesList
    }


    /**
     * function to finalize the raw list of equipment names.
     * * When there is no equipment at all, we return an empty list.
     * * When the raw list is empty, we show all equipment.
     * * When the list contains only one equipment, this will be selected as the suggested equipment and we show all equipment.
     * * When the equipment list is not the full equipment list, we add the option to show all equipment.
     * * Finally, the option to select no equipment is added.
     */
    fun finalizeEquipmentNames(equipmentNames: Set<String>) {
        if (DEBUG) Log.i(TAG, "finalizeEquipmentNames(equipmentNames: $equipmentNames)" +
                "\n bSportType: $currentBSportType" +
                "\n suggestedEquipmentName: $suggestedEquipmentName")

        var suggestedEquipmentNames = equipmentNames

        val allEquipment = equipmentManager.getEquipment(currentBSportType).toSet()

        // when there is no equipment, we return an empty list
        if (allEquipment.isEmpty()) {
            _equipmentNames.value = emptyList()
            return
        }


        // when requested by the user or the suggested equipment is empty, we show all equipment instead
        if (showAllEquipment || suggestedEquipmentNames.isEmpty() ) {
            suggestedEquipmentNames = allEquipment
        }

        // when the suggested equipment contains only one item, we set this equipment
        if (suggestedEquipmentNames.size == 1) {
            if (DEBUG) Log.i(TAG, "FTW: finalizeEquipmentNames, {setting equipment to: ${suggestedEquipmentNames.first()}}")
            suggestedEquipmentName = suggestedEquipmentNames.first()
            suggestedEquipmentNames = allEquipment
        }

        val suggestedEquipmentNamesList = suggestedEquipmentNames.toMutableList()

        // we should add the 'show all' option if and only if the suggestedEquipmentNames do not contain all possible equipment
        if (suggestedEquipmentNames != allEquipment) {
            val allEquipmentName = when (currentBSportType) {
                BSportType.BIKE -> ALL_BIKES
                BSportType.RUN -> ALL_SHOES
                else -> ALL_EQUIPMENT
            }
            suggestedEquipmentNamesList.add(allEquipmentName)
        }

        // add the option to select no equipment
        suggestedEquipmentNamesList.add(0, NO_EQUIPMENT)

        // when the list of equipment does not contain the currently selected equipment, the suggested equipment will be NO_EQUIPMENT
        if (!suggestedEquipmentNames.contains(suggestedEquipmentName)) {
            suggestedEquipmentName = NO_EQUIPMENT
        }

        _equipmentNames.value = suggestedEquipmentNamesList
    }

    fun updateSuggestedSportNames(newEquipmentName: String?) {
        if (DEBUG) Log.i(TAG, "updateSuggestedSportNames, {newEquipmentName: $newEquipmentName}")
        if (newEquipmentName == null) {
            return
        }

        finalizeSportNames(
            discoveryManager.getSportNamesForEquipment(newEquipmentName),
            )
    }

    fun updateSuggestedEquipmentNames(newSportName: String) {
        if (DEBUG) Log.i(TAG, "updateSuggestedEquipmentNames, {newSportName: $newSportName}")
        finalizeEquipmentNames(
            discoveryManager.getEquipmentNamesForSport(newSportName),
        )
    }

    fun showAllSportTypes() {
        if (showAllSportTypes) {  // when we already show all BSportType specific sports and the user selects this option again, we show really all.
            showAbsolutelyAllSportTypes = true
        }
        else {
            showAllSportTypes = true  // remember this choice
        }

        finalizeSportNames(
            sportTypeDatabaseManager.getSportTypesUiNameList(currentBSportType).toSet(),
        )

        // trigger the event to open the sport type spinner
        _openSpinnerEvent.value = Event(SpinnerType.SPORT)
    }

    fun showAllEquipment() {
        showAllEquipment = true  // remember this choice
        finalizeEquipmentNames(
            equipmentManager.getEquipment(currentBSportType).toSet(),
        )

        // trigger the event to open the equipment spinner
        _openSpinnerEvent.value = Event(SpinnerType.EQUIPMENT)
    }

    fun updateWorkoutName(newName: String) {
        repository.updateWorkoutName(workoutId, newName)
    }


    // --- Smart handler for sport type changes ---
    fun updateSportName(newSportName: String) {
        if (DEBUG) Log.i(TAG, "updateSportName: Start with {newSportName: $newSportName}")
        val workout = currentWorkoutState ?: return

        if (newSportName == ALL_SPORT_TYPES) {
            showAllSportTypes()
            return
        }

        if (newSportName == workout.sportData.sportName) return


        // first, get the new sportId and bSportType
        val newSportId = sportTypeDatabaseManager.getSportTypeIdFromUIName(newSportName)
        val newBSportType = sportTypeDatabaseManager.getBSportType(newSportId)

        // finally, call a repository method that updates the sport and equipment data
        repository.updateSport(workoutId, newSportName, newSportId, newBSportType)

        if (newSportName != suggestedSportTypeName) {
            userSelectedSportTypeName = newSportName
        }

        currentBSportType = newBSportType
        suggestedSportTypeName = newSportName
        updateSuggestedEquipmentNames(newSportName)
    }

    // --- Smart handler for equipment changes ---
    fun updateEquipmentName(selectedEquipmentName: String) {
        if (DEBUG) Log.i(TAG, "updateEquipmentName, {selectedEquipmentName: $selectedEquipmentName}")
        val workout = currentWorkoutState ?: return

        // first, the special cases
        // NO_EQUIPMENT means equipment name = null
        val newEquipmentName = if (selectedEquipmentName == NO_EQUIPMENT) null else selectedEquipmentName

        if (newEquipmentName == ALL_EQUIPMENT || newEquipmentName == ALL_SHOES || newEquipmentName == ALL_BIKES) {
            showAllEquipment()
            return
        }

        if (newEquipmentName == workout.equipmentData.equipmentName) return

        // call the repository method that updates the equipment data
        repository.updateEquipmentName(workoutId, newEquipmentName)

        suggestedEquipmentName = newEquipmentName
        updateSuggestedSportNames(newEquipmentName)
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