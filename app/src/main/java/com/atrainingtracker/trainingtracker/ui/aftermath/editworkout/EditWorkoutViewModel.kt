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
 * along with this program.  See the GNU General Public License for more details.
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.*
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.SimpleSportTypeInfo
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper.EquipmentData
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.repositories.EquipmentRepository
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditWorkoutViewModel(application: Application, private val workoutId: Long) : AndroidViewModel(application) {

    private val repository = WorkoutRepository.getInstance(application)
    private val equipmentRepository = EquipmentRepository.getInstance(application)
    private val sportTypesRepository = SportTypesRepository.getInstance(application)
    private val sportTypeDatabaseManager = SportTypeDatabaseManager.getInstance(application)
    private val workoutSummariesDatabaseManager = WorkoutSummariesDatabaseManager.getInstance(application)
    private val discoveryManager = EquipmentAndSportTypeDiscoveryManager.getInstance(application)

    // Using StateFlow for modern reactive UI
    private val _workoutData = MutableStateFlow<WorkoutData?>(null)
    val workoutData: StateFlow<WorkoutData?> = _workoutData.asStateFlow()

    // Options for Spinners (Sport Types & Equipment)
    private val sportTypesList: List<SimpleSportTypeInfo> = sportTypesRepository.sportTypesList
    private val equipmentList: List<EquipmentData> = equipmentRepository.equipmentList

    private val _sportTypeNames = MutableLiveData(sportTypesList.map { it.name })
    val sportTypeNames: LiveData<List<String>> = _sportTypeNames
    
    private val _equipmentNames = MutableLiveData<List<String>>()
    val equipmentNames: LiveData<List<String>> = _equipmentNames

    // Suggested values (Sport & Equipment)
    var suggestedSportTypeName by mutableStateOf("")
    var userSelectedSportTypeName by mutableStateOf<String?>(null)
    var suggestedEquipmentName by mutableStateOf<String?>(null)
    
    var currentBSportType by mutableStateOf(BSportType.UNKNOWN)

    // Constants for special spinner items
    val allSportTypes = application.getString(R.string.all_sports)
    val allEquipment = application.getString(R.string.all_equipment)
    val allShoes = application.getString(R.string.all_shoes)
    val allBikes = application.getString(R.string.all_bikes)
    val noEquipment = application.getString(R.string.no_equipment)

    // Suggested Clusters for the Auto-Name dialog (SCRUM-214)
    private val _clusterSuggestions = MutableStateFlow<List<Pair<WorkoutCluster, Double>>>(emptyList())
    val clusterSuggestions: StateFlow<List<Pair<WorkoutCluster, Double>>> = _clusterSuggestions.asStateFlow()

    init {
        loadWorkoutData()
    }

    private fun loadWorkoutData() {
        viewModelScope.launch {
            repository.loadWorkout(workoutId)
            repository.initialWorkoutLoaded.asFlow().collect { data ->
                _workoutData.value = data
                
                suggestedSportTypeName = data.sportName
                currentBSportType = data.bSportType
                suggestedEquipmentName = data.equipmentName
                updateSuggestedEquipmentNames(data.sportName)
                fetchClusterSuggestions(data)
            }
        }
    }

    fun updateWorkoutName(newName: String) {
        _workoutData.update { it?.copy(workoutName = newName) }
    }

    /**
     * Smart handler for sport changes. Automatically updates bSportType and equipment suggestions.
     */
    fun updateSportName(newSportName: String) {
        if (newSportName == allSportTypes) {
            showAllSportTypes()
            return
        }

        if (newSportName == workoutData.value?.sportName) return
        val simpleSportTypeInfo = sportTypesList.find { it.name == newSportName }
        val newSportId = simpleSportTypeInfo?.id ?: -1

        // Automatically infer equipment and Strava upload (SCRUM-200)
        val identity = discoveryManager.inferIdentityFromSport(newSportId)
        val inferredEquipmentName = equipmentList.find { it.id == identity.equipmentId }?.name

        _workoutData.update { current ->
            current?.copy(
                sportName = newSportName,
                bSportType = identity.bSportType,
                sportId = newSportId,
                stravaSportName = identity.stravaSportName,
                uploadToStrava = identity.uploadToStrava,
                equipmentName = inferredEquipmentName,
                equipmentId = identity.equipmentId
            )
        }
        
        // Synchronize suggested equipment name for UI (SCRUM-200)
        suggestedEquipmentName = inferredEquipmentName

        // first, get the new sportId and bSportType
        val newBSportType = sportTypeDatabaseManager.getBSportType(newSportId)

        if (newSportName != suggestedSportTypeName) {
            userSelectedSportTypeName = newSportName
        }

        currentBSportType = newBSportType
        suggestedSportTypeName = newSportName
        updateSuggestedEquipmentNames(newSportName)
    }

    // --- Smart handler for equipment changes ---

    fun updateEquipmentName(newName: String) {
        when (newName) {
            allEquipment -> showAllEquipment()
            allShoes -> showAllShoes()
            allBikes -> showAllBikes()
            else -> {
                val equipment = equipmentList.find { it.name == newName }
                val newId = equipment?.id ?: -1L
                _workoutData.update { it?.copy(equipmentName = if (newId == -1L) null else newName, equipmentId = newId) }
                suggestedEquipmentName = if (newId == -1L) null else newName
            }
        }
    }

    private fun updateSuggestedEquipmentNames(sportName: String) {
        val linkedEquipment = discoveryManager.getEquipmentNamesForSport(sportName).toList()
        if (linkedEquipment.isNotEmpty()) {
            val options = mutableListOf<String>()
            options.addAll(linkedEquipment)
            options.add(noEquipment)
            options.add(allEquipment)
            
            // Add categorical filters based on BSportType
            if (currentBSportType == BSportType.RUN) options.add(allShoes)
            if (currentBSportType == BSportType.BIKE) options.add(allBikes)
            
            _equipmentNames.value = options
        } else {
            showAllEquipment()
        }
    }

    private fun showAllSportTypes() {
        _sportTypeNames.value = sportTypesList.map { it.name }
    }

    private fun showAllEquipment() {
        val options = mutableListOf<String>()
        options.add(noEquipment)
        options.addAll(equipmentList.map { it.name })
        _equipmentNames.value = options
    }

    private fun showAllShoes() {
        val options = mutableListOf<String>()
        options.add(noEquipment)
        options.addAll(equipmentList.filter { it.sportType == BSportType.RUN }.map { it.name })
        options.add(allEquipment)
        _equipmentNames.value = options
    }

    private fun showAllBikes() {
        val options = mutableListOf<String>()
        options.add(noEquipment)
        options.addAll(equipmentList.filter { it.sportType == BSportType.BIKE }.map { it.name })
        options.add(allEquipment)
        _equipmentNames.value = options
    }


    fun updateDescription(newDescription: String) {
        _workoutData.update { it?.copy(description = newDescription) }
    }

    fun updateGoal(newGoal: String) {
        _workoutData.update { it?.copy(goal = newGoal) }
    }

    fun updateMethod(newMethod: String) {
        _workoutData.update { it?.copy(method = newMethod) }
    }

    fun updateIsCommute(isChecked: Boolean) {
        val isTrainer = if (isChecked) { false } else { workoutData.value?.trainer ?: false }

        _workoutData.update { it?.copy(commute = isChecked, trainer = isTrainer) }
    }

    fun updateIsTrainer(isChecked: Boolean) {
        val isCommute = if (isChecked) { false } else { workoutData.value?.commute ?: false }

        _workoutData.update { it?.copy(trainer = isChecked, commute = isCommute) }
    }

    fun updateUploadToStrava(isChecked: Boolean) {
        _workoutData.update { it?.copy(uploadToStrava = if (isChecked) 1 else 0) }
    }


    /**
     * Saves the current state of the WorkoutData object to the database.
     */
    fun saveChanges() {
        repository.saveWorkout(workoutData.value)
    }

    private fun fetchClusterSuggestions(workout: WorkoutData) {
        val start = workout.startLatLng ?: return
        val end = workout.endLatLng ?: return
        val apex = workout.maxDisplacementLatLng ?: return
        
        viewModelScope.launch {
            val suggestions = WorkoutClusterEngine.getInstance(getApplication())
                .getClusterScores(start, end, apex, workout.totalDistance, workout.workoutName)
            _clusterSuggestions.value = suggestions
        }
    }

    fun applyClusterIdentity(cluster: WorkoutCluster) {
        repository.assignClusterToWorkout(workoutId, cluster.id)
    }

    fun getSportName(sportId: Long): String {
        return sportTypeDatabaseManager.getUIName(sportId)
    }

    fun getBSportType(sportId: Long): BSportType {
        return sportTypeDatabaseManager.getBSportType(sportId)
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
