/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Pure JVM in-memory [DataStore] test double for unit tests that construct
 * [SettingsDataStore], [TuningPreferencesDataStore], or [com.atrainingtracker.trainingtracker.MyPreferenceManager]
 * without touching disk or Android `filesDir`.
 */
internal class InMemoryPreferencesDataStore(
    initialPreferences: Preferences = emptyPreferences()
) : DataStore<Preferences> {
    private val stateFlow = MutableStateFlow(initialPreferences)
    override val data: Flow<Preferences> = stateFlow
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(stateFlow.value)
        stateFlow.value = updated
        return updated
    }
}
