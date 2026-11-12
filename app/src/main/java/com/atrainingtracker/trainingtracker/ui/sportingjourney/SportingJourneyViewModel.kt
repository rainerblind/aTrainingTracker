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

package com.atrainingtracker.trainingtracker.ui.sportingjourney

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel orchestrating data loading and filter mutations for [SportingJourneySheet] (REQ-SPT-002, REQ-SPT-003).
 */
class SportingJourneyViewModel(
    application: Application,
    private val repository: SportingJourneyRepository = SportingJourneyRepository.getInstance(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SportingJourneyData(isLoading = true))
    val uiState: StateFlow<SportingJourneyData> = _uiState.asStateFlow()

    private var currentHorizon: SportingHorizon = SportingHorizon.AllTime
    private var currentSportFilter: SportingSportFilter = SportingSportFilter.ALL
    private var loadJob: Job? = null

    init {
        loadData()
    }

    /**
     * Loads retrospective data for [currentHorizon] and [currentSportFilter].
     * Cancels any in-flight query and immediately keys [uiState] to the target filters.
     */
    fun loadData() {
        loadJob?.cancel()
        _uiState.value = _uiState.value.copy(
            horizon = currentHorizon,
            sportFilter = currentSportFilter,
            isLoading = true
        )
        loadJob = viewModelScope.launch {
            val data = repository.loadJourneyData(currentHorizon, currentSportFilter)
            _uiState.value = data.copy(isLoading = false)
        }
    }

    /**
     * Updates the time horizon (All-Time vs calendar year) and refreshes data.
     */
    fun setHorizon(horizon: SportingHorizon) {
        if (currentHorizon == horizon) return
        currentHorizon = horizon
        loadData()
    }

    /**
     * Updates the sport filter and refreshes data.
     */
    fun setSportFilter(filter: SportingSportFilter) {
        if (currentSportFilter == filter) return
        currentSportFilter = filter
        loadData()
    }
}
