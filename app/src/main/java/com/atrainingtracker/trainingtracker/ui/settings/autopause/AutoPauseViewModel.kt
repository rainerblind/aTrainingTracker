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

package com.atrainingtracker.trainingtracker.ui.settings.autopause

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel managing sport-specific auto-pause preferences (REQ-SET-080, TST-SET-080, ATT-4836).
 */
class AutoPauseViewModel @JvmOverloads constructor(
    application: Application,
    private val dataStore: TuningPreferencesDataStore = TuningPreferencesDataStore(application)
) : AndroidViewModel(application) {

    val tuningConfig: StateFlow<TuningConfig> = dataStore.tuningConfigStateFlow

    fun updateStartInAutoPauseEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.updateStartInAutoPauseEnabled(enabled)
        }
    }

    fun updateAutoPauseBikeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.updateAutoPauseBikeEnabled(enabled)
        }
    }

    fun updateAutoPauseBikeSpeedThreshold(threshold: Int) {
        viewModelScope.launch {
            dataStore.updateAutoPauseBikeSpeedThreshold(threshold)
        }
    }

    fun updateAutoPauseBikeDebounceSec(debounceSec: Int) {
        viewModelScope.launch {
            dataStore.updateAutoPauseBikeDebounceSec(debounceSec)
        }
    }

    fun updateAutoPauseBikeResumeDebounceSec(debounceSec: Int) {
        viewModelScope.launch {
            dataStore.updateAutoPauseBikeResumeDebounceSec(debounceSec)
        }
    }

    fun updateAutoPauseRunEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.updateAutoPauseRunEnabled(enabled)
        }
    }

    fun updateAutoPauseRunSpeedThreshold(threshold: Int) {
        viewModelScope.launch {
            dataStore.updateAutoPauseRunSpeedThreshold(threshold)
        }
    }

    fun updateAutoPauseRunDebounceSec(debounceSec: Int) {
        viewModelScope.launch {
            dataStore.updateAutoPauseRunDebounceSec(debounceSec)
        }
    }

    fun updateAutoPauseRunResumeDebounceSec(debounceSec: Int) {
        viewModelScope.launch {
            dataStore.updateAutoPauseRunResumeDebounceSec(debounceSec)
        }
    }

    fun updateAutoPauseOtherEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.updateAutoPauseOtherEnabled(enabled)
        }
    }

    fun updateAutoPauseOtherSpeedThreshold(threshold: Int) {
        viewModelScope.launch {
            dataStore.updateAutoPauseOtherSpeedThreshold(threshold)
        }
    }

    fun updateAutoPauseOtherDebounceSec(debounceSec: Int) {
        viewModelScope.launch {
            dataStore.updateAutoPauseOtherDebounceSec(debounceSec)
        }
    }

    fun updateAutoPauseOtherResumeDebounceSec(debounceSec: Int) {
        viewModelScope.launch {
            dataStore.updateAutoPauseOtherResumeDebounceSec(debounceSec)
        }
    }

    fun resetToDefaults(sportType: BSportType? = null) {
        viewModelScope.launch {
            dataStore.resetAutoPausePreferences(sportType)
        }
    }
}
