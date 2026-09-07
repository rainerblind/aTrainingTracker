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
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.repositories.EquipmentRepository
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.FlowPreview::class)
class EditWorkoutViewModel(application: Application, private val workoutId: Long) : AndroidViewModel(application) {

    private val repository = WorkoutRepository.getInstance(application)
    private val equipmentRepository = EquipmentRepository.getInstance(application)
    private val sportTypesRepository = SportTypesRepository.getInstance(application)
    private val sportTypeDatabaseManager = SportTypeDatabaseManager.getInstance(application)
    private val discoveryManager = EquipmentAndSportTypeDiscoveryManager.getInstance(application)

    // Using StateFlow for modern reactive UI
    private val _workoutData = MutableStateFlow<WorkoutData?>(null)
    val workoutData: StateFlow<WorkoutData?> = _workoutData.asStateFlow()

    // Options for Spinners (Sport Types & Equipment)
    private val sportTypesList: List<SimpleSportTypeInfo> = sportTypesRepository.sportTypesList
    private val equipmentList: List<EquipmentData> = equipmentRepository.equipmentList

    private val _sportTypeNames = MutableLiveData<List<String>>()
    val sportTypeNames: LiveData<List<String>> = _sportTypeNames
    
    private val _equipmentNames = MutableLiveData<List<String>>()
    val equipmentNames: LiveData<List<String>> = _equipmentNames

    // Suggested values for dropdown synchronization (SCRUM-200)
    var suggestedSportTypeName by mutableStateOf("")
    var suggestedEquipmentName by mutableStateOf<String?>(null)
    
    var currentBSportType by mutableStateOf(BSportType.UNKNOWN)

    // Constants for special spinner items
    val allSportTypes = application.getString(R.string.all_sports)
    val allShoes = application.getString(R.string.all_shoes)
    val allBikes = application.getString(R.string.all_bikes)
    val noEquipment = application.getString(R.string.no_equipment)

    // Suggested Clusters for the Auto-Name dialog (SCRUM-214)
    private val _clusterSuggestions = MutableStateFlow<List<Pair<WorkoutCluster, Double>>>(emptyList())
    val clusterSuggestions: StateFlow<List<Pair<WorkoutCluster, Double>>> = _clusterSuggestions.asStateFlow()

    init {
        loadWorkoutData()

        // Reactive Cluster Suggestions (ATT-447)
        viewModelScope.launch {
            _workoutData
                .filterNotNull()
                .distinctUntilChanged { old, new ->
                    old.workoutName == new.workoutName && old.bSportType == new.bSportType
                }
                .debounce(500)
                .collect { data ->
                    fetchClusterSuggestions(data)
                }
        }
    }

