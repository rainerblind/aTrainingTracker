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

package com.atrainingtracker.trainingtracker.activities

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.ZoneType
import com.atrainingtracker.trainingtracker.settings.ZoneDisplayOptions
import com.atrainingtracker.trainingtracker.settings.ZoneProfileState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * ViewModel managing athletic training zone configuration, atomic single-transaction
 * persistence, and draft preservation across configuration changes via SavedStateHandle (REQ-UI-428).
 */
class ZonesSettingsViewModel(
    application: Application,
    val savedStateHandle: SavedStateHandle,
    val dataStore: SettingsDataStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    constructor(application: Application, savedStateHandle: SavedStateHandle) :
        this(application, savedStateHandle, SettingsDataStore(application), Dispatchers.IO)

    constructor(application: Application) :
        this(application, SavedStateHandle(), SettingsDataStore(application), Dispatchers.IO)

    private val _profiles = MutableStateFlow<List<ZoneProfileState>>(emptyList())
    val allProfiles: StateFlow<List<ZoneProfileState>> = _profiles.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val saveJobs = ConcurrentHashMap<ZoneType, Job>()

    init {
        loadProfiles()
    }

    /**
     * Loads all zone profiles from DataStore, merging with any in-flight drafts
     * stored in SavedStateHandle (REQ-UI-428.2, REQ-UI-428.3).
     */
    fun loadProfiles() {
        viewModelScope.launch(ioDispatcher) {
            _isLoading.value = true
            val loaded = dataStore.loadAllZoneProfiles()
            val merged = loaded.map { profile ->
                getDraft(profile.type) ?: profile
            }
            _profiles.value = merged
            _isLoading.value = false
        }
    }

    /**
     * Reloads a profile fresh from DataStore, discarding in-flight drafts (e.g. after calculation dialog).
     */
    fun reloadProfileFromDataStore(zoneType: ZoneType) {
        clearDraft(zoneType)
        viewModelScope.launch(ioDispatcher) {
            val fresh = dataStore.loadZoneProfile(zoneType)
            _profiles.update { list ->
                if (list.any { it.type == zoneType }) {
                    list.map { if (it.type == zoneType) fresh else it }
                } else {
                    list + fresh
                }
            }
        }
    }

    /**
     * Reloads all profiles fresh from DataStore, discarding in-flight drafts.
     */
    fun reloadAllFromDataStore() {
        ZoneType.values().forEach { clearDraft(it) }
        loadProfiles()
    }

    /**
     * Updates an in-flight zone profile draft in memory and SavedStateHandle.
     * If the profile satisfies monotonicity, schedules an atomic DataStore write (REQ-UI-428.1, REQ-UI-428.2).
     */
    fun updateProfile(updated: ZoneProfileState, debounceMs: Long = 300L) {
        saveDraft(updated)
        _profiles.update { list ->
            val idx = list.indexOfFirst { it.type == updated.type }
            if (idx != -1) {
                val copy = list.toMutableList()
                copy[idx] = updated
                copy
            } else {
                list + updated
            }
        }

        if (updated.isMonotonic()) {
            saveJobs[updated.type]?.cancel()
            saveJobs[updated.type] = viewModelScope.launch(ioDispatcher) {
                if (debounceMs > 0) {
                    delay(debounceMs)
                }
                dataStore.saveZoneProfile(updated)
            }
        }
    }

    /**
     * Updates a single zone threshold cutoff for a profile and saves if monotonic.
     */
    fun updateThreshold(zoneType: ZoneType, zoneIndex: Int, value: Int, debounceMs: Long = 300L) {
        val current = _profiles.value.find { it.type == zoneType } ?: return
        val updated = when (zoneIndex) {
            1 -> current.copy(z1 = value)
            2 -> current.copy(z2 = value)
            3 -> current.copy(z3 = value)
            4 -> current.copy(z4 = value)
            5 -> current.copy(z5 = value)
            6 -> current.copy(z6 = value)
            else -> current
        }
        updateProfile(updated, debounceMs)
    }

    /**
     * Updates display options for a zone profile and commits immediately without debounce.
     */
    fun updateDisplayOptions(zoneType: ZoneType, options: ZoneDisplayOptions) {
        val current = _profiles.value.find { it.type == zoneType } ?: return
        val updated = current.copy(displayOptions = options)
        updateProfile(updated, debounceMs = 0L)
    }

    fun getProfile(zoneType: ZoneType): ZoneProfileState? {
        return _profiles.value.find { it.type == zoneType }
    }

    fun getDraft(type: ZoneType): ZoneProfileState? {
        return savedStateHandle.get<ZoneProfileState>(draftKey(type))
    }

    fun hasDraft(type: ZoneType): Boolean {
        return savedStateHandle.contains(draftKey(type))
    }

    fun clearDraft(type: ZoneType) {
        savedStateHandle.remove<ZoneProfileState>(draftKey(type))
    }

    private fun saveDraft(profile: ZoneProfileState) {
        savedStateHandle[draftKey(profile.type)] = profile
    }

    private fun draftKey(type: ZoneType): String = "draft_zone_${type.name}"

    class Factory(
        private val application: Application,
        private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
        private val dataStore: SettingsDataStore = SettingsDataStore(application)
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ZonesSettingsViewModel(application, savedStateHandle, dataStore) as T
        }
    }

    companion object {
        fun provideFactory(
            context: Context,
            savedStateHandle: SavedStateHandle = SavedStateHandle()
        ): ViewModelProvider.Factory {
            val app = context.applicationContext as Application
            return Factory(app, savedStateHandle)
        }
    }
}