    private fun loadWorkoutData() {
        viewModelScope.launch {
            repository.loadWorkout(workoutId)
            repository.initialWorkoutLoaded.asFlow().collect { data ->
                // Guard against global events for different workouts
                if (data.id != workoutId) return@collect

                _workoutData.update { current ->
                    // If we already have data and are just updating identity, merge it
                    if (current != null) {
                        current.copy(
                            workoutName = data.workoutName,
                            sportId = data.sportId,
                            sportName = data.sportName,
                            bSportType = data.bSportType,
                            equipmentId = data.equipmentId,
                            equipmentName = data.equipmentName,
                            clusterId = data.clusterId,
                            clusterName = data.clusterName,
                            stravaSportName = data.stravaSportName
                        )
                    } else {
                        data
                    }
                }
                
                suggestedSportTypeName = data.sportName
                currentBSportType = data.bSportType

                val linkedEquipment = getFilteredLinkedEquipment(data.sportName, data.bSportType)
                val finalEquipmentName: String?
                val finalEquipmentId: Long

                if (data.equipmentName != null) {
                    val currentEquip = equipmentList.find { it.name == data.equipmentName }
                    if (currentEquip != null && (data.bSportType == BSportType.UNKNOWN || currentEquip.sportType == data.bSportType)) {
                        finalEquipmentName = data.equipmentName
                        finalEquipmentId = data.equipmentId
                    } else if (linkedEquipment.size == 1) {
                        val linkedName = linkedEquipment.first()
                        finalEquipmentName = linkedName
                        finalEquipmentId = equipmentList.find { it.name == linkedName }?.id ?: -1L
                    } else {
                        finalEquipmentName = null
                        finalEquipmentId = -1L
                    }
                } else {
                    if (linkedEquipment.size == 1) {
                        val linkedName = linkedEquipment.first()
                        finalEquipmentName = linkedName
                        finalEquipmentId = equipmentList.find { it.name == linkedName }?.id ?: -1L
                    } else {
                        finalEquipmentName = null
                        finalEquipmentId = -1L
                    }
                }

                if (finalEquipmentName != data.equipmentName || finalEquipmentId != data.equipmentId) {
                    _workoutData.update { it?.copy(equipmentName = finalEquipmentName, equipmentId = finalEquipmentId) }
                }

                suggestedEquipmentName = finalEquipmentName
                updateSuggestedSportTypeNames(data)
                updateSuggestedEquipmentNames(data.sportName)
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

        val previousBSportType = currentBSportType
        val newBSportType = sportTypeDatabaseManager.getBSportType(newSportId)
        currentBSportType = newBSportType
        suggestedSportTypeName = newSportName

        // Query linked equipment for the new sport
        val linkedEquipment = getFilteredLinkedEquipment(newSportName, newBSportType)

        // Determine preselected equipment based on REQ-UI-130
        val targetEquipmentName: String?
        val targetEquipmentId: Long

        if (linkedEquipment.size == 1) {
            // Case 2 (N == 1): The uniquely linked equipment is preselected
            targetEquipmentName = linkedEquipment.first()
            targetEquipmentId = equipmentList.find { it.name == targetEquipmentName }?.id ?: -1L
        } else if (newBSportType == previousBSportType && previousBSportType != BSportType.UNKNOWN) {
            // Same base sport: If current equipment is valid for this base sport, retain it
            val currentEquipName = workoutData.value?.equipmentName
            val currentEquip = equipmentList.find { it.name == currentEquipName }
            if (currentEquip != null && currentEquip.sportType == newBSportType) {
                targetEquipmentName = currentEquipName
                targetEquipmentId = currentEquip.id
            } else {
                targetEquipmentName = null
                targetEquipmentId = -1L
            }
        } else {
            // Different base sport or UNKNOWN: reset to - none -
            targetEquipmentName = null
            targetEquipmentId = -1L
        }

        val stravaSportName = sportTypeDatabaseManager.getStravaName(newSportId)

        _workoutData.update { current ->
            current?.copy(
                sportName = newSportName,
                bSportType = newBSportType,
                sportId = newSportId,
                stravaSportName = stravaSportName,
                uploadToStrava = if (stravaSportName != null) 1 else 0,
                equipmentName = targetEquipmentName,
                equipmentId = targetEquipmentId
            )
        }

        suggestedEquipmentName = targetEquipmentName

        workoutData.value?.let { current ->
            val updatedForSuggestions = current.copy(sportName = newSportName, bSportType = newBSportType)
            updateSuggestedSportTypeNames(updatedForSuggestions)
        }
        updateSuggestedEquipmentNames(newSportName)
    }

    // --- Smart handler for equipment changes ---

    fun updateEquipmentName(newName: String) {
        when (newName) {
            allShoes -> showAllShoes()
            allBikes -> showAllBikes()
            noEquipment -> {
                _workoutData.update { it?.copy(equipmentName = null, equipmentId = -1L) }
                suggestedEquipmentName = null
            }
            else -> {
                val equipment = equipmentList.find { it.name == newName }
                val newId = equipment?.id ?: -1L
                _workoutData.update { it?.copy(equipmentName = if (newId == -1L) null else newName, equipmentId = newId) }
                suggestedEquipmentName = if (newId == -1L) null else newName
            }
        }
    }

    // --- Smart handlers for suggestions (SCRUM-200) ---

    private fun updateSuggestedSportTypeNames(data: WorkoutData) {
        val suggestedSports = discoveryManager.getSpeedBasedSportTypeNames(data.bSportType, data.avgSpeedMps).toMutableList()
        
        // Ensure current sport is in the list
        if (!suggestedSports.contains(data.sportName)) {
            suggestedSports.add(0, data.sportName)
        }
        
        // Add "All sports" option to allow expanding the list
        suggestedSports.add(allSportTypes)
        
        _sportTypeNames.value = suggestedSports
    }

    private fun getFilteredLinkedEquipment(sportName: String, bSportType: BSportType): List<String> {
        return discoveryManager.getEquipmentNamesForSport(sportName)
            .filter { name ->
                val eq = equipmentList.find { it.name == name }
                eq != null && (bSportType == BSportType.UNKNOWN || eq.sportType == bSportType)
            }
    }

    private fun updateSuggestedEquipmentNames(sportName: String) {
        val linkedEquipment = getFilteredLinkedEquipment(sportName, currentBSportType)
        val options = mutableListOf<String>()
        options.add(noEquipment)

        val sportEquipment = when (currentBSportType) {
            BSportType.BIKE -> equipmentList.filter { it.sportType == BSportType.BIKE }.map { it.name }
            BSportType.RUN -> equipmentList.filter { it.sportType == BSportType.RUN }.map { it.name }
            else -> emptyList()
        }

        if (linkedEquipment.size > 1) {
            // Case 1 (N > 1): [- none -, <linked equipment...>, + all bikes/shoes +]
            options.addAll(linkedEquipment)
            when (currentBSportType) {
                BSportType.BIKE -> options.add(allBikes)
                BSportType.RUN -> options.add(allShoes)
                else -> { /* no expansion token for UNKNOWN / OTHER */ }
            }
        } else {
            // Case 2 (N == 1) & Case 3 (N == 0): [- none -, <all equipment of that sport>]
            options.addAll(sportEquipment)
        }

        _equipmentNames.value = options.distinct()
    }

    private fun showAllSportTypes() {
        _sportTypeNames.value = sportTypesList.map { it.name }
    }

    private fun showAllShoes() {
        val options = mutableListOf<String>()
        options.add(noEquipment)
        options.addAll(equipmentList.filter { it.sportType == BSportType.RUN }.map { it.name })
        _equipmentNames.value = options.distinct()
    }

    private fun showAllBikes() {
        val options = mutableListOf<String>()
        options.add(noEquipment)
        options.addAll(equipmentList.filter { it.sportType == BSportType.BIKE }.map { it.name })
        _equipmentNames.value = options.distinct()
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
                .getClusterScores(start, end, apex, workout.totalDistance, workout.workoutName, workout.bSportType)
            _clusterSuggestions.value = suggestions
        }
    }

    fun applyClusterIdentity(cluster: WorkoutCluster) {
        _workoutData.update { current ->
            current?.copy(
                clusterId = cluster.id,
                clusterName = cluster.name,
                workoutName = application.getString(R.string.cluster_autoname_format, cluster.name, cluster.hitCount + 1),
                sportId = cluster.probableSportId,
                bSportType = cluster.bSportType
            )
        }
        // Sync secondary UI states
        suggestedSportTypeName = sportTypeDatabaseManager.getUIName(cluster.probableSportId)
        currentBSportType = cluster.bSportType

        // ATT-388: Persist the new cluster identity immediately
        repository.assignClusterToWorkout(workoutId, cluster.id)
    }

    fun unassignCluster() {
        _workoutData.update { current ->
            current?.copy(
                clusterId = -1L,
                clusterName = null
            )
        }
        repository.unassignClusterFromWorkout(workoutId)
    }

    fun createNewCluster(customName: String) {
        val current = _workoutData.value
        if (current != null) {
            repository.createNewClusterFromWorkout(current, customName)
        } else {
            repository.createNewClusterFromWorkout(workoutId, customName)
        }
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
